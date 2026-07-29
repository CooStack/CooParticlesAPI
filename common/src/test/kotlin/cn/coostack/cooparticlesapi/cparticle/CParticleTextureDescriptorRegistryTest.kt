package cn.coostack.cooparticlesapi.cparticle

import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CParticleTextureDescriptorRegistryTest {
    @Test
    fun `descriptor id survives invalidation while frames are re-resolved`() {
        val key = Any()
        val binding = textureBinding("stable")
        var frames = listOf(CParticleUv(0f, 0f, 0.25f, 0.25f))
        val id = CParticleTextureDescriptors.register(key, binding) { frames }

        assertEquals(frames.first(), CParticleTextureDescriptors.firstFrame(id))

        val reloaded = CParticleUv(0.5f, 0.5f, 1f, 1f)
        frames = listOf(reloaded)
        CParticleTextureDescriptors.invalidate()

        assertEquals(id, CParticleTextureDescriptors.register(key, binding) { error("must reuse the id") })
        assertEquals(reloaded, CParticleTextureDescriptors.firstFrame(id))
    }

    @Test
    fun `one descriptor key cannot cross texture bindings`() {
        val key = Any()
        CParticleTextureDescriptors.register(key, textureBinding("first")) { listOf(CParticleUv.FULL) }

        assertFailsWith<IllegalStateException> {
            CParticleTextureDescriptors.register(key, textureBinding("second")) { listOf(CParticleUv.FULL) }
        }
    }

    @Test
    fun `descriptor ids are restricted to the declared 24 bit range`() {
        assertEquals(
            CParticleTextureDescriptors.MAX_DESCRIPTOR_ID,
            CParticleTextureDescriptors.requireValidDescriptorId(CParticleTextureDescriptors.MAX_DESCRIPTOR_ID),
        )
        assertFailsWith<IllegalArgumentException> {
            CParticleTextureDescriptors.requireValidDescriptorId(-1)
        }
        assertFailsWith<IllegalArgumentException> {
            CParticleTextureDescriptors.requireValidDescriptorId(CParticleTextureDescriptors.MAX_DESCRIPTOR_ID + 1)
        }
    }

    @Test
    fun `registered descriptor must match system binding`() {
        val expected = textureBinding("expected")
        val descriptorId = CParticleTextureDescriptors.register(Any(), expected) {
            listOf(CParticleUv.FULL)
        }

        assertEquals(descriptorId, CParticleTextureDescriptors.requireBinding(descriptorId, expected))
        assertFailsWith<IllegalArgumentException> {
            CParticleTextureDescriptors.requireBinding(descriptorId, textureBinding("other"))
        }
        assertFailsWith<IllegalArgumentException> {
            CParticleTextureDescriptors.requireBinding(descriptorId + 10_000, expected)
        }
    }

    private fun textureBinding(path: String) = CParticleTextureBindingKey(
        CParticleTextureBindingKind.TEXTURE,
        ResourceLocation.fromNamespaceAndPath("descriptor_test", path),
    )
}
