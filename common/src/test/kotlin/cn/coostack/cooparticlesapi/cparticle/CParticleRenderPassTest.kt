package cn.coostack.cooparticlesapi.cparticle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CParticleRenderPassTest {
    @Test
    fun `opaque and translucent passes do not overlap`() {
        assertTrue(CParticleRenderPass.OPAQUE.accepts(CParticleRenderLayer.OPAQUE))
        assertFalse(CParticleRenderPass.OPAQUE.accepts(CParticleRenderLayer.TRANSLUCENT))
        assertFalse(CParticleRenderPass.TRANSLUCENT.accepts(CParticleRenderLayer.OPAQUE))
        assertTrue(CParticleRenderPass.TRANSLUCENT.accepts(CParticleRenderLayer.ADDITION_BLEND))
    }

    @Test
    fun `predicate coverage selects one render pass`() {
        assertEquals(CParticleRenderPass.ALL, CParticleRenderPass.fromCoverage(true, true))
        assertEquals(CParticleRenderPass.OPAQUE, CParticleRenderPass.fromCoverage(true, false))
        assertEquals(CParticleRenderPass.TRANSLUCENT, CParticleRenderPass.fromCoverage(false, true))
        assertEquals(CParticleRenderPass.NONE, CParticleRenderPass.fromCoverage(false, false))
    }
}
