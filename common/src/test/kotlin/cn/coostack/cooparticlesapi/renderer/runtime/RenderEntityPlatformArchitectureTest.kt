package cn.coostack.cooparticlesapi.renderer.runtime

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RenderEntityPlatformArchitectureTest {
    @Test
    fun `canonical render platform files exist`() {
        assertTrue(projectFile("common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/backend/RenderFrameStage.kt").exists())
        assertTrue(projectFile("common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/backend/RenderSceneTargets.kt").exists())
        assertTrue(projectFile("common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/backend/RenderSceneResources.kt").exists())
        assertTrue(projectFile("common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/client/ClientRenderSceneResourcesResolver.kt").exists())
        assertTrue(projectFile("common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/client/ClientRenderTargetResolver.kt").exists())
        assertTrue(projectFile("common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/effects/RenderEffectDescriptor.kt").exists())
        assertTrue(projectFile("common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/effects/RenderEffectGraph.kt").exists())
        assertTrue(projectFile("common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/effects/RenderEffectRegistry.kt").exists())
        assertTrue(projectFile("common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/compute/ComputeDispatchRenderer.kt").exists())
        assertTrue(projectFile("common/src/main/kotlin/cn/coostack/cooparticlesapi/display/CooRenderTypeDescriptor.kt").exists())
        assertTrue(projectFile("common/src/main/kotlin/cn/coostack/cooparticlesapi/display/CooLayeredRenderType.kt").exists())
        assertTrue(projectFile("common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/AdvancedShaderProgramBuilder.kt").exists())
        assertTrue(projectFile("common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/ShaderProgramRegistry.kt").exists())
        assertTrue(projectFile("common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/buffer/ShaderBufferLayout.kt").exists())
        assertTrue(projectFile("common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/buffer/ShaderBufferRegistry.kt").exists())
    }

    @Test
    fun `pipeline manager drives stage and scene resource aware frame contexts`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/client/ClientRenderPipelineManager.kt"
        )

        assertTrue("RenderFrameStage" in source)
        assertTrue("ClientRenderSceneResourcesResolver.resolveCurrentResources()" in source)
        assertTrue("sceneResources = sceneResources" in source)
        assertTrue("activeBackend.runStage(stage, context, backendHooks)" in source)
    }

    @Test
    fun `renderer interface exposes descriptor api without frame effect compatibility bridge`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/RenderEntityRenderer.kt"
        )

        assertTrue("fun describeFeatures(entity: T): RenderEntityFeatureSet" in source)
        assertTrue("interface WorldPassRenderEntityRenderer" in source)
        assertTrue("interface FramePostRenderEntityRenderer" in source)
        assertTrue("interface RenderEntityReleaseHook" in source)
        assertFalse("collectFrameEffects(" in source)
        assertFalse("FrameEffectInput" in source)
        assertFalse("asFrameEffectCollector(" in source)
        assertTrue("fun renderLocal(input: LocalRenderInput<T>)" in source)
        assertTrue("fun collectRenderContributions(" in source)
        assertTrue("fun release(instance: RenderEntityInstance<T>)" in source)
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
