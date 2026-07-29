package cn.coostack.cooparticlesapi.cparticle

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CParticleCapabilitiesTest {
    @Test
    fun `opengl 32 with arb instanced arrays supports rendering`() {
        assertTrue(CParticleCapabilities.supportsInstancing(true, false, true))
        assertFalse(CParticleCapabilities.supportsInstancing(true, false, false))
    }

    @Test
    fun `detection outside the render thread remains retryable`() {
        val detected = CParticleCapabilities::class.java.getDeclaredField("detected").apply {
            isAccessible = true
        }
        val previous = detected.getBoolean(CParticleCapabilities)

        try {
            detected.setBoolean(CParticleCapabilities, false)
            CParticleCapabilities.detect()
            assertFalse(detected.getBoolean(CParticleCapabilities))
        } finally {
            detected.setBoolean(CParticleCapabilities, previous)
        }
    }
}
