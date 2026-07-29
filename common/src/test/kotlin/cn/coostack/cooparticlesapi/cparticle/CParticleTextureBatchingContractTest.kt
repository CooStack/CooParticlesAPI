package cn.coostack.cooparticlesapi.cparticle

import java.nio.file.Files
import java.nio.file.Path
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class CParticleTextureBatchingContractTest {
    @Test
    fun `system key splits bindings while preserving same binding batching`() {
        val particles = binding("particles")
        val blocks = binding("blocks")
        val first = CParticleSystemKey(
            "shared",
            CParticleSystemMode.SIMULATED,
            CParticleRenderLayer.TRANSLUCENT,
            particles,
        )

        assertEquals(first, first.copy())
        assertNotEquals(first, first.copy(textureBindingKey = blocks))
        assertNotEquals(first, first.copy(mode = CParticleSystemMode.SCRIPTED))
        assertNotEquals(first, first.copy(layer = CParticleRenderLayer.OPAQUE))
    }

    @Test
    fun `manager splits systems by optional mask binding`() {
        val name = "mask-batching-${System.nanoTime()}"
        val particles = binding("particles")
        val blocks = binding("blocks")
        val withoutMask = CParticleSystemManager.getOrCreateSystem(
            name,
            1,
            CParticleRenderLayer.TRANSLUCENT,
            CParticleSystemMode.SIMULATED,
            particles,
            false,
            null,
        )
        val withMask = CParticleSystemManager.getOrCreateSystem(
            name,
            1,
            CParticleRenderLayer.TRANSLUCENT,
            CParticleSystemMode.SIMULATED,
            particles,
            false,
            blocks,
        )

        try {
            assertNotSame(withoutMask, withMask)
        } finally {
            CParticleSystemManager.removeSystem(
                name,
                CParticleSystemMode.SIMULATED,
                CParticleRenderLayer.TRANSLUCENT,
                particles,
                null,
            )
            CParticleSystemManager.removeSystem(
                name,
                CParticleSystemMode.SIMULATED,
                CParticleRenderLayer.TRANSLUCENT,
                particles,
                blocks,
            )
        }
    }

    @Test
    fun `renderer binds once per sorted binding batch and uses a generic sampler`() {
        val renderer = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/render/CParticleRenderer.kt"
        )
        val fragment = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/core/fragment/cparticle.fsh"
        )
        val drawLoop = renderer.substringAfter("for (layer in drawLayers)")
            .substringBefore("} finally")

        assertTrue("sortedWith(compareBy(CParticleSystem::textureBindingKey)" in drawLoop)
        assertTrue("thenBy(CParticleSystem::maskTextureBindingKey)" in drawLoop)
        assertTrue("system.textureBindingKey != boundMainTexture" in drawLoop)
        assertTrue("system.maskTextureBindingKey" in drawLoop)
        assertTrue("CParticleTextureResolver.textureId(system.textureBindingKey)" in drawLoop)
        assertTrue(drawLoop.indexOf("RenderSystem.bindTexture") < drawLoop.indexOf("system.glBuffer.draw"))
        assertTrue("uMainTexture" in fragment)
        assertTrue("uMaskTexture" in fragment)
        assertTrue("uAtlas" !in fragment)
    }

    @Test
    fun `block item reload and dynamic contracts use the shared resolver`() {
        val resolver = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/CParticleTextureResolver.kt"
        )
        val manager = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/CParticleSystemManager.kt"
        )
        val store = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/storage/CParticleStore.kt"
        )

        assertTrue("CParticleBlockAppearanceResolver.resolve" in resolver)
        assertTrue("itemRenderer.getModel" in resolver)
        assertTrue("model.particleIcon" in resolver)
        assertTrue("cooparticlesapi\u0024getItemColors" in resolver)
        assertTrue("modelSeed" in resolver)
        assertTrue("resolveItemSprite(stack, source.modelSeed)" in resolver)
        assertTrue("sprite.atlasLocation()" in resolver)
        assertTrue("itemSpriteCache" !in resolver)
        assertTrue("CParticleTextureResolver.invalidate()" in readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/CParticleSprites.kt"
        ))
        assertTrue("defaultSystem(layer, resolved.base.bindingKey, resolved.mask?.bindingKey)" in manager)
        assertTrue("source.textureRevision != state.textureRevisions[slot]" in store)
        assertTrue("kill(slot)" in store.substringAfter("resolved.base.bindingKey != expectedBindingKey"))
    }

    @Test
    fun `particle and arbitrary stitched atlases are resolved through the texture manager`() {
        val resolver = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/CParticleTextureResolver.kt"
        )
        val atlasLookup = resolver.substringAfter("private fun textureAtlas(")
            .substringBefore("private fun uv(")

        assertTrue("textureManager.getTexture(atlas) as? TextureAtlas" in atlasLookup)
        assertTrue("textureAtlas(bindingKey.location)?.id" in resolver)
        assertTrue("textureAtlas(atlas)?.getSprite(sprite)" in resolver)
    }

    private fun binding(path: String) = CParticleTextureBindingKey(
        CParticleTextureBindingKind.TEXTURE,
        ResourceLocation.fromNamespaceAndPath("batch_test", path),
    )

    private fun readProjectFile(relativePath: String): String =
        Files.readString(findRepoRoot().resolve(relativePath))

    private fun findRepoRoot(): Path {
        var cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (cursor.parent != null) {
            if (Files.exists(cursor.resolve("settings.gradle"))) return cursor
            cursor = cursor.parent
        }
        error("Could not locate repository root from ${System.getProperty("user.dir")}")
    }
}
