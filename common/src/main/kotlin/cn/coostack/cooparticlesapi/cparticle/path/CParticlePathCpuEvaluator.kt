package cn.coostack.cooparticlesapi.cparticle.path

import cn.coostack.cooparticlesapi.particles.ParticleCameraOption
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * # 路径位置约束的 CPU 共用求值
 *
 * 显式 CPU 模拟（[cn.coostack.cooparticlesapi.cparticle.simulate.CParticleCpuSimulator]）与
 * 传统 `ParticleCommand` 都从这里求值，因此两条 CPU 路线使用**同一套**路径定义、播放规则、
 * 稳定随机规则与位置约束数学，不会各自漂移。
 *
 * 数据来自 CPU 侧同版本图层：不从 GPU 回读路径纹理或粒子位置。图层缺失或版本不匹配时返回
 * `null`，调用方应整条命令跳过，而不是退回到另一条模拟路线。
 */
object CParticlePathCpuEvaluator {
    /**
     * 判定方向差分区间是否退化。
     *
     * 相邻两个进度落在同一个播放参数上时（周期性折返的端点、单程模式的夹取端点）差分恒为零，
     * 此时必须报告零方向，而不是输出一个由浮点噪声放大的随机方向。
     */
    private const val DIRECTION_EPSILON = 1.0E-9
    /**
     * 一次 CPU 侧路径求值的输入。
     *
     * @property progressMode 播放进度到曲线长度的映射方式
     * @property playMode 播放模式
     * @property offsetRadius 环绕半径；`<= 0` 表示不启用环绕偏移
     * @property playPeriodTicks 单程播放周期；`<= 0` 表示沿用粒子寿命
     * @property phaseRadians 环绕初始相位（弧度）
     * @property angularVelocityRadiansPerTick 环绕角速度（弧度 / tick）
     * @property angularVelocityScale 每粒子稳定随机的角速度倍率
     * @property bindingQuaternion 路径本地空间到目标空间的旋转
     * @property bindingScale 路径本地空间到目标空间的缩放
     * @property deltaTicks 位置预测步长（tick）
     * @property offsetMode 对出生偏移的处理方式
     * @property birth 粒子不可变的出生参考；启用出生偏移时必填，缺失则跳过命令
     */
    data class Input(
        val progressMode: CParticlePathProgressMode = CParticlePathProgressMode.ARC_LENGTH,
        val playMode: CParticlePathPlayMode = CParticlePathPlayMode.ONCE,
        val offsetRadius: Double = 0.0,
        val playPeriodTicks: Double = 0.0,
        val phaseRadians: Double = 0.0,
        val angularVelocityRadiansPerTick: Double = 0.0,
        val angularVelocityScale: Double = 1.0,
        val forwardAxis: CParticlePathForwardAxis = CParticlePathForwardAxis.MODEL_POSITIVE_X,
        val customForwardAxis: Vec3? = null,
        val bindingQuaternion: Quaternionf = Quaternionf(),
        val bindingScale: Vec3 = Vec3(1.0, 1.0, 1.0),
        val deltaTicks: Double = 1.0,
        val offsetMode: CParticlePathOffsetMode = CParticlePathOffsetMode.NONE,
        val birth: CParticlePathBirth? = null,
    )

    /**
     * 求值结果。
     *
     * @property position 应用绑定后的位置，坐标系与输入路径图层一致
     * @property direction 单位运动方向；回绕或退化时为 `Vec3.ZERO`
     * @property reachedEnd 是否到达终点（单程模式）
     * @property wrapped 本次推进是否跨越了播放周期边界
     */
    data class Result(
        val position: Vec3,
        val direction: Vec3,
        val reachedEnd: Boolean,
        val wrapped: Boolean,
    )

