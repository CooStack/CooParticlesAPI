package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.network.particle.emitters.command.curve.FloatCurve
import cn.coostack.cooparticlesapi.network.particle.emitters.command.curve.BezierFloatKeyframe
import cn.coostack.cooparticlesapi.network.particle.emitters.command.curve.BezierKeyframeFloatCurve
import net.minecraft.network.PacketByteBuf

class CParticleCurve private constructor(
    packed: FloatArray,
    val keyCount: Int,
    val interpolation: CParticleCurveInterpolation,
    packedHandles: FloatArray,
) {
    internal val packedData: FloatArray = packed
    internal val packedHandleData: FloatArray = packedHandles

    val packed: FloatArray
        get() = packedData.copyOf()
    val packedHandles: FloatArray
        get() = packedHandleData.copyOf()

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

    companion object {
        const val MAX_KEYS = 8
        private const val EXTENDED_CODEC_MARKER = 0

        @JvmField
        val STREAM_CODEC: ForgeStreamCodec<CParticleCurve> = ForgeStreamCodec.of(
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

        @JvmStatic
        fun linear(from: Float, to: Float): CParticleCurve = of(0f to from, 1f to to)

        @JvmStatic
        fun fadeInOut(peak: Float = 1f, fadeIn: Float = 0.15f, fadeOut: Float = 0.75f): CParticleCurve =
            of(0f to 0f, fadeIn to peak, fadeOut to peak, 1f to 0f)

        @JvmStatic
        fun fromFloatCurve(curve: FloatCurve): CParticleCurve {
            if (curve is BezierKeyframeFloatCurve) {
                try {
                    return bezier(*curve.frames().toTypedArray())
                } catch (_: IllegalArgumentException) {
                }
            }
            return sampledLinear(curve)
        }

        private fun sampledLinear(curve: FloatCurve): CParticleCurve =
            of(*Array(MAX_KEYS) { index ->
                val time = index / (MAX_KEYS - 1f)
                time to curve.sample(time.toDouble()).toFloat()
            })
    }
}
