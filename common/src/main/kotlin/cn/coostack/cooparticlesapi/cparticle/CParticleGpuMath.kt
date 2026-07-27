package cn.coostack.cooparticlesapi.cparticle

import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector3fc
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.atan2
import kotlin.math.sqrt

/** CPU 端对照实现。公式与 cparticle.vsh 保持一致。 */
object CParticleGpuMath {
    const val FRAME_PROGRESS_RESOLUTION = 4096
    private const val SEED_LOW_MASK = 0xFFFF
    private const val RANDOM_TICK_MIX = 0x9E3779B9u
    private const val QUARTER_U_MIX = 0xA511E9B3u
    private const val QUARTER_V_MIX = 0x63D83595u
    private const val HASH_FLOAT_MASK = 0x00FF_FFFFu
    private const val HASH_FLOAT_DIVISOR = 16_777_216f
    private val automaticSeed = AtomicInteger(System.nanoTime().toInt())

    @JvmStatic
    fun lifecycleFrameIndex(age: Int, lifetime: Int, frameCount: Int): Int {
        if (frameCount <= 1) return 0
        val safeLifetime = lifetime.coerceAtLeast(1).toLong()
        val safeAge = age.toLong().coerceIn(0L, safeLifetime)
        val frameAge = safeAge * FRAME_PROGRESS_RESOLUTION / safeLifetime
        return (frameAge * (frameCount - 1) / FRAME_PROGRESS_RESOLUTION).toInt()
    }

    @JvmStatic
    fun randomFrameIndex(seed: Int, tick: Int, frameCount: Int): Int {
        if (frameCount <= 1) return 0
        val mixed = hash32(seed.toUInt() xor tick.toUInt() * RANDOM_TICK_MIX)
        return (mixed % frameCount.toUInt()).toInt()
    }

    /**
     * 返回 FallingDust 风格 1/4 裁剪的连续偏移，两个分量都在 `[0, 3)`。
     *
     * Example: 相同 seed 在 CPU 测试和 shader 中得到相同偏移。
     * Forbidden: 不要把偏移取整，否则会改变现有 FallingDust 的连续随机行为。
     *
     * @param seed 粒子实例随机种子
     * @return x 为 U 偏移，y 为 V 偏移
     */
    @JvmStatic
    fun randomQuarterOffsets(seed: Int): Vector2f {
        return Vector2f(
            hashUnitFloat(seed.toUInt() xor QUARTER_U_MIX) * 3f,
            hashUnitFloat(seed.toUInt() xor QUARTER_V_MIX) * 3f,
        )
    }

    /**
     * 用 shader 的公式把基础 sprite UV 裁成随机 1/4 区域。
     *
     * U 方向保留 `ControlableFallingDustParticle` 的反向取值，V 方向保持正常顺序。
     * Example: 30 万粒子可共享 [uv] 的一个 descriptor，只让 seed 改变裁剪位置。
     * Forbidden: 不要为每个结果注册新的 descriptor。
     *
     * @param uv 完整 sprite UV
     * @param seed 粒子实例随机种子
     * @return 与 vertex shader 一致的裁剪 UV
     */
    @JvmStatic
    fun randomQuarterUv(uv: CParticleUv, seed: Int): CParticleUv {
        val offset = randomQuarterOffsets(seed)
        val uSpan = uv.u1 - uv.u0
        val vSpan = uv.v1 - uv.v0
        return CParticleUv(
            uv.u0 + uSpan * (offset.x + 1f) * 0.25f,
            uv.v0 + vSpan * offset.y * 0.25f,
            uv.u0 + uSpan * offset.x * 0.25f,
            uv.v0 + vSpan * (offset.y + 1f) * 0.25f,
        )
    }

    @JvmStatic
    fun nextAutomaticSeed(): Int = automaticSeed.getAndAdd(0x9E3779B9u.toInt())

    @JvmStatic
    fun seedLow(seed: Int): Int = seed and SEED_LOW_MASK

    @JvmStatic
    fun seedHigh(seed: Int): Int = seed ushr 16

    @JvmStatic
    fun joinSeed(low: Int, high: Int): Int =
        (low and SEED_LOW_MASK) or ((high and SEED_LOW_MASK) shl 16)

    /** 返回 x=pitch、y=yaw。零向量与 Math3DUtil 一样回退到 0。 */
    @JvmStatic
    fun directionAngles(direction: Vector3fc?): Vector2f {
        if (direction == null) return Vector2f()
        val x = direction.x()
        val y = direction.y()
        val z = direction.z()
        if (x == 0f && y == 0f && z == 0f) return Vector2f()
        return Vector2f(
            atan2(y, sqrt(x * x + z * z)),
            -atan2(z, x),
        )
    }

    /** base 和 velocity 都按 x=pitch、y=yaw、z=roll 排列。 */
    @JvmStatic
    fun accumulateAngles(
        base: Vector3fc,
        velocity: Vector3fc,
        elapsedTicks: Float,
        direction: Vector3fc? = null,
    ): Vector3f {
        val pointed = directionAngles(direction)
        return Vector3f(
            pointed.x + base.x() + velocity.x() * elapsedTicks,
            pointed.y + base.y() + velocity.y() * elapsedTicks,
            base.z() + velocity.z() * elapsedTicks,
        )
    }

    private fun hash32(value: UInt): UInt {
        var x = value
        x = (x xor (x shr 16)) * 0x7FEB352Du
        x = (x xor (x shr 15)) * 0x846CA68Bu
        return x xor (x shr 16)
    }

    private fun hashUnitFloat(value: UInt): Float =
        (hash32(value) and HASH_FLOAT_MASK).toFloat() / HASH_FLOAT_DIVISOR
}