    /**
     * 按当前年龄求值一次路径位置约束。
     *
     * 位置取“下一 tick 的进度”，方向取该处的前向差分，因此方向与这一 tick 真正发生的位移一致，
     * 同时包含沿程推进、环绕旋转与绑定缩放带来的移动。
     *
     * @param layer 路径图层数据
     * @param slot 路径槽位号
     * @param ageTicks 粒子当前年龄
     * @param maxAgeTicks 粒子既有最大寿命
     * @param input 其余求值参数
     * @return 求值结果；图层缺失、版本过期或几何不可用时返回 `null`
     */
    fun evaluate(
        layer: FloatArray?,
        slot: Int,
        ageTicks: Double,
        maxAgeTicks: Double,
        input: Input,
    ): Result? {
        val data = layer ?: return null
        if (slot < 0 || slot >= CooPathLayer.SLOT_COUNT) return null
        val payload = data[CooPathLayer.SLOT_TABLE + slot].toInt()
        if (payload <= 0) return null
        val segmentCount = data[payload + CooPathLayer.CooPathPayload.H_SEGMENT_COUNT].toInt()
        val pointCount = data[payload + CooPathLayer.CooPathPayload.H_POINT_COUNT].toInt()
        val sampleCount = data[payload + CooPathLayer.CooPathPayload.H_SAMPLE_COUNT].toInt()
        if (segmentCount <= 0 || pointCount < 2 || sampleCount <= 0) return null

        val deltaTicks = if (input.deltaTicks.isFinite() && input.deltaTicks > 0.0) {
            input.deltaTicks
        } else {
            CParticlePathEvaluator.DEFAULT_DELTA_TICKS
        }
        // 求值对象是“下一 tick 的位置”，因此未显式给出周期时沿用寿命并按同一相位换算，
        // 否则同一组参数在 CPU 与 GPU 上会得到不同的播放进度。
        val cycle = CParticlePathEvaluator.resolveCycle(input.playPeriodTicks, maxAgeTicks, deltaTicks)
        if (cycle <= 0.0) return null

        val playModeWire = input.playMode.wireValue
        val targetAge = ageTicks + deltaTicks
        // 三个进度都必须从**原始比例**映射一次：把已经映射过的进度再送进 wrapProgress 会对
        // PingPong 做第二次折返映射，得到完全错误的相位。
        val targetTau = wrapProgress(targetAge / cycle, playModeWire)
        // 只有循环模式的参数会真正从 1 跳回 0；PingPong 的折返是连续推进，不属于跳变。
        val wrapped = playModeWire == CooPathCommandAbi.PLAY_LOOP &&
            wrapProgress(ageTicks / cycle, playModeWire) > targetTau

        val birthOffset = if (input.offsetMode == CParticlePathOffsetMode.NONE) {
            null
        } else {
            val birth = input.birth ?: return null
            val scale = input.bindingScale
            if (!birth.age.isFinite() ||
                !birth.position.x.isFinite() || !birth.position.y.isFinite() || !birth.position.z.isFinite() ||
                !scale.x.isFinite() || !scale.y.isFinite() || !scale.z.isFinite() ||
                abs(scale.x) <= 1.0E-9 || abs(scale.y) <= 1.0E-9 || abs(scale.z) <= 1.0E-9
            ) return null
            val birthTau = wrapProgress(birth.age / cycle, playModeWire)
            val initial = localAt(
                data, payload, segmentCount, pointCount, sampleCount, birth.age, birthTau, input,
            )
            // 先逆旋转再除缩放，避免非均匀缩放或旋转把世界空间出生偏移算错。
            val inverseRotation = Quaternionf(input.bindingQuaternion)
            if (inverseRotation.lengthSquared() <= 1.0E-12F) inverseRotation.identity()
            inverseRotation.normalize().conjugate()
            val localBirth = inverseRotation.transform(
                Vector3f(birth.position.x.toFloat(), birth.position.y.toFloat(), birth.position.z.toFloat()),
            )
            val offset = Vec3(localBirth.x / scale.x, localBirth.y / scale.y, localBirth.z / scale.z).subtract(initial)
            if (input.offsetMode == CParticlePathOffsetMode.BIRTH_POSITION) {
                offset
            } else {
                val sampleBase = payload + CooPathLayer.CooPathPayload.POINT_BASE +
                    pointCount * CooPathLayer.CooPathPayload.POINT_STRIDE
                val closed = data[payload + CooPathLayer.CooPathPayload.H_CLOSED] > 0.5F
                val frame = frameAt(
                    data, sampleBase, sampleCount, closed,
                    parameterAtProgress(data, payload, pointCount, sampleCount, birthTau, input.progressMode),
                )
                Vec3(offset.dot(frame.normal), offset.dot(frame.binormal), offset.dot(frame.tangent))
            }
        }
        val targetLocal = localAt(
            data, payload, segmentCount, pointCount, sampleCount, targetAge, targetTau, input, birthOffset,
        )
        // 差分区间取两个邻点映射值的较小/较大者，目标必然落在区间内。
        val rawLow = wrapProgress((targetAge - deltaTicks) / cycle, playModeWire)
        val rawHigh = wrapProgress((targetAge + deltaTicks) / cycle, playModeWire)
        val lowTau = minOf(rawLow, rawHigh)
        val highTau = maxOf(rawLow, rawHigh)
        val lowLocal = localAt(
            data, payload, segmentCount, pointCount, sampleCount, targetAge - deltaTicks, rawLow, input, birthOffset,
        )
        val highLocal = localAt(
            data, payload, segmentCount, pointCount, sampleCount, targetAge + deltaTicks, rawHigh, input, birthOffset,
        )
        // 位移方向由“时间顺序”决定，而不是区间端点顺序：PingPong 回程时后一步落在更小的进度上，
        // 若始终做 high - low 会得到与真实运动相反的符号。
        val localDelta = if (highTau - lowTau <= DIRECTION_EPSILON) {
            Vec3.ZERO
        } else {
            highLocal.subtract(lowLocal)
        }
        // 方向只在差分区间退化时清零：LOOP 跨越周期边界时两端的取值区间被夹成退化区间，
        // 位置差分是虚假的，必须报告零方向由渲染端沿用上一帧。
        // 绑定只做旋转与缩放；本地差分先按缩放变换即可得到目标空间方向，再归一化。
        // 方向保持真实运动方向；纹理前向轴仅在显式开启朝向跟随时参与姿态求解。
        val direction = normalizeOrZero(
            CParticlePathEvaluator.applyBinding(
                Vec3(
                    localDelta.x,
                    localDelta.y,
                    localDelta.z,
                ),
                CParticlePathEvaluation(
                    bindingQuaternion = input.bindingQuaternion,
                    bindingScale = input.bindingScale,
                ),
            ),
        )
        val position = CParticlePathEvaluator.applyBinding(
            targetLocal,
            CParticlePathEvaluation(
                bindingQuaternion = input.bindingQuaternion,
                bindingScale = input.bindingScale,
            ),
        )
        val reachedEnd = playModeWire == CooPathCommandAbi.PLAY_ONCE && targetTau >= 1.0
        return Result(position, direction, reachedEnd, wrapped)
    }

