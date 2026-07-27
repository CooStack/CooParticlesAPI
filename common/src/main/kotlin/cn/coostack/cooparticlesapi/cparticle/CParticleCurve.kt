package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.network.particle.emitters.command.curve.FloatCurve
import cn.coostack.cooparticlesapi.network.particle.emitters.command.curve.BezierFloatKeyframe
import cn.coostack.cooparticlesapi.network.particle.emitters.command.curve.BezierKeyframeFloatCurve
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec

/**
 * GPU 和 CPU 共用的生命周期标量曲线。
 *
 * 示例：`CParticleCurve.bezier(...)` 可以为 alpha 或 scale 保留三次贝塞尔控制柄。
 * 禁止：贝塞尔时间控制柄不能交叉，否则一个生命周期位置可能对应多个参数。
 *
 * @property packed 固定布局的锚点数组，顺序为 `[t0..t7, v0..v7]`
 * @property keyCount 有效关键帧数量，范围为 `1..8`
 * @property interpolation 相邻关键帧的插值方式
 * @property packedHandles 每个关键帧的 `outX, outValue, inX, inValue`
 */
class CParticleCurve private constructor(
    packed: FloatArray,
    val keyCount: Int,
    val interpolation: CParticleCurveInterpolation,
    packedHandles: FloatArray,
) {
    /**
     * codec、descriptor 和 renderer 上传共用的内部锚点存储。
     *
     * 示例：renderer 直接上传此数组，不会分配公开快照。
     * 禁止：内部调用方必须把此数组视为不可变数据。
     */
    internal val packedData: FloatArray = packed

    /**
     * codec、descriptor 和 renderer 上传共用的内部控制柄存储。
     *
     * 示例：注册外观时会把有效项复制到不可变定义中。
     * 禁止：内部调用方不能修改已经校验的控制柄。
     */
    internal val packedHandleData: FloatArray = packedHandles

    /**
     * 返回固定布局锚点数据的快照。
     *
     * 示例：可用 `curve.packed.toList()` 做诊断或测试。
     * 禁止：修改返回数组不会改变曲线；需要修改时应创建新曲线。
     */
    val packed: FloatArray
        get() = packedData.copyOf()

    /**
     * 返回固定布局贝塞尔控制柄的快照。
     *
     * 示例：前四个值是第 0 个关键帧的 `outX, outValue, inX, inValue`。
     * 禁止：修改返回数组不会改变曲线。
     */
    val packedHandles: FloatArray
        get() = packedHandleData.copyOf()

    /**
     * 在归一化时间上采样曲线。
     *
     * 示例：`sample(0.5f)` 返回生命周期中点的倍率。
     * 禁止：调用方不需要预先钳制时间，本方法会限制到 `0..1`。
     *
     * @param t 归一化生命周期或循环进度
     * @return 当前曲线倍率
     */
    internal fun sample(t: Float): Float {
        val sampleT = t.coerceIn(0f, 1f)
        if (sampleT <= packedData[0]) return packedData[MAX_KEYS]
        for (i in 1 until keyCount) {
            if (sampleT <= packedData[i]) {
                val t0 = packedData[i - 1]
                val t1 = packedData[i]
                val from = packedData[MAX_KEYS + i - 1]
                val to = packedData[MAX_KEYS + i]
                if (interpolation == CParticleCurveInterpolation.LINEAR) {
                    val progress = if (t1 > t0) (sampleT - t0) / (t1 - t0) else 0f
                    return from + (to - from) * progress
                }
                val previousHandle = (i - 1) * CParticleBezierMath.SCALAR_HANDLE_COMPONENTS
                val currentHandle = i * CParticleBezierMath.SCALAR_HANDLE_COMPONENTS
                val parameter = CParticleBezierMath.parameterAt(
                    sampleT,
                    t0,
                    packedHandleData[previousHandle],
                    t1,
                    packedHandleData[currentHandle + 2],
                )
                return CParticleBezierMath.cubic(
                    parameter,
                    from,
                    from + packedHandleData[previousHandle + 1],
                    to + packedHandleData[currentHandle + 3],
                    to,
                )
            }
        }
        return packedData[MAX_KEYS + keyCount - 1]
    }

    /**
     * 构建并序列化 CParticle 标量曲线。
     *
     * 示例：[linear] 保留紧凑的旧版 payload，[bezier] 使用扩展 payload。
     * 禁止：增加 [MAX_KEYS] 时必须同步更新所有 shader 数组和 descriptor 布局。
     */
    companion object {
        /** 标量与颜色 GPU 曲线允许的最大关键帧数量。 */
        const val MAX_KEYS = 8

        private const val EXTENDED_CODEC_MARKER = 0

        /**
         * 生命周期标量曲线的网络 codec。
         *
         * 示例：`@CodecField var alphaCurve = CParticleCurve.fadeInOut()`。
         * 禁止：解码端不会接受空曲线或超过 [MAX_KEYS] 的关键帧。
         */
        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, CParticleCurve> = StreamCodec.of(
            { buf, curve ->
                if (curve.interpolation == CParticleCurveInterpolation.LINEAR) {
                    buf.writeByte(curve.keyCount)
                    for (i in 0 until curve.keyCount) {
                        buf.writeFloat(curve.packedData[i])
                        buf.writeFloat(curve.packedData[MAX_KEYS + i])
                    }
                } else {
                    buf.writeByte(EXTENDED_CODEC_MARKER)
                    buf.writeByte(curve.interpolation.wireId)
                    buf.writeByte(curve.keyCount)
                    for (i in 0 until curve.keyCount) {
                        val handleOffset = i * CParticleBezierMath.SCALAR_HANDLE_COMPONENTS
                        buf.writeFloat(curve.packedData[i])
                        buf.writeFloat(curve.packedData[MAX_KEYS + i])
                        repeat(CParticleBezierMath.SCALAR_HANDLE_COMPONENTS) { component ->
                            buf.writeFloat(curve.packedHandleData[handleOffset + component])
                        }
                    }
                }
            },
            { buf ->
                val markerOrCount = buf.readUnsignedByte().toInt()
                if (markerOrCount != EXTENDED_CODEC_MARKER) {
                    require(markerOrCount in 1..MAX_KEYS) {
                        "curve key count must be in 1..$MAX_KEYS: $markerOrCount"
                    }
                    of(*Array(markerOrCount) { buf.readFloat() to buf.readFloat() })
                } else {
                    val interpolation = CParticleCurveInterpolation.fromWireId(buf.readUnsignedByte().toInt())
                    require(interpolation == CParticleCurveInterpolation.CUBIC_BEZIER) {
                        "extended scalar curve must use cubic Bezier interpolation"
                    }
                    val count = buf.readUnsignedByte().toInt()
                    require(count in 1..MAX_KEYS) { "curve key count must be in 1..$MAX_KEYS: $count" }
                    bezier(*Array(count) {
                        BezierFloatKeyframe(
                            time = buf.readFloat().toDouble(),
                            value = buf.readFloat().toDouble(),
                            outX = buf.readFloat().toDouble(),
                            outY = buf.readFloat().toDouble(),
                            inX = buf.readFloat().toDouble(),
                            inY = buf.readFloat().toDouble(),
                        )
                    })
                }
            },
        )

        /**
         * 用线段连接一组标量关键帧。
         *
         * `key.first` 是归一化时间，`0` 表示出生、`1` 表示生命周期结束；
         * `key.second` 是该时刻的倍率值。关键帧必须按时间升序排列。
         * 示例：`of(0f to 0f, 0.2f to 1f, 1f to 0f)` 会先淡入再淡出。
         * 禁止：不要把粒子的 tick 年龄直接当作 key；应先除以 `maxAge`。
         *
         * @param keys 按归一化时间排列的 `time to value` 关键帧
         * @return 使用线性插值的曲线
         * @throws IllegalArgumentException 如果关键帧为空、时间越界或数据不是有限值
         */
        @JvmStatic
        fun of(vararg keys: Pair<Float, Float>): CParticleCurve {
            require(keys.isNotEmpty()) { "curve requires at least 1 key" }
            val count = keys.size.coerceAtMost(MAX_KEYS)
            val packed = FloatArray(MAX_KEYS * 2)
            var previousTime = Float.NEGATIVE_INFINITY
            for (i in 0 until count) {
                val (time, value) = keys[i]
                require(time.isFinite() && time in 0f..1f) {
                    "curve time must be finite and in 0..1"
                }
                require(time >= previousTime) { "curve keys must be sorted by time" }
                require(value.isFinite()) { "curve value must be finite" }
                packed[i] = time
                packed[MAX_KEYS + i] = value
                previousTime = time
            }
            return CParticleCurve(
                packed,
                count,
                CParticleCurveInterpolation.LINEAR,
                FloatArray(MAX_KEYS * CParticleBezierMath.SCALAR_HANDLE_COMPONENTS),
            )
        }

        /**
         * 创建一条可由 GPU 稳定求解时间控制柄的三次贝塞尔曲线。
         *
         * 示例：`bezier(BezierFloatKeyframe(0.0, 0.0, outX = 25.0), ...)`。
         * 禁止：关键帧必须有序，每一段的 X 控制柄都必须保持单调。
         *
         * @param keys 标量锚点以及相对出入控制柄
         * @return 保留给定贝塞尔数据的曲线
         * @throws IllegalArgumentException 如果关键帧或控制柄无法在 GPU 上安全求值
         */
        @JvmStatic
        fun bezier(vararg keys: BezierFloatKeyframe): CParticleCurve {
            require(keys.size in 1..MAX_KEYS) { "Bezier curve requires 1..$MAX_KEYS keys" }
            val packed = FloatArray(MAX_KEYS * 2)
            val handles = FloatArray(MAX_KEYS * CParticleBezierMath.SCALAR_HANDLE_COMPONENTS)
            keys.forEachIndexed { index, key ->
                require(
                    key.time.isFinite() && key.value.isFinite() && key.outX.isFinite() &&
                            key.outY.isFinite() && key.inX.isFinite() && key.inY.isFinite()
                ) { "Bezier curve keys and handles must be finite" }
                require(key.time in 0.0..1.0) { "Bezier curve time must be in 0..1" }
                val time = key.time.toFloat()
                val value = key.value.toFloat()
                val handleOffset = index * CParticleBezierMath.SCALAR_HANDLE_COMPONENTS
                val outX = key.outX.toFloat()
                val outY = key.outY.toFloat()
                val inX = key.inX.toFloat()
                val inY = key.inY.toFloat()
                require(time.isFinite() && value.isFinite() && outX.isFinite() && outY.isFinite() &&
                        inX.isFinite() && inY.isFinite()) {
                    "Bezier curve keys and handles must fit finite GPU floats"
                }
                packed[index] = time
                packed[MAX_KEYS + index] = value
                handles[handleOffset] = outX
                handles[handleOffset + 1] = outY
                handles[handleOffset + 2] = inX
                handles[handleOffset + 3] = inY
                if (index > 0) {
                    val previousHandle = handleOffset - CParticleBezierMath.SCALAR_HANDLE_COMPONENTS
                    CParticleBezierMath.requireMonotonicSegment(
                        packed[index - 1],
                        handles[previousHandle],
                        packed[index],
                        handles[handleOffset + 2],
                    )
                }
            }
            return CParticleCurve(packed, keys.size, CParticleCurveInterpolation.CUBIC_BEZIER, handles)
        }

        /**
         * 创建贯穿整个生命周期的线性倍率曲线。
         *
         * 示例：`linear(1f, 0f)` 会从出生时的 `1` 均匀降到死亡时的 `0`。
         * 禁止：[from] 和 [to] 必须是有限值。
         *
         * @param from 归一化时间 `0` 时的倍率
         * @param to 归一化时间 `1` 时的倍率
         * @return 两个关键帧组成的线性曲线
         */
        @JvmStatic
        fun linear(from: Float, to: Float): CParticleCurve = of(0f to from, 1f to to)

        /**
         * 创建 `0 -> peak -> peak -> 0` 的线性淡入淡出曲线。
         *
         * [fadeIn] 和 [fadeOut] 都是归一化生命周期位置，不是持续时间。
         * 示例：`fadeInOut(fadeIn = 0.12f, fadeOut = 0.82f)` 表示前 12% 淡入，
         * 中间保持 [peak]，从 82% 生命周期处开始淡出。
         * 禁止：[fadeIn] 不能晚于 [fadeOut]，二者也不能超出 `0..1`。
         *
         * @param peak 淡入完成后保持的倍率
         * @param fadeIn 达到 [peak] 的归一化时间
         * @param fadeOut 开始从 [peak] 淡出的归一化时间
         * @return 四个关键帧组成的线性曲线
         */
        @JvmStatic
        fun fadeInOut(peak: Float = 1f, fadeIn: Float = 0.15f, fadeOut: Float = 0.75f): CParticleCurve =
            of(0f to 0f, fadeIn to peak, fadeOut to peak, 1f to 0f)

        /**
         * 从现有 [FloatCurve] 创建 GPU 曲线。
         *
         * [BezierKeyframeFloatCurve] 会保留原始控制柄，其他实现仍均匀采样 8 个线性点。
         * 示例：emitter 贝塞尔曲线转换后仍使用 [CParticleCurveInterpolation.CUBIC_BEZIER]。
         * 禁止：非单调贝塞尔时间柄不能直接转换到 GPU 曲线，而会回退为线性采样。
         *
         * @param curve emitter 标量曲线
         * @return 可上传到 CParticle shader 的曲线
         */
        @JvmStatic
        fun fromFloatCurve(curve: FloatCurve): CParticleCurve {
            if (curve is BezierKeyframeFloatCurve) {
                try {
                    return bezier(*curve.frames().toTypedArray())
                } catch (_: IllegalArgumentException) {
                    // 旧 emitter 曲线允许固定大小、单调的 GPU 求解器无法精确保留的形状。
                }
            }
            return sampledLinear(curve)
        }

        /**
         * 把任意 emitter 曲线转换成 8 个经过校验的 GPU 线性采样点。
         *
         * 示例：包含 9 个关键帧的 emitter 曲线会走此兼容路径。
         * 禁止：非有限采样值不能上传到 GPU。
         *
         * @param curve 在 `0..1` 范围内采样的源曲线
         * @return 包含 8 个关键帧的线性近似曲线
         */
        private fun sampledLinear(curve: FloatCurve): CParticleCurve =
            of(*Array(MAX_KEYS) { index ->
                val time = index / (MAX_KEYS - 1f)
                time to curve.sample(time.toDouble()).toFloat()
            })
    }
}
