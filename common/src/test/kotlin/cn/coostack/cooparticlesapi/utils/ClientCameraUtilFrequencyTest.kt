package cn.coostack.cooparticlesapi.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClientCameraUtilFrequencyTest {
    @Test
    fun `phase step scales with frequency without saturating high values`() {
        assertEquals(1.0, ClientCameraShakeMath.shakePhaseStep(2.0), 1.0E-9)
        assertEquals(120.0, ClientCameraShakeMath.shakePhaseStep(240.0), 1.0E-9)
        assertTrue(ClientCameraShakeMath.shakePhaseStep(240.0) > ClientCameraShakeMath.shakePhaseStep(2.0))
    }

    @Test
    fun `noise sampler is deterministic for the same phase and seed`() {
        val sample = ClientCameraShakeMath.sampleShakeNoise(3.25, 1.37)

        assertEquals(sample, ClientCameraShakeMath.sampleShakeNoise(3.25, 1.37), 1.0E-9)
    }

    @Test
    fun `follow factor only ramps after legacy one-target-per-tick threshold`() {
        assertEquals(0.45, ClientCameraShakeMath.shakeFollowFactor(2.0), 1.0E-9)
        assertTrue(ClientCameraShakeMath.shakeFollowFactor(240.0) > 0.9)
    }
}