    /**
     * 校验图层负载携带的重建版本是否与调用方看到的版本一致。
     *
     * 传统命令在命令提交时记录图层版本，运行时校验；版本不一致说明基址已被重新分配，
     * 必须整条命令跳过，而不是读到别的路径。
     *
     * @param layer 路径图层数据
     * @param slot 路径槽位号
     * @param expectedRevision 调用方记录的图层重建版本
     * @return 版本一致且负载可用时返回 `true`
     */
    fun revisionMatches(layer: FloatArray?, slot: Int, expectedRevision: Int): Boolean {
        val data = layer ?: return false
        if (slot < 0 || slot >= CooPathLayer.SLOT_COUNT) return false
        val payload = data[CooPathLayer.SLOT_TABLE + slot].toInt()
        if (payload <= 0) return false
        return data[payload + CooPathLayer.CooPathPayload.H_LAYER_REVISION].toInt() == expectedRevision
    }

    /** 按运动方向换算朝向模式与轴向通道数值，供渲染端使用。 */
    fun orientationFor(direction: Vec3): ParticleCameraOption? =
        if (direction.lengthSqr() > 1.0E-18) ParticleCameraOption.ROTATION else null

    /**
     * 包装模式归一化。
     *
     * 直接委托 [CParticlePathEvaluator.wrapProgress]，同一套归一化只维护一处；
     * GPU shader 的 `pathWrapProgress` 与它逐分支一致。
     *
     * @param raw 未归一化的循环数
     * @param playModeWire [CooPathCommandAbi] 中的播放模式标识
     */
    fun wrapProgress(raw: Double, playModeWire: Int): Double =
        CParticlePathEvaluator.wrapProgress(raw, playModeOf(playModeWire))

