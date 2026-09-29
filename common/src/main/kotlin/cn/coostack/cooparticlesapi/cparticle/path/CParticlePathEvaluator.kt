package cn.coostack.cooparticlesapi.cparticle.path

import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.floor

/**
 * # 一次路径位置约束求值的输入
 *
 * 描述“在给定归一化播放参数下，粒子应该在路径的什么位置、朝哪个方向”。它与后端无关：
 * GPU compute 与 CPU 模拟器读取同一组数值，因此两侧结果在数值容差内一致。
 *
 * 环绕相位与姿态是两种不同语义，因此分成两组字段，不使用一个含糊的 `rotation` 参数混合它们：
 * - [phaseRadians] 是绕路径的**初始相位**，决定粒子从横截面的哪个方向开始环绕。
 * - [angularVelocityRadiansPerTick] 是**环绕角速度**，决定相位随进度如何推进。
 *
 * 粒子自身姿态（俯仰/滚转偏移）由调用方在渲染端设置，不在这里求值：路径约束只负责给出
 * 运动方向，姿态是模型自己的属性。
 *
 * @property progressMode 播放进度到曲线长度的映射方式
 * @property offsetRadius 环绕半径；`<= 0` 表示不启用环绕偏移
 * @property phaseRadians 环绕初始相位（弧度）
 * @property angularVelocityRadiansPerTick 环绕角速度（弧度 / tick）
 * @property angularVelocityScale 每粒子稳定随机的角速度倍率；1 表示不缩放
 * @property forwardAxis 模型前方轴约定
 * @property customForwardAxis [CParticlePathForwardAxis.CUSTOM] 时使用的显式前方轴
 * @property bindingQuaternion 路径本地空间到目标空间的旋转
 * @property bindingScale 路径本地空间到目标空间的缩放
 * @property deltaTicks 位置预测步长（tick）；`1` 表示求值“下一 tick 的位置”
 */
data class CParticlePathEvaluation(
    val progressMode: CParticlePathProgressMode = CParticlePathProgressMode.ARC_LENGTH,
    val offsetRadius: Double = 0.0,
    val playPeriodTicks: Double = CParticlePathEvaluator.LIFETIME_PERIOD,
    val phaseRadians: Double = 0.0,
    val angularVelocityRadiansPerTick: Double = 0.0,
    val angularVelocityScale: Double = 1.0,
    val forwardAxis: CParticlePathForwardAxis = CParticlePathForwardAxis.MODEL_POSITIVE_X,
    val customForwardAxis: Vec3? = null,
    val bindingQuaternion: Quaternionf = Quaternionf(),
    val bindingScale: Vec3 = UNIT_SCALE,
    val deltaTicks: Double = CParticlePathEvaluator.DEFAULT_DELTA_TICKS,
) {
    private companion object {
        val UNIT_SCALE = Vec3(1.0, 1.0, 1.0)
    }
}

/**
 * # 路径位置约束的公共求值原语
 *
 * 这里只放 GPU 与 CPU 都必须逐字一致的部分：播放参数归一化、播放周期换算、绑定变换、
 * 四元数与参考基读取。完整的位置与方向求值在 [CParticlePathCpuEvaluator]（CPU 侧）与
 * `path_constraint.glsl`（GPU 侧），两者使用本对象提供的同一套原语，避免相位与变换语义各自漂移。
 */
object CParticlePathEvaluator {
    /** 位置预测的默认步长：1 tick 后，即模拟本 tick 实际会写入的位置。 */
    const val DEFAULT_DELTA_TICKS = 1.0

    /**
     * 播放周期哨兵：表示“沿用粒子既有寿命”。
     *
     * 求值对象是“下一 tick 的位置”，因此这个默认值按同一相位换算为 `寿命 / 步长` 个循环。
     * 这是**哨兵**而不是可用周期；调用方显式指定周期时必须为正数。
     */
    const val LIFETIME_PERIOD = 0.0

