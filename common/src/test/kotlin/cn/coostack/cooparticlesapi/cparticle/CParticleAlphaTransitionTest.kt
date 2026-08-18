package cn.coostack.cooparticlesapi.cparticle

import kotlin.test.Test
import kotlin.test.assertEquals

class CParticleAlphaTransitionTest {
    @Test
    fun `alpha transition starts at zero on its first rendered tick`() {
        val system = CParticleSystem(
            "alpha-transition-test",
            1,
            CParticleRenderLayer.TRANSLUCENT,
            CParticleSystemMode.SCRIPTED,
        )

        fun playTransition() {
            system.playAlphaTransition(
                durationTicks = 10f,
                alphaCurve = CParticleCurve.linear(0f, 1f),
            )
        }

        playTransition()
        system.tick()
        assertEquals(0f, system.alphaTransition!!.progressAt(system.tickCount.toFloat()))
        assertEquals(
            0.05f,
            system.alphaTransition!!.progressAt(system.tickCount + 0.5f)!!,
            absoluteTolerance = 0.000001f,
        )

        playTransition()
        system.tick()
        assertEquals(
            0.1f,
            system.alphaTransition!!.progressAt(system.tickCount.toFloat())!!,
            absoluteTolerance = 0.000001f,
        )

        repeat(9) {
            playTransition()
            system.tick()
        }
        assertEquals(1f, system.alphaTransition!!.progressAt(system.tickCount.toFloat()))
    }
}
