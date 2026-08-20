package cn.coostack.cooparticlesapi.cparticle

import java.nio.file.Path as NioPath
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CParticleIrisRenderStageContractTest {
    @Test
    fun `gpu particles render inside the platform particle engine pass`() {
        val fabricMixin = readProjectFile(
            "fabric/src/main/java/cn/coostack/cooparticlesapi/mixin/CParticleEngineFabricMixin.java"
        )
        val neoForgeMixin = readProjectFile(
            "neoforge/src/main/java/cn/coostack/cooparticlesapi/mixin/CParticleEngineNeoForgeMixin.java"
        )

        assertTrue("turnOffLightLayer()V" in fabricMixin)
        assertTrue("renderFabricParticlePass" in fabricMixin)
        assertTrue("turnOffLightLayer()V" in neoForgeMixin)
        assertTrue("renderTypeFilter" in neoForgeMixin)
        assertTrue("renderParticlePass" in neoForgeMixin)
    }

    /**
     * 校验 Iris MIXED 探测失败时，同一帧最多执行一次完整 GPU 粒子绘制。
     *
     * 示例：第一次 Fabric 粒子回调返回 `ALL`，后续回调返回 `NONE`。
     * 禁止在探测失败时让两次回调都返回 `ALL`，否则颜色与亮度会累加两次。
     */
    @Test
    fun `fabric fallback draws all gpu particles only once per frame`() {
        val manager = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/CParticleSystemManager.kt"
        )

        assertTrue("if (fabricParticlePassIndex++ == 0)" in manager)
    }

    @Test
    fun `gpu particles replay after terrain mapping instead of preserving its background`() {
        val manager = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/CParticleSystemManager.kt"
        )
        val terrainManager = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainPipelineManager.kt"
        )
        val clientRenderManager = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/client/ClientRenderEntityManager.kt"
        )
        val renderer = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/render/CParticleRenderer.kt"
        )
        val backend = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/OpenGlPostEffectExecutionBackend.kt"
        )
        val postExecutor = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/PostEffectFrameExecutor.kt"
        )
        val pipelineRuntime = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/pipeline/CooPipelineRuntimeEffect.kt"
        )
        val shader = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/post/procedural_mapping_screen.fsh"
        )
        val irisPipelineMixin = readProjectFile(
            "common/src/main/java/cn/coostack/cooparticlesapi/mixin/compat/iris/IrisRenderingPipelineMixin.java"
        )

        assertFalse("hasNoDepthParticlesRenderedThisFrame" in manager)
        assertFalse("CParticleSystemManager.hasNoDepthParticlesRenderedThisFrame()" in terrainManager)
        assertTrue("hasMandatoryCParticleCoverage" in terrainManager)
        assertTrue("CooPipelineTarget.FinalScreen" in terrainManager)
        assertTrue("warnUnprotectedMappingPipeline" in terrainManager)
        assertTrue("prepareTerrainCoverage(context, scenePost = true)" in clientRenderManager)
        assertTrue("prepareTerrainCoverage(context, scenePost = false)" in clientRenderManager)
        assertTrue("includeMappings = includeTerrainMappings" in clientRenderManager)
        assertTrue("groupKey.second != null && !includeMappings" in terrainManager)
        assertTrue("if (includeMappings) collectScreenOnlyMappingPostEffects" in terrainManager)
        assertTrue("reportCParticleCoverageFailure" in clientRenderManager)
        assertTrue("cParticleForegroundReplayScenePost()" in manager)
        assertTrue("PostEffectFrameExecutor.supportsForegroundReplay()" in manager)
        assertTrue("deferredTerrainForegroundFrame = renderFrameId" in manager)
        assertTrue("renderDeferredTerrainForeground" in manager)
        assertTrue("renderTerrainForeground" in renderer)
        assertTrue("forceDirectShader = true" in renderer)
        assertTrue("layer.applyIndexedState(0)" in renderer)
        assertTrue("withPrimaryColorWriteOnly(drawLayerSystems)" in renderer)
        assertTrue("pass.accepts(it.layer) && !it.released" in renderer)
        assertFalse("!it.layer.depthWrite" in renderer)
        assertTrue("captureCParticleCoverage" in renderer)
        assertTrue(
            "val useIrisParticleShader = irisShaderPackActive && !coverageOnly && !forceDirectShader" in renderer
        )
        assertTrue("indexedBlendStateAvailable = irisShaderPackActive" in renderer)
        assertTrue("GL30.glDisablei(GL_BLEND, 0)" in renderer)
        assertTrue("fun captureCParticleCoverage" in backend)
        assertTrue("cParticleCoverageTexture" in backend)
        assertTrue("glDisable(GL_SCISSOR_TEST)" in backend)
        assertTrue("previousScissorEnabled" in backend)
        assertTrue("PostEffectForegroundReplayBackend" in postExecutor)
        assertTrue("fun supportsForegroundReplay(): Boolean" in postExecutor)
        assertTrue("fun invalidateSceneColorCopy()" in postExecutor)
        assertTrue("fun replayForeground(context: RenderFrameContext, render: () -> Unit)" in postExecutor)
        assertTrue("override fun replayForeground" in backend)
        assertTrue("depthAttachmentType == GL_NONE" in backend)
        assertTrue("chainedSceneFramebufferId = framebuffer" in backend)
        assertTrue("invalidatePreparedSceneCopy()" in backend)
        assertTrue("PostEffectFrameExecutor.invalidateSceneColorCopy()" in pipelineRuntime)
        assertTrue("val indexedBlendStates = captureIndexedBlendStates()" in backend)
        assertTrue("restoreIndexedBlendStates(indexedBlendStates)" in backend)
        assertTrue("glIsEnabledi(GL_BLEND, drawBuffer)" in backend)
        val coverageCapture = backend.substringAfter("fun captureCParticleCoverage")
            .substringBefore("private fun ensureCParticleCoverageColor")
        val disableScissor = coverageCapture.indexOf("glDisable(GL_SCISSOR_TEST)")
        val renderCoverage = coverageCapture.indexOf("render()")
        val restoreScissor = coverageCapture.indexOf("if (previousScissorEnabled)")
        assertTrue(disableScissor >= 0 && renderCoverage > disableScissor)
        assertTrue(restoreScissor > renderCoverage)
        assertTrue("CParticleCoverageMask" in shader)
        assertTrue("CooHasCParticleCoverage != 0" in shader)
        assertTrue("texture(CParticleCoverageMask, screen_uv).r > 0.5" in shader)
        assertTrue("FinalPassRenderer;renderFinalPass()V" in irisPipelineMixin)
        assertTrue("shift = At.Shift.AFTER" in irisPipelineMixin)
        val frameCoverage = clientRenderManager.indexOf("prepareTerrainCoverage(context, scenePost = false)")
        val frameTerrain = clientRenderManager.indexOf("collectPostEffects", frameCoverage)
        val frameReplay = clientRenderManager.indexOf(
            "replayDeferredCParticleForeground(refreshedContext, scenePost = false)",
            frameTerrain,
        )
        val frameEntities = clientRenderManager.indexOf(
            ".filterNot(RenderEntityInstance<RenderEntity>::usesScenePost)",
            frameReplay,
        )
        assertTrue(frameCoverage >= 0)
        assertTrue(frameTerrain > frameCoverage)
        assertTrue(frameReplay > frameTerrain)
        assertTrue(frameEntities > frameReplay)

        val sceneCoverage = clientRenderManager.indexOf("prepareTerrainCoverage(context, scenePost = true)")
        val sceneRefresh = clientRenderManager.indexOf(
            "ClientRenderSceneResourcesResolver.resolveCurrentResources()",
            sceneCoverage,
        )
        val sceneTerrain = clientRenderManager.indexOf("collectScenePostEffects", sceneCoverage)
        val sceneReplay = clientRenderManager.indexOf(
            "replayDeferredCParticleForeground(terrainContext, scenePost = true)",
            sceneTerrain,
        )
        val sceneEntities = clientRenderManager.indexOf(
            "val graph = RenderEffectGraph(context.backend.capabilities, context)",
            sceneReplay,
        )
        assertTrue(sceneCoverage >= 0)
        assertTrue(sceneRefresh > sceneCoverage)
        assertTrue(sceneTerrain > sceneRefresh)
        assertTrue(sceneReplay > sceneTerrain)
        assertTrue(sceneEntities > sceneReplay)
    }

    @Test
    fun `world render events no longer draw gpu particles`() {
        val fabricClient = readProjectFile(
            "fabric/src/main/kotlin/cn/coostack/cooparticlesapi/CooParticlesAPIFabricClient.kt"
        )
        val neoForgeListener = readProjectFile(
            "neoforge/src/main/kotlin/cn/coostack/cooparticlesapi/listener/client/ClientEventsListener.kt"
        )

        assertFalse("CParticleSystemManager.renderWorld" in fabricClient)
        assertFalse("CParticleSystemManager.renderWorld" in neoForgeListener)
    }

    @Test
    fun `gpu draw expands instances before using the iris particle shader`() {
        val manager = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/CParticleSystemManager.kt"
        )
        val irisCompat = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/compat/IrisCompat.kt"
        )
        val renderer = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/render/CParticleRenderer.kt"
        )

        assertTrue("CParticleRenderer.render(" in manager)
        assertFalse("runWithFallbackFramebuffer" in manager)
        assertFalse("RenderSystem.getShader()" in manager)

        assertTrue("fun runWithParticleShader(" in irisCompat)
        assertTrue("getParticleTranslucentShader()" in irisCompat)
        assertTrue("particleShader.setDefaultUniforms(" in irisCompat)
        assertTrue("particleShader.apply()" in irisCompat)

        val expansion = renderer.indexOf("expandForParticleShader(")
        val irisScope = renderer.indexOf("IrisCompat.runWithParticleShader(")
        val expandedDraw = renderer.indexOf("drawExpanded(", irisScope)
        assertTrue(expansion >= 0)
        assertTrue(irisScope > expansion)
        assertTrue(expandedDraw > irisScope)
        assertFalse("glDrawBuffer(" in renderer)
        assertFalse("glDrawBuffers(" in renderer)
    }

    @Test
    fun `iris particle shaders keep low alpha fragments`() {
        val shaderKeyMixin = readProjectFile(
            "common/src/main/java/cn/coostack/cooparticlesapi/mixin/compat/iris/ShaderKeyIrisCompatMixin.java"
        )
        val mixinConfig = readProjectFile("common/src/main/resources/cooparticlesapi.mixins.json")

        assertTrue("net.irisshaders.iris.pipeline.programs.ShaderKey" in shaderKeyMixin)
        assertTrue("PARTICLES" in shaderKeyMixin)
        assertTrue("PARTICLES_TRANS" in shaderKeyMixin)
        assertTrue("0.001F" in shaderKeyMixin)
        assertTrue("compat.iris.ShaderKeyIrisCompatMixin" in mixinConfig)
    }

    private fun readProjectFile(relativePath: String): String {
        return findRepoRoot().resolve(relativePath).readText()
    }

    private fun findRepoRoot(): NioPath {
        var cursor = Path(System.getProperty("user.dir")).absolute()
        while (cursor.parent != null) {
            if (cursor.resolve("settings.gradle").exists()) return cursor
            cursor = cursor.parent
        }
        error("Could not locate repository root from ${System.getProperty("user.dir")}")
    }
}