    /**
     * 由「一个完整播放循环的时长」换算实际用于归一化的循环长度。
     *
     * @param playPeriodTicks 显式周期；`<= 0` 表示沿用 [maxAgeTicks]
     * @param maxAgeTicks 粒子既有最大寿命
     * @param deltaTicks 求值步长
     * @return 实际循环时长；参数不可用时返回 `0` 表示无法求值
     */
    fun resolveCycle(playPeriodTicks: Double, maxAgeTicks: Double, deltaTicks: Double): Double {
        if (playPeriodTicks.isFinite() && playPeriodTicks > 0.0) return playPeriodTicks
        val step = if (deltaTicks.isFinite() && deltaTicks > 0.0) deltaTicks else DEFAULT_DELTA_TICKS
        val cycle = maxAgeTicks / step
        return if (cycle.isFinite() && cycle > 0.0) cycle else 0.0
    }

    /**
     * 把“已经走过的循环数”归一化到 `0..1` 的播放参数。
     *
     * [CParticlePathPlayMode.ONCE] 夹取到 `[0, 1]`；[CParticlePathPlayMode.LOOP] 取小数部分；
     * [CParticlePathPlayMode.PING_PONG] 把一个小数周期折成往返，端点立即反向。
     * GPU shader 的 `pathWrapProgress` 与本方法逐分支一致；任何差异都会表现为同一组参数下的相位漂移。
     *
     * @param raw 未归一化的循环数，可以取任意实数
     * @param mode 播放模式
     * @return 归一化播放参数，取值域 `0..1`
     */
    fun wrapProgress(raw: Double, mode: CParticlePathPlayMode): Double {
        if (!raw.isFinite()) return 0.0
        return when (mode) {
            CParticlePathPlayMode.ONCE -> raw.coerceIn(0.0, 1.0)
            CParticlePathPlayMode.LOOP -> raw - floor(raw)
            CParticlePathPlayMode.PING_PONG -> {
                val normalized = raw - floor(raw)
                if (normalized <= 0.5) normalized * 2.0 else 2.0 - normalized * 2.0
            }
        }
    }

    /**
     * 把本地位置按绑定旋转与缩放送到目标空间。
     *
     * 平移不在此处：命令与粒子位置都处于 system 原点相对坐标，路径自然随 system 一起移动。
     * 恒等绑定走短路返回，不产生临时对象。
     *
     * @param local 路径本地空间位置
     * @param evaluation 求值参数
     * @return 目标空间位置
     */
    fun applyBinding(local: Vec3, evaluation: CParticlePathEvaluation): Vec3 {
        val quaternion = evaluation.bindingQuaternion
        val scale = evaluation.bindingScale
        val identityRotation = quaternion.x == 0F && quaternion.y == 0F && quaternion.z == 0F && quaternion.w == 1F
        val identityScale = scale.x == 1.0 && scale.y == 1.0 && scale.z == 1.0
        if (identityRotation && identityScale) return local
        val scaled = Vector3f(
            (local.x * scale.x).toFloat(),
            (local.y * scale.y).toFloat(),
            (local.z * scale.z).toFloat(),
        )
        if (!identityRotation) quaternion.transform(scaled)
        return Vec3(scaled.x.toDouble(), scaled.y.toDouble(), scaled.z.toDouble())
    }

    /**
     * 取某个全局参数处的位置与连续横截面参考基。
     *
     * 位置做线性插值，参考基取最近样本，避免插值让基失去正交性。
     *
     * @param table 预采样表
     * @param u 全局曲线参数，取值域 `0..1`
     * @return 该处的位置与参考基
     */
    fun frameAt(table: PathSampleTable, u: Double): PathFrame {
        val sampleCount = table.sampleCount
        if (sampleCount <= 0) return PathFrame(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO)
        if (sampleCount == 1) {
            return PathFrame(table.positions[0], table.normals[0], table.binormals[0], table.tangents[0])
        }
        val scaled = u.coerceIn(0.0, 1.0) * (sampleCount - 1)
        val index = floor(scaled).toInt().coerceIn(0, sampleCount - 1)
        val nextIndex = if (table.closed) (index + 1) % sampleCount else (index + 1).coerceAtMost(sampleCount - 1)
        val ratio = scaled - index
        val position = if (nextIndex == index) {
            table.positions[index]
        } else {
            table.positions[index].add(table.positions[nextIndex].subtract(table.positions[index]).scale(ratio))
        }
        val base = if (ratio > 0.5) nextIndex else index
        return PathFrame(position, table.normals[base], table.binormals[base], table.tangents[base])
    }

