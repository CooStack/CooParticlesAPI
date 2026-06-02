package cn.coostack.cooparticlesapi.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClientCameraUtilFrequencyTest {
    @Test
    fun `new shake packet replaces active shake strength instead of extending the stronger one`() {
        ClientCameraShakeMath.startShake(40, 12.0, 240.0)

        val state = assertNotNull(ClientCameraShakeMath.startShake(8, 1.5, 2.0))

        assertEquals(8, state.tick)
        assertEquals(8, state.duration)
        assertEquals(1.5, state.amplitude, 1.0E-9)
        assertEquals(2.0, state.frequency, 1.0E-9)
        assertEquals(1.5 / 8.0, state.amplitudeStep, 1.0E-9)
    }

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
