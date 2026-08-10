package cn.coostack.cooparticlesapi.renderer.runtime

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RenderEntityPlatformRefactorGuardTest {
    @Test
    fun `renderer no longer exposes feature description`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/RenderEntityRenderer.kt"
        )

        assertTrue("val pipeline: CooRenderPipeline<T>" in source)
        assertFalse("BuiltinRenderEffectDescriptors" in source)
        assertFalse("describeFeatures" in source)
    }

    @Test
    fun `instance delegates builtin provider expansion to descriptor layer instead of hardcoded managers`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/RenderEntityInstance.kt"
        )

        assertTrue("BuiltinRenderEffectDescriptors.collectEntity(entity, context, collector)" in source)
        assertTrue("CooPipelineRuntimeEffect.descriptor" in source)
        assertFalse("CooPipelines.MASK_BLOOM.id" in source)
        assertFalse("BuiltinRenderEffectDescriptors.maskBloom" in source)
        assertFalse("ClientScreenGlowManager" in source)
        assertFalse("ClientPersistentBloomManager" in source)
        assertFalse("ClientWorldLightManager.submitFrameEffects(" in source)
    }

    @Test
    fun `render backend capability surface does not advertise unimplemented early world hook`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/backend/RenderBackendCapability.kt"
        )

        assertFalse("EARLY_WORLD_HOOK" in source)
    }

    @Test
    fun `unused frame effect stack wrapper is removed`() {
        assertFalse(
            projectFile("common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/effects/FrameEffectStack.kt").toFile().exists()
        )
    }

    private fun readProjectFile(relativePath: String): String {
        return Files.readString(projectFile(relativePath))
    }

    private fun projectFile(relativePath: String): Path {
        val repoRoot = findRepoRoot()
        return repoRoot.resolve(relativePath)
    }

    private fun findRepoRoot(): Path {
        var cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (cursor.parent != null) {
            if (Files.exists(cursor.resolve("settings.gradle"))) {
                return cursor
            }
            cursor = cursor.parent
        }
        error("Could not locate repository root from ${System.getProperty("user.dir")}")
    }
}