    /**
     * 把 ABI 数值标识还原成播放模式。
     *
     * @param playModeWire [CooPathCommandAbi] 中的播放模式标识
     * @return 对应的播放模式；未知取值回退到单程
     */
    fun playModeOf(playModeWire: Int): CParticlePathPlayMode =
        CParticlePathPlayMode.entries.getOrElse(playModeWire) { CParticlePathPlayMode.ONCE }

    private fun localAt(
        layer: FloatArray,
        payload: Int,
        segmentCount: Int,
        pointCount: Int,
        sampleCount: Int,
        targetAge: Double,
        tau: Double,
        input: Input,
        birthOffset: Vec3? = null,
    ): Vec3 {
        val pointBase = payload + CooPathLayer.CooPathPayload.POINT_BASE
        val sampleBase = pointBase + pointCount * CooPathLayer.CooPathPayload.POINT_STRIDE
        val closed = layer[payload + CooPathLayer.CooPathPayload.H_CLOSED] > 0.5F
        val curveType = layer[payload + CooPathLayer.CooPathPayload.H_SEGMENT_TYPE].toInt()
        val u = parameterAtProgress(layer, payload, pointCount, sampleCount, tau, input.progressMode)
        val scaled = u.coerceIn(0.0, 1.0) * segmentCount
        val segment = scaled.toInt().coerceIn(0, segmentCount - 1)
        val local = (scaled - segment).coerceIn(0.0, 1.0)
        val firstIndex = pointBase + segment * CooPathLayer.CooPathPayload.POINT_STRIDE
        val secondIndex = firstIndex + CooPathLayer.CooPathPayload.POINT_STRIDE
        val first = readVec3(layer, firstIndex)
        val second = readVec3(layer, secondIndex)
        val position = if (curveType == CooPathCommandAbi.SEGMENT_LINEAR) {
            first.add(second.subtract(first).scale(local))
        } else {
            val firstControl = first.add(readVec3(layer, firstIndex + CooPathLayer.CooPathPayload.POINT_OUT_HANDLE))
            val secondControl = second.add(readVec3(layer, secondIndex + CooPathLayer.CooPathPayload.POINT_IN_HANDLE))
            cubicBezier(first, firstControl, secondControl, second, local)
        }
        val radius = CParticlePathEvaluator.usableRadius(input.offsetRadius)
        if (radius <= 0.0 && birthOffset == null) return position
        val frame = frameAt(layer, sampleBase, sampleCount, closed, u)
        val angle = input.phaseRadians +
            input.angularVelocityRadiansPerTick * input.angularVelocityScale * targetAge
        val base = position
            .add(frame.normal.scale(cos(angle) * radius))
            .add(frame.binormal.scale(sin(angle) * radius))
        if (birthOffset == null) return base
        if (input.offsetMode == CParticlePathOffsetMode.BIRTH_POSITION) return base.add(birthOffset)
        val spin = input.angularVelocityRadiansPerTick * input.angularVelocityScale *
            (targetAge - input.birth!!.age)
        val normalOffset = birthOffset.x * cos(spin) - birthOffset.y * sin(spin)
        val binormalOffset = birthOffset.x * sin(spin) + birthOffset.y * cos(spin)
        return base.add(frame.normal.scale(normalOffset))
            .add(frame.binormal.scale(binormalOffset))
            .add(frame.tangent.scale(birthOffset.z))
    }

