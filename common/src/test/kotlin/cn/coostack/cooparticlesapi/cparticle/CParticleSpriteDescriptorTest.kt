package cn.coostack.cooparticlesapi.cparticle

import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CParticleSpriteDescriptorTest {
    @Test
    fun `explicit sprite stays ahead of effect and ids survive invalidation`() {
        val sprite = ResourceLocation.fromNamespaceAndPath("test", "sprite")
        val effect = ResourceLocation.fromNamespaceAndPath("test", "effect")
        val effectId = CParticleSprites.animationId(null, effect)
        val spriteId = CParticleSprites.animationId(sprite, effect)

        assertEquals(spriteId, CParticleSprites.animationId(sprite, null))
        assertNotEquals(effectId, spriteId)

        CParticleSprites.clearCache()
        assertEquals(effectId, CParticleSprites.animationId(null, effect))
        assertEquals(spriteId, CParticleSprites.animationId(sprite, effect))
    }

    @Test
    fun `explicit default sprite remains a fixed sprite descriptor`() {
        val explicitSpriteId = CParticleSprites.animationId(CParticleSprites.DEFAULT, CParticleSprites.DEFAULT)
        val defaultEffectId = CParticleSprites.animationId(null, CParticleSprites.DEFAULT)

        assertNotEquals(defaultEffectId, explicitSpriteId)
        assertEquals(explicitSpriteId, CParticleSprites.animationId(CParticleSprites.DEFAULT, null))
        assertEquals(explicitSpriteId, CParticleSprites.animationId(null, null))
    }

    @Test
    fun `external texture providers keep a stable reloadable descriptor`() {
        val id = ResourceLocation.fromNamespaceAndPath("test", "block_state")
        val first = CParticleSprites.registerExternalAnimation(id) {
            listOf(CParticleSprites.UvRect(0f, 0f, 0.5f, 0.5f))
        }
        val second = CParticleSprites.registerExternalAnimation(id) {
            listOf(CParticleSprites.UvRect(0.5f, 0.5f, 1f, 1f))
        }

        assertEquals(first, second)
    }
}
