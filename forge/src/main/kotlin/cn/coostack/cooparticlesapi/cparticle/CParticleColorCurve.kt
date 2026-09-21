package cn.coostack.cooparticlesapi.cparticle

import net.minecraft.network.PacketByteBuf

import org.joml.Vector3f
import org.joml.Vector3fc

class CParticleColorCurve private constructor(
    packedTimes: FloatArray,
    packedColors: FloatArray,
    val keyCount: Int,
    val interpolation: CParticleCurveInterpolation,
    packedOutHandles: FloatArray,
    packedInHandles: FloatArray,
) {
    internal val packedTimeData: FloatArray = packedTimes
    internal val packedColorData: FloatArray = packedColors
    internal val packedOutHandleData: FloatArray = packedOutHandles
    internal val packedInHandleData: FloatArray = packedInHandles

    val packedTimes: FloatArray
        get() = packedTimeData.copyOf()
    val packedColors: FloatArray
        get() = packedColorData.copyOf()
    val packedOutHandles: FloatArray
        get() = packedOutHandleData.copyOf()
    val packedInHandles: FloatArray
        get() = packedInHandleData.copyOf()

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

    companion object {
        const val MAX_KEYS = CParticleCurve.MAX_KEYS
        private const val COLOR_COMPONENTS = 3
        internal const val HANDLE_COMPONENTS = 4
        private const val EXTENDED_CODEC_MARKER = 0

        @JvmField
        val STREAM_CODEC: ForgeStreamCodec<CParticleColorCurve> = ForgeStreamCodec.of(
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