    /**
     * 把“模型前方轴对齐运动方向”的修正作用到方向向量上。
     *
     * 框架既有的朝向换算约定**模型前方为本地 `+X`**。当模型实际前方是别的轴时，先把方向向量
     * 按“把运动方向转到模型前方轴”的旋转的**逆**作用一次，得到的向量恰好满足：
     * 模型前方轴转到它之后正好对齐原来的运动方向。这样模型不会侧着前进。
     *
     * 修正只在 [forwardAxis] 不是 [CParticlePathForwardAxis.MODEL_POSITIVE_X] 时启用。
     * 两向量方向相反时旋转不唯一，此时保持原方向，不引入任意轴。
     *
     * @param localDirection 已经过绑定缩放的本地运动方向
     * @param forwardAxis 模型前方轴约定
     * @param customForwardAxis [CParticlePathForwardAxis.CUSTOM] 时使用的显式前方轴
     * @return 修正后的方向
     */
    fun correctForwardAxis(
        localDirection: Vec3,
        forwardAxis: CParticlePathForwardAxis,
        customForwardAxis: Vec3?,
    ): Vec3 {
        if (forwardAxis == CParticlePathForwardAxis.MODEL_POSITIVE_X) return localDirection
        if (localDirection.lengthSqr() <= 1.0E-18) return localDirection
        val modelForward = CParticlePathForwardAxis.resolve(forwardAxis, customForwardAxis)
        val axis = modelForward.cross(localDirection)
        if (axis.lengthSqr() <= 1.0E-18) return localDirection
        // 与 GLSL pathCorrectForwardAxis 使用同一组公式：q = normalize(cross(from, to), 1 + dot)，再取逆。
        val quaternion = Quaternionf(
            axis.x.toFloat(),
            axis.y.toFloat(),
            axis.z.toFloat(),
            (1.0 + modelForward.dot(localDirection)).toFloat(),
        )
        if (quaternion.lengthSquared() <= 1.0E-12F) return localDirection
        quaternion.normalize().conjugate()
        val corrected = quaternion.transform(
            Vector3f(
                localDirection.x.toFloat(),
                localDirection.y.toFloat(),
                localDirection.z.toFloat(),
            ),
        )
        return Vec3(corrected.x.toDouble(), corrected.y.toDouble(), corrected.z.toDouble())
    }

    /** 把四元数装入 flat 数组；顺序为 `x, y, z, w`。 */
    fun writeQuaternion(target: FloatArray, base: Int, quaternion: Quaternionf) {
        target[base] = quaternion.x
        target[base + 1] = quaternion.y
        target[base + 2] = quaternion.z
        target[base + 3] = quaternion.w
    }

    /** 从 flat 数组读取四元数；零四元数会回退为单位四元数，避免产生 NaN。 */
    fun readQuaternion(source: FloatArray, base: Int): Quaternionf {
        val quaternion = Quaternionf(source[base], source[base + 1], source[base + 2], source[base + 3])
        if (quaternion.lengthSquared() <= 1.0E-12F) return Quaternionf()
        return quaternion.normalize()
    }

    /** 由矩阵取旋转四元数；传入的单位矩阵会返回单位四元数。 */
    fun quaternionOf(matrix: Matrix4f): Quaternionf = Quaternionf().setFromUnnormalized(matrix)

    /** 判定半径是否可用；非有限值与负数都视为不启用环绕。 */
    fun usableRadius(value: Double): Double = if (value.isFinite() && value > 0.0) value else 0.0
}

/**
 * 路径横截面参考基。
 *
 * @property position 该处位置
 * @property normal 横截面第一参考轴（单位向量）
 * @property binormal 横截面第二参考轴（单位向量）
 * @property tangent 该处单位切线
 */
data class PathFrame(
    val position: Vec3,
    val normal: Vec3,
    val binormal: Vec3,
    val tangent: Vec3,
)