    /** 将播放进度统一换算成曲线参数，供中心位置与出生参考基共同使用。 */
    private fun parameterAtProgress(
        layer: FloatArray,
        payload: Int,
        pointCount: Int,
        sampleCount: Int,
        tau: Double,
        mode: CParticlePathProgressMode,
    ): Double {
        if (mode == CParticlePathProgressMode.PARAMETER) return tau
        val totalLength = layer[payload + CooPathLayer.CooPathPayload.H_TOTAL_LENGTH].toDouble()
        if (totalLength <= 1.0E-9) return tau
        val sampleBase = payload + CooPathLayer.CooPathPayload.POINT_BASE +
            pointCount * CooPathLayer.CooPathPayload.POINT_STRIDE
        return parameterAtDistance(
            layer, sampleBase, sampleCount, layer[payload + CooPathLayer.CooPathPayload.H_CLOSED] > 0.5F,
            layer[payload + CooPathLayer.CooPathPayload.H_PARAM_DENOMINATOR].toInt().coerceAtLeast(1),
            tau.coerceIn(0.0, 1.0) * totalLength,
        )
    }
    private fun frameAt(
        layer: FloatArray,
        sampleBase: Int,
        sampleCount: Int,
        closed: Boolean,
        u: Double,
    ): PathFrame {
        val cyclic = closed && sampleCount > 1
        // 采样表的有效下标恒为 `0..sampleCount - 1`：闭合表把“回到首样本”的那一段
        // 只记进总长（`tables.distances` 不额外补一项），因此参数 1 必须折回下标 0，
        // 而不是当作第 sampleCount 个样本去读——那会越界读到相邻路径的数据。
        val maxIndex = sampleCount - 1
        val scaled = u.coerceIn(0.0, 1.0) * (if (cyclic) sampleCount else maxIndex)
        val index = scaled.toInt().coerceIn(0, maxIndex)
        val nextIndex = if (cyclic) (index + 1) % sampleCount else minOf(index + 1, maxIndex)
        val ratio = scaled - index
        val firstPosition = readVec3(layer, sampleBase + index * CooPathLayer.CooPathPayload.SAMPLE_STRIDE)
        val secondPosition = readVec3(layer, sampleBase + nextIndex * CooPathLayer.CooPathPayload.SAMPLE_STRIDE)
        val position = firstPosition.add(secondPosition.subtract(firstPosition).scale(ratio))
        val basisIndex = if (ratio > 0.5) nextIndex else index
        val basisBase = sampleBase + basisIndex * CooPathLayer.CooPathPayload.SAMPLE_STRIDE
        return PathFrame(
            position = position,
            normal = readVec3(layer, basisBase + CooPathLayer.CooPathPayload.SAMPLE_NORMAL),
            binormal = readVec3(layer, basisBase + CooPathLayer.CooPathPayload.SAMPLE_BINORMAL),
            tangent = readVec3(layer, basisBase + CooPathLayer.CooPathPayload.SAMPLE_TANGENT),
        )
    }

    private fun parameterAtDistance(
        layer: FloatArray,
        sampleBase: Int,
        sampleCount: Int,
        closed: Boolean,
        parameterDenominator: Int,
        distance: Double,
    ): Double {
        if (sampleCount <= 1) return 0.0
        // 累计弧长表的有效下标恒为 `0..sampleCount - 1`；闭合路径的收尾段只体现在总长里，
        // 因此二分上界不能取 sampleCount（那会读到下一段样本之外的区域）。
        val lastIndex = sampleCount - 1
        var low = 0
        var high = lastIndex
        while (low + 1 < high) {
            val middle = (low + high) ushr 1
            if (distanceAt(layer, sampleBase, middle) <= distance) low = middle else high = middle
        }
        val spanStart = distanceAt(layer, sampleBase, low)
        val spanEnd = distanceAt(layer, sampleBase, minOf(low + 1, lastIndex))
        val ratio = if (spanEnd - spanStart <= 1.0E-12) 0.0 else (distance - spanStart) / (spanEnd - spanStart)
        return ((low + ratio.coerceIn(0.0, 1.0)) / parameterDenominator.toDouble()).coerceIn(0.0, 1.0)
    }

    private fun distanceAt(layer: FloatArray, sampleBase: Int, index: Int): Double =
        layer[sampleBase + index * CooPathLayer.CooPathPayload.SAMPLE_STRIDE +
            CooPathLayer.CooPathPayload.SAMPLE_DISTANCE].toDouble()

    private fun readVec3(layer: FloatArray, index: Int): Vec3 =
        Vec3(layer[index].toDouble(), layer[index + 1].toDouble(), layer[index + 2].toDouble())

    private fun cubicBezier(first: Vec3, firstControl: Vec3, secondControl: Vec3, second: Vec3, t: Double): Vec3 {
        val inverse = 1.0 - t
        val inverseSquared = inverse * inverse
        val tSquared = t * t
        return first.scale(inverseSquared * inverse)
            .add(firstControl.scale(3.0 * inverseSquared * t))
            .add(secondControl.scale(3.0 * inverse * tSquared))
            .add(second.scale(tSquared * t))
    }

    private fun normalizeOrZero(vector: Vec3): Vec3 {
        val length = vector.length()
        return if (length.isFinite() && length > 1.0E-9) vector.scale(1.0 / length) else Vec3.ZERO
    }
}
