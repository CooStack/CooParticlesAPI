package cn.coostack.cooparticlesapi.cparticle

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import org.joml.Vector3f
import org.joml.Vector3fc

/**
 * GPU 和 CPU 共用的生命周期 RGB 倍率曲线。
 *
 * 示例：`CParticleColorCurve.bezier(...)` 可以让三个颜色通道沿同一时间曲线平滑变化。
 * 禁止：颜色值控制柄是相对关键帧颜色的偏移量，不是绝对 RGB 值。
 *
 * @property packedTimes 固定长度的关键帧时间数组
 * @property packedColors 固定长度的 RGB 锚点数组
 * @property keyCount 有效关键帧数量，范围为 `1..8`
 * @property interpolation 相邻颜色关键帧的插值方式
 * @property packedOutHandles 每个关键帧的 `outX, outR, outG, outB`
 * @property packedInHandles 每个关键帧的 `inX, inR, inG, inB`
 */
class CParticleColorCurve private constructor(
    packedTimes: FloatArray,
    packedColors: FloatArray,
    val keyCount: Int,
    val interpolation: CParticleCurveInterpolation,
    packedOutHandles: FloatArray,
    packedInHandles: FloatArray,
) {
    /**
     * codec、descriptor 和 renderer 上传共用的内部关键帧时间存储。
     *
     * 示例：renderer 直接上传此数组，不会分配公开快照。
     * 禁止：内部调用方必须把此数组视为不可变数据。
     */
    internal val packedTimeData: FloatArray = packedTimes

    /**
     * codec、descriptor 和 renderer 上传共用的内部 RGB 存储。
     *
     * 示例：每三个元素保存一个关键帧的红、绿、蓝值。
     * 禁止：内部调用方不能修改已经校验的颜色。
     */
    internal val packedColorData: FloatArray = packedColors

    /**
     * codec、descriptor 和 renderer 上传共用的内部出控制柄存储。
     *
     * 示例：每四个元素保存 `outX, outR, outG, outB`。
     * 禁止：内部调用方不能修改已经校验的控制柄。
     */
    internal val packedOutHandleData: FloatArray = packedOutHandles

    /**
     * codec、descriptor 和 renderer 上传共用的内部入控制柄存储。
     *
     * 示例：每四个元素保存 `inX, inR, inG, inB`。
     * 禁止：内部调用方不能修改已经校验的控制柄。
     */
    internal val packedInHandleData: FloatArray = packedInHandles

    /**
     * 返回固定布局关键帧时间的快照。
     *
     * 示例：`curve.packedTimes.take(curve.keyCount)` 返回有效时间。
     * 禁止：修改返回数组不会改变曲线。
     */
    val packedTimes: FloatArray
        get() = packedTimeData.copyOf()

    /**
     * 返回固定布局 RGB 锚点的快照。
     *
     * 示例：前三个元素保存第 0 个关键帧的 RGB 倍率。
     * 禁止：修改返回数组不会改变曲线。
     */
    val packedColors: FloatArray
        get() = packedColorData.copyOf()

    /**
     * 返回 `X, R, G, B` 出控制柄的快照。
     *
     * 示例：`curve.packedOutHandles.take(4)` 可检查第 0 个关键帧。
     * 禁止：修改返回数组不会改变曲线。
     */
    val packedOutHandles: FloatArray
        get() = packedOutHandleData.copyOf()

    /**
     * 返回 `X, R, G, B` 入控制柄的快照。
     *
     * 示例：`curve.packedInHandles.take(4)` 可检查第 0 个关键帧。
     * 禁止：修改返回数组不会改变曲线。
     */
    val packedInHandles: FloatArray
        get() = packedInHandleData.copyOf()

    /**
     * 在归一化时间上采样 RGB 倍率。
     *
     * 示例：`sample(0.5f, target)` 会复用 [target] 保存中点颜色。
     * 禁止：[destination] 不能与曲线内部数据共享，因为结果会覆盖其内容。
     *
     * @param t 归一化生命周期或循环进度
     * @param destination 接收采样结果的可变向量
     * @return [destination]
     */
    internal fun sample(t: Float, destination: Vector3f): Vector3f {
        val sampleT = t.coerceIn(0f, 1f)
        if (sampleT <= packedTimeData[0]) return colorAt(0, destination)
        for (i in 1 until keyCount) {
            if (sampleT <= packedTimeData[i]) {
                val t0 = packedTimeData[i - 1]
                val t1 = packedTimeData[i]
                val fromOffset = (i - 1) * COLOR_COMPONENTS
                val toOffset = i * COLOR_COMPONENTS
                if (interpolation == CParticleCurveInterpolation.CUBIC_BEZIER) {
                    val previousHandle = (i - 1) * HANDLE_COMPONENTS
                    val currentHandle = i * HANDLE_COMPONENTS
                    val parameter = CParticleBezierMath.parameterAt(
                        sampleT,
                        t0,
                        packedOutHandleData[previousHandle],
                        t1,
                        packedInHandleData[currentHandle],
                    )
                    return destination.set(
                        sampleBezierColor(parameter, fromOffset, toOffset, previousHandle, currentHandle, 0),
                        sampleBezierColor(parameter, fromOffset, toOffset, previousHandle, currentHandle, 1),
                        sampleBezierColor(parameter, fromOffset, toOffset, previousHandle, currentHandle, 2),
                    )
                }
                val progress = if (t1 > t0) (sampleT - t0) / (t1 - t0) else 0f
                return destination.set(
                    lerp(packedColorData[fromOffset], packedColorData[toOffset], progress),
                    lerp(packedColorData[fromOffset + 1], packedColorData[toOffset + 1], progress),
                    lerp(packedColorData[fromOffset + 2], packedColorData[toOffset + 2], progress),
                )
            }
        }
        return colorAt(keyCount - 1, destination)
    }

    /**
     * 计算当前贝塞尔曲线段的一个 RGB 分量。
     *
     * 示例：分量 `1` 使用绿色通道自己的值控制柄求值。
     * 禁止：控制柄的 X 存在分量 `0`，它不是颜色分量。
     *
     * @param parameter 求出的三次贝塞尔参数
     * @param fromOffset 前一个关键帧的 RGB 偏移
     * @param toOffset 当前关键帧的 RGB 偏移
     * @param previousHandle 前一个关键帧的出控制柄偏移
     * @param currentHandle 当前关键帧的入控制柄偏移
     * @param component `0..2` 范围内的 RGB 分量下标
     * @return 求值后的颜色分量
     */
    private fun sampleBezierColor(
        parameter: Float,
        fromOffset: Int,
        toOffset: Int,
        previousHandle: Int,
        currentHandle: Int,
        component: Int,
    ): Float {
        val from = packedColorData[fromOffset + component]
        val to = packedColorData[toOffset + component]
        return CParticleBezierMath.cubic(
            parameter,
            from,
            from + packedOutHandleData[previousHandle + 1 + component],
            to + packedInHandleData[currentHandle + 1 + component],
            to,
        )
    }

    private fun colorAt(index: Int, destination: Vector3f): Vector3f {
        val offset = index * COLOR_COMPONENTS
        return destination.set(
            packedColorData[offset],
            packedColorData[offset + 1],
            packedColorData[offset + 2],
        )
    }

    /**
     * 构建并序列化 CParticle RGB 曲线。
     *
     * 示例：[linear] 保留紧凑的旧版 payload，[bezier] 额外写入控制柄。
     * 禁止：修改打包分量数量时必须同步修改 descriptor 和 shader。
     */
    companion object {
        /** GPU 布局允许的最大 RGB 关键帧数量。 */
        const val MAX_KEYS = CParticleCurve.MAX_KEYS
        private const val COLOR_COMPONENTS = 3
        internal const val HANDLE_COMPONENTS = 4
        private const val EXTENDED_CODEC_MARKER = 0

        /**
         * 生命周期颜色乘数曲线的网络 codec。
         *
         * 示例：`@CodecField var colorCurve = CParticleColorCurve.linear(from, to)`。
         * 禁止：解码端不会接受空曲线或超过 [MAX_KEYS] 的关键帧。
         */
        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, CParticleColorCurve> = StreamCodec.of(
            { buf, curve ->
                if (curve.interpolation == CParticleCurveInterpolation.LINEAR) {
                    buf.writeByte(curve.keyCount)
                    for (i in 0 until curve.keyCount) {
                        val colorOffset = i * COLOR_COMPONENTS
                        buf.writeFloat(curve.packedTimeData[i])
                        buf.writeFloat(curve.packedColorData[colorOffset])
                        buf.writeFloat(curve.packedColorData[colorOffset + 1])
                        buf.writeFloat(curve.packedColorData[colorOffset + 2])
                    }
                } else {
                    buf.writeByte(EXTENDED_CODEC_MARKER)
                    buf.writeByte(curve.interpolation.wireId)
                    buf.writeByte(curve.keyCount)
                    for (i in 0 until curve.keyCount) {
                        val colorOffset = i * COLOR_COMPONENTS
                        val handleOffset = i * HANDLE_COMPONENTS
                        buf.writeFloat(curve.packedTimeData[i])
                        repeat(COLOR_COMPONENTS) { component ->
                            buf.writeFloat(curve.packedColorData[colorOffset + component])
                        }
                        repeat(HANDLE_COMPONENTS) { component ->
                            buf.writeFloat(curve.packedOutHandleData[handleOffset + component])
                        }
                        repeat(HANDLE_COMPONENTS) { component ->
                            buf.writeFloat(curve.packedInHandleData[handleOffset + component])
                        }
                    }
                }
            },
            { buf ->
                val markerOrCount = buf.readUnsignedByte().toInt()
                if (markerOrCount != EXTENDED_CODEC_MARKER) {
                    require(markerOrCount in 1..MAX_KEYS) {
                        "color curve key count must be in 1..$MAX_KEYS: $markerOrCount"
                    }
                    of(*Array(markerOrCount) {
                        buf.readFloat() to Vector3f(buf.readFloat(), buf.readFloat(), buf.readFloat())
                    })
                } else {
                    val interpolation = CParticleCurveInterpolation.fromWireId(buf.readUnsignedByte().toInt())
                    require(interpolation == CParticleCurveInterpolation.CUBIC_BEZIER) {
                        "extended color curve must use cubic Bezier interpolation"
                    }
                    val count = buf.readUnsignedByte().toInt()
                    require(count in 1..MAX_KEYS) { "color curve key count must be in 1..$MAX_KEYS: $count" }
                    bezier(*Array(count) {
                        CParticleBezierColorKeyframe(
                            time = buf.readFloat().toDouble(),
                            value = Vector3f(buf.readFloat(), buf.readFloat(), buf.readFloat()),
                            outX = buf.readFloat().toDouble(),
                            outValueOffset = Vector3f(buf.readFloat(), buf.readFloat(), buf.readFloat()),
                            inX = buf.readFloat().toDouble(),
                            inValueOffset = Vector3f(buf.readFloat(), buf.readFloat(), buf.readFloat()),
                        )
                    })
                }
            },
        )

        @JvmStatic
        fun of(vararg keys: Pair<Float, Vector3fc>): CParticleColorCurve {
            require(keys.isNotEmpty()) { "color curve requires at least 1 key" }
            val count = keys.size.coerceAtMost(MAX_KEYS)
            val times = FloatArray(MAX_KEYS)
            val colors = FloatArray(MAX_KEYS * COLOR_COMPONENTS)
            var previousTime = Float.NEGATIVE_INFINITY
            for (i in 0 until count) {
                val (time, color) = keys[i]
                require(time.isFinite() && time in 0f..1f) {
                    "color curve time must be finite and in 0..1"
                }
                require(time >= previousTime) { "color curve keys must be sorted by time" }
                require(color.x().isFinite() && color.y().isFinite() && color.z().isFinite()) {
                    "color curve values must be finite"
                }
                times[i] = time
                val offset = i * COLOR_COMPONENTS
                colors[offset] = color.x()
                colors[offset + 1] = color.y()
                colors[offset + 2] = color.z()
                previousTime = time
            }
            return CParticleColorCurve(
                times,
                colors,
                count,
                CParticleCurveInterpolation.LINEAR,
                FloatArray(MAX_KEYS * HANDLE_COMPONENTS),
                FloatArray(MAX_KEYS * HANDLE_COMPONENTS),
            )
        }

        /**
         * 创建一条共享时间映射、各通道独立使用值控制柄的 RGB 三次贝塞尔曲线。
         *
         * 示例：独立的 RGB 偏移可以让曲线段先向绿色弯曲，再结束于蓝色。
         * 禁止：关键帧必须有序，每一段的 X 控制柄都必须保持单调。
         *
         * @param keys RGB 锚点以及相对出入控制柄
         * @return 保留给定贝塞尔数据的曲线
         * @throws IllegalArgumentException 如果关键帧或控制柄无法在 GPU 上安全求值
         */
        @JvmStatic
        fun bezier(vararg keys: CParticleBezierColorKeyframe): CParticleColorCurve {
            require(keys.size in 1..MAX_KEYS) { "Bezier color curve requires 1..$MAX_KEYS keys" }
            val times = FloatArray(MAX_KEYS)
            val colors = FloatArray(MAX_KEYS * COLOR_COMPONENTS)
            val outHandles = FloatArray(MAX_KEYS * HANDLE_COMPONENTS)
            val inHandles = FloatArray(MAX_KEYS * HANDLE_COMPONENTS)
            keys.forEachIndexed { index, key ->
                require(key.time.isFinite() && key.outX.isFinite() && key.inX.isFinite()) {
                    "Bezier color curve times and handles must be finite"
                }
                require(key.time in 0.0..1.0) { "Bezier color curve time must be in 0..1" }
                require(key.value.isFinite() && key.outValueOffset.isFinite() && key.inValueOffset.isFinite()) {
                    "Bezier color curve values and handles must be finite"
                }
                val time = key.time.toFloat()
                val outX = key.outX.toFloat()
                val inX = key.inX.toFloat()
                require(time.isFinite() && outX.isFinite() && inX.isFinite()) {
                    "Bezier color curve times and handles must fit finite GPU floats"
                }
                times[index] = time
                val colorOffset = index * COLOR_COMPONENTS
                colors[colorOffset] = key.value.x()
                colors[colorOffset + 1] = key.value.y()
                colors[colorOffset + 2] = key.value.z()
                val handleOffset = index * HANDLE_COMPONENTS
                outHandles[handleOffset] = outX
                outHandles[handleOffset + 1] = key.outValueOffset.x()
                outHandles[handleOffset + 2] = key.outValueOffset.y()
                outHandles[handleOffset + 3] = key.outValueOffset.z()
                inHandles[handleOffset] = inX
                inHandles[handleOffset + 1] = key.inValueOffset.x()
                inHandles[handleOffset + 2] = key.inValueOffset.y()
                inHandles[handleOffset + 3] = key.inValueOffset.z()
                if (index > 0) {
                    CParticleBezierMath.requireMonotonicSegment(
                        times[index - 1],
                        outHandles[handleOffset - HANDLE_COMPONENTS],
                        times[index],
                        inHandles[handleOffset],
                    )
                }
            }
            return CParticleColorCurve(
                times,
                colors,
                keys.size,
                CParticleCurveInterpolation.CUBIC_BEZIER,
                outHandles,
                inHandles,
            )
        }

        @JvmStatic
        fun linear(from: Vector3fc, to: Vector3fc): CParticleColorCurve =
            of(0f to from, 1f to to)

        private fun lerp(from: Float, to: Float, progress: Float): Float =
            from + (to - from) * progress

    }
}
