package cn.coostack.cooparticlesapi.cparticle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CParticleTextureFlagsTest {
    @Test
    fun `all assigned flags round trip through float without overlap`() {
        val featureBits = listOf(
            CParticleInstanceFlags.ALIVE,
            CParticleInstanceFlags.RANDOM_AGE,
            CParticleInstanceFlags.ROTATION_DIRECTION,
            CParticleInstanceFlags.RANDOM_QUARTER_UV,
            CParticleInstanceFlags.MASK_RANDOM_QUARTER_UV,
            CParticleInstanceFlags.BLOCK_COLLISION,
            CParticleInstanceFlags.NEWBORN,
        )
        for (index in featureBits.indices) for (other in index + 1 until featureBits.size) {
            assertEquals(0, featureBits[index] and featureBits[other])
        }

        val flags = CParticleInstanceFlags.packWithMask(
            alive = true,
            cameraMode = 2,
            blockLight = 5,
            skyLight = 13,
            randomAge = true,
            rotationDirection = true,
            randomQuarterUv = true,
            randomMaskQuarterUv = true,
            blockCollision = true,
        )

        assertEquals(flags, flags.toFloat().toInt())
        assertEquals(2, CParticleInstanceFlags.cameraMode(flags))
        assertEquals(5, CParticleInstanceFlags.blockLight(flags))
        assertEquals(13, CParticleInstanceFlags.skyLight(flags))
        assertTrue(flags and CParticleInstanceFlags.RANDOM_QUARTER_UV != 0)
        assertTrue(flags and CParticleInstanceFlags.MASK_RANDOM_QUARTER_UV != 0)
        assertTrue(flags and CParticleInstanceFlags.BLOCK_COLLISION != 0)
        assertTrue(CParticleInstanceFlags.MAX_PACKED_VALUE < CParticleInstanceFlags.FLOAT_EXACT_INTEGER_LIMIT)
    }

    @Test
    fun `random quarter crop is deterministic continuous and one quarter wide`() {
        val base = CParticleUv(0.2f, 0.3f, 0.8f, 0.9f)
        val first = CParticleGpuMath.randomQuarterUv(base, 0x12345678)
        val second = CParticleGpuMath.randomQuarterUv(base, 0x12345678)
        val offsets = CParticleGpuMath.randomQuarterOffsets(0x12345678)

        assertEquals(first, second)
        assertTrue(offsets.x >= 0f && offsets.x < 3f)
        assertTrue(offsets.y >= 0f && offsets.y < 3f)
        assertEquals((base.u1 - base.u0) * 0.25f, first.u0 - first.u1, 1e-6f)
        assertEquals((base.v1 - base.v0) * 0.25f, first.v1 - first.v0, 1e-6f)
    }
}
