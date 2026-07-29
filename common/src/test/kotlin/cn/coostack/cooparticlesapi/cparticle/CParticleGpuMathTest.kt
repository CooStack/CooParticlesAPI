package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.utils.Math3DUtil
import org.joml.Vector3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CParticleGpuMathTest {
    @Test
    fun `lifecycle frame selection preserves the old 4096 quantization`() {
        val ages = listOf(-10, 0, 1, 2, 7, 19, 20, 21, Int.MAX_VALUE)
        val lifetimes = listOf(-1, 0, 1, 3, 20, 97)
        val frameCounts = listOf(1, 2, 3, 7, 32)

        for (age in ages) for (lifetime in lifetimes) for (frameCount in frameCounts) {
            val safeLifetime = lifetime.coerceAtLeast(1).toLong()
            val safeAge = age.toLong().coerceIn(0L, safeLifetime)
            val frameAge = safeAge * 4096L / safeLifetime
            val expected = if (frameCount <= 1) 0 else (frameAge * (frameCount - 1) / 4096L).toInt()
            assertEquals(expected, CParticleGpuMath.lifecycleFrameIndex(age, lifetime, frameCount))
        }
    }

    @Test
    fun `seed packing keeps every int bit and random frames are reproducible`() {
        val seeds = intArrayOf(Int.MIN_VALUE, -1, 0, 1, 0x12345678, Int.MAX_VALUE)
        for (seed in seeds) {
            assertEquals(
                seed,
                CParticleGpuMath.joinSeed(CParticleGpuMath.seedLow(seed), CParticleGpuMath.seedHigh(seed)),
            )
            assertEquals(
                CParticleGpuMath.randomFrameIndex(seed, 42, 17),
                CParticleGpuMath.randomFrameIndex(seed, 42, 17),
            )
        }

        val first = CParticleGpuMath.nextAutomaticSeed()
        val second = CParticleGpuMath.nextAutomaticSeed()
        assertNotEquals(first, second)
    }

    @Test
    fun `direction angles match Math3DUtil including the zero fallback`() {
        val directions = listOf(
            Vector3f(),
            Vector3f(1f, 0f, 0f),
            Vector3f(0f, 1f, 0f),
            Vector3f(-2f, 3f, 4f),
        )
        for (direction in directions) {
            val expected = Math3DUtil.calculateEulerAnglesToPoint(direction)
            val actual = CParticleGpuMath.directionAngles(direction)
            assertEquals(expected.first, actual.x, 1e-6f)
            assertEquals(expected.second, actual.y, 1e-6f)
        }
    }

    @Test
    fun `angle accumulation uses pitch yaw roll component order`() {
        val result = CParticleGpuMath.accumulateAngles(
            Vector3f(1f, 2f, 3f),
            Vector3f(0.1f, 0.2f, 0.3f),
            5f,
        )
        assertEquals(1.5f, result.x, 1e-6f)
        assertEquals(3f, result.y, 1e-6f)
        assertEquals(4.5f, result.z, 1e-6f)
    }
}
