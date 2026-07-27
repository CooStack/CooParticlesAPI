package cn.coostack.cooparticlesapi.cparticle

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import org.joml.Vector3f
import org.joml.Vector3fc

/**
 * Per-particle lifetime color multiplier sampled by the GPU.
 *
 * Each key is `(lifeT, rgbMultiplier)`, where `lifeT` is in `0..1`.
 * The multiplier preserves per-particle colors and texture tinting.
 */
class CParticleColorCurve private constructor(
    val packedTimes: FloatArray,
    val packedColors: FloatArray,
    val keyCount: Int,
) {
    internal fun sample(t: Float, destination: Vector3f): Vector3f {
        val sampleT = t.coerceIn(0f, 1f)
        if (sampleT <= packedTimes[0]) return colorAt(0, destination)
        for (i in 1 until keyCount) {
            if (sampleT <= packedTimes[i]) {
                val t0 = packedTimes[i - 1]
                val t1 = packedTimes[i]
                val progress = if (t1 > t0) (sampleT - t0) / (t1 - t0) else 0f
                val fromOffset = (i - 1) * COLOR_COMPONENTS
                val toOffset = i * COLOR_COMPONENTS
                return destination.set(
                    lerp(packedColors[fromOffset], packedColors[toOffset], progress),
                    lerp(packedColors[fromOffset + 1], packedColors[toOffset + 1], progress),
                    lerp(packedColors[fromOffset + 2], packedColors[toOffset + 2], progress),
                )
            }
        }
        return colorAt(keyCount - 1, destination)
    }

    private fun colorAt(index: Int, destination: Vector3f): Vector3f {
        val offset = index * COLOR_COMPONENTS
        return destination.set(
            packedColors[offset],
            packedColors[offset + 1],
            packedColors[offset + 2],
        )
    }

    companion object {
        const val MAX_KEYS = CParticleCurve.MAX_KEYS
        private const val COLOR_COMPONENTS = 3

        /**
         * 生命周期颜色乘数曲线的网络 codec。
         *
         * Example: `@CodecField var colorCurve = CParticleColorCurve.linear(from, to)`。
         * Forbidden: 解码端不会接受空曲线或超过 [MAX_KEYS] 的关键帧。
         */
        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, CParticleColorCurve> = StreamCodec.of(
            { buf, curve ->
                buf.writeByte(curve.keyCount)
                for (i in 0 until curve.keyCount) {
                    val colorOffset = i * COLOR_COMPONENTS
                    buf.writeFloat(curve.packedTimes[i])
                    buf.writeFloat(curve.packedColors[colorOffset])
                    buf.writeFloat(curve.packedColors[colorOffset + 1])
                    buf.writeFloat(curve.packedColors[colorOffset + 2])
                }
            },
            { buf ->
                val count = buf.readUnsignedByte().toInt()
                require(count in 1..MAX_KEYS) { "color curve key count must be in 1..$MAX_KEYS: $count" }
                of(*Array(count) {
                    buf.readFloat() to Vector3f(buf.readFloat(), buf.readFloat(), buf.readFloat())
                })
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
            return CParticleColorCurve(times, colors, count)
        }

        @JvmStatic
        fun linear(from: Vector3fc, to: Vector3fc): CParticleColorCurve =
            of(0f to from, 1f to to)

        private fun lerp(from: Float, to: Float, progress: Float): Float =
            from + (to - from) * progress
    }
}
