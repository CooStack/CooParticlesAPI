package cn.coostack.cooparticlesapi.renderer.backend

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RenderBackendCapabilityTest {
    @Test
    fun `vanilla safe backend advertises implemented scene and final frame capabilities`() {
        val backend = VanillaSafeRenderBackend

        assertTrue(backend.supports(RenderBackendCapability.FINAL_FRAME_POST))
        assertTrue(backend.supports(RenderBackendCapability.SCENE_COLOR_COPY))
        assertTrue(backend.supports(RenderBackendCapability.SCENE_DEPTH_READ))
        assertTrue(backend.supports(RenderBackendCapability.SAFE_WORLD_COMPOSITE))
    }

    @Test
    fun `iris safe backend exposes external scene depth when present`() {
        val backend = IrisSafeRenderBackend

        assertTrue(backend.supports(RenderBackendCapability.FINAL_FRAME_POST))
        assertTrue(backend.supports(RenderBackendCapability.SAFE_WORLD_COMPOSITE))
        assertTrue(backend.supports(RenderBackendCapability.SCENE_COLOR_COPY))
        assertTrue(backend.supports(RenderBackendCapability.SCENE_DEPTH_READ))
    }

    @Test
    fun `pipeline manager and level renderer mixin route through active backend without empty end frame mixin`() {
        val pipelineManagerSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/client/ClientRenderPipelineManager.kt"
        )
        val levelRendererMixinSource = readProjectFile(
            "common/src/main/java/cn/coostack/cooparticlesapi/mixin/LevelRendererMixin.java"
        )
        val mixinsSource = readProjectFile("common/src/main/resources/cooparticlesapi.mixins.json")

        assertTrue("var activeBackend" in pipelineManagerSource)
        assertTrue("fun setActiveBackend(" in pipelineManagerSource)
        assertTrue("fun beginFrame(" in pipelineManagerSource)
        assertTrue("fun finishLevelRender(" in pipelineManagerSource)
        assertTrue("getActiveBackend()" in levelRendererMixinSource || "beginFrame(" in levelRendererMixinSource)
        assertTrue("finishLevelRender(" in levelRendererMixinSource)
        assertFalse("GameRendererMixin" in mixinsSource)
        assertFalse(Files.exists(projectFile("common/src/main/java/cn/coostack/cooparticlesapi/mixin/GameRendererMixin.java")))
    }

    @Test
    fun `pipeline context carries raw framebuffer ids for iris external targets`() {
        val contextSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/backend/RenderFrameContext.kt"
        )
        val resolverSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/client/ClientRenderTargetResolver.kt"
        )
        val pipelineSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/client/ClientRenderPipelineManager.kt"
        )

        assertTrue("val sceneColorFramebufferId: Int? = null" in contextSource)
        assertTrue("val finalCompositeFramebufferId: Int? = null" in contextSource)
        assertTrue("val externalFramebuffer: Boolean = false" in contextSource)
        assertTrue("val usesExternalFramebuffer = matchedTarget == null && boundFramebufferId > 0" in resolverSource)
        assertTrue("targetLabel = finalTarget.label" in resolverSource)
        assertTrue("sceneColorFramebufferId = if (usesExternalFramebuffer) boundFramebufferId" in resolverSource)
        assertTrue("get() = sceneDepthTarget.depthTextureId.takeIf { it > 0 }" in resolverSource)
        assertTrue("val externalDepthFramebufferId = selectExternalDepthFramebufferId(boundFramebufferId, sceneDepthTarget)" in resolverSource)
        assertTrue("return if (sceneDepthTarget.depthTextureId > 0)" in resolverSource)
        assertTrue("sceneDepthFramebufferId = if (usesExternalFramebuffer) externalDepthFramebufferId" in resolverSource)
        assertTrue("glGetFramebufferAttachmentParameteri" in resolverSource)
        assertTrue("finalCompositeFramebufferId = if (usesExternalFramebuffer) boundFramebufferId" in resolverSource)
        assertTrue("sceneColorFramebufferId = if (activeBackend.supports(RenderBackendCapability.SCENE_COLOR_COPY))" in pipelineSource)
        assertTrue("sceneDepthFramebufferId = if (activeBackend.supports(RenderBackendCapability.SCENE_DEPTH_READ))" in pipelineSource)
        assertTrue("finalCompositeFramebufferId = resolvedTargets.finalCompositeFramebufferId" in pipelineSource)
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
