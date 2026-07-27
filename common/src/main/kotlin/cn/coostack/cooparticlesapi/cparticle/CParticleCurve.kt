package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.network.particle.emitters.command.curve.FloatCurve
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec

/**
 * 生命周期标量曲线 (GPU 求值).
 *
 * 最多 8 个关键帧, 打包为 16 float: [t0..t7, v0..v7], 顶点着色器按
 * lifeT = age/maxAge 线性插值 — 用于 alpha / size 随生命周期变化.
 */
class CParticleCurve private constructor(
    /** [t0..t7, v0..v7] */
    val packed: FloatArray,
    val keyCount: Int,
) {
    internal fun sample(t: Float): Float {
        val sampleT = t.coerceIn(0f, 1f)
        if (sampleT <= packed[0]) return packed[MAX_KEYS]
        for (i in 1 until keyCount) {
            if (sampleT <= packed[i]) {
                val t0 = packed[i - 1]
                val t1 = packed[i]
                val progress = if (t1 > t0) (sampleT - t0) / (t1 - t0) else 0f
                val from = packed[MAX_KEYS + i - 1]
                val to = packed[MAX_KEYS + i]
                return from + (to - from) * progress
            }
        }
        return packed[MAX_KEYS + keyCount - 1]
    }

    companion object {
        const val MAX_KEYS = 8

        /**
         * 生命周期标量曲线的网络 codec。
         *
         * Example: `@CodecField var alphaCurve = CParticleCurve.fadeInOut()`。
         * Forbidden: 解码端不会接受空曲线或超过 [MAX_KEYS] 的关键帧。
         */
        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, CParticleCurve> = StreamCodec.of(
            { buf, curve ->
                buf.writeByte(curve.keyCount)
                for (i in 0 until curve.keyCount) {
                    buf.writeFloat(curve.packed[i])
                    buf.writeFloat(curve.packed[MAX_KEYS + i])
                }
            },
            { buf ->
                val count = buf.readUnsignedByte().toInt()
                require(count in 1..MAX_KEYS) { "curve key count must be in 1..$MAX_KEYS: $count" }
                of(*Array(count) { buf.readFloat() to buf.readFloat() })
            },
        )

        /** 关键帧: (time 0..1, value) 需按 time 升序 */
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
            return CParticleCurve(packed, count)
        }

        /** 线性 from→to */
        @JvmStatic
        fun linear(from: Float, to: Float): CParticleCurve = of(0f to from, 1f to to)

        /** 淡入淡出: 0→peak→0 */
        @JvmStatic
        fun fadeInOut(peak: Float = 1f, fadeIn: Float = 0.15f, fadeOut: Float = 0.75f): CParticleCurve =
            of(0f to 0f, fadeIn to peak, fadeOut to peak, 1f to 0f)

        /**
         * 从现有 [FloatCurve] 采样 8 个点 (兼容 emitters 的曲线体系,
         * 包括 KeyframeFloatCurve / BezierKeyframeFloatCurve)
         */
        @JvmStatic
        fun fromFloatCurve(curve: FloatCurve): CParticleCurve {
            val packed = FloatArray(MAX_KEYS * 2)
            for (i in 0 until MAX_KEYS) {
                val t = i / (MAX_KEYS - 1f)
                packed[i] = t
                packed[MAX_KEYS + i] = curve.sample(t.toDouble()).toFloat()
            }
            return CParticleCurve(packed, MAX_KEYS)
        }
    }
}
