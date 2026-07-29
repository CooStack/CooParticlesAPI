package cn.coostack.cooparticlesapi.cparticle

import org.joml.Vector3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CParticleColorCurveTest {
    @Test
    fun `samples lifetime color keys linearly`() {
        val curve = CParticleColorCurve.of(
            0f to Vector3f(1f, 0f, 0f),
            0.5f to Vector3f(0f, 1f, 0f),
            1f to Vector3f(0f, 0f, 1f),
        )
        val sampled = curve.sample(0.75f, Vector3f())

        assertEquals(0f, sampled.x)
        assertEquals(0.5f, sampled.y)
        assertEquals(0.5f, sampled.z)
    }

    @Test
    fun `clamps samples and validates key order`() {
        val curve = CParticleColorCurve.linear(
            Vector3f(0.25f, 0.5f, 0.75f),
            Vector3f(1f, 1f, 1f),
        )

        assertEquals(Vector3f(0.25f, 0.5f, 0.75f), curve.sample(-1f, Vector3f()))
        assertEquals(Vector3f(1f), curve.sample(2f, Vector3f()))
        assertFailsWith<IllegalArgumentException> {
            CParticleColorCurve.of(
                1f to Vector3f(1f),
                0f to Vector3f(0f),
            )
        }
    }
}
