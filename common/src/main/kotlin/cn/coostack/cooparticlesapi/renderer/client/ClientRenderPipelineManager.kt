package cn.coostack.cooparticlesapi.renderer.client

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackend
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendHooks
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameStage
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneTargets
import cn.coostack.cooparticlesapi.renderer.backend.VanillaSafeRenderBackend
import cn.coostack.cooparticlesapi.renderer.shader.pipe.manager.ShaderPipeManager
import com.mojang.blaze3d.pipeline.RenderTarget
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix4f
import java.util.function.Supplier

object ClientRenderPipelineManager {
    val registerPipeLines = HashMap<ResourceLocation, ShaderPipeManager>()
    fun getPipeManager(id: ResourceLocation): ShaderPipeManager? = registerPipeLines[id]
    val minecraft: Minecraft get() = Minecraft.getInstance()
    var width = 1920
    var height = 1080
    var initialized = false
    var activeBackend: RenderBackend = VanillaSafeRenderBackend
        private set
    private var currentFrameContext: RenderFrameContext? = null
    private var lastLoggedTargetSignature: String? = null
    private val backendHooks = object : RenderBackendHooks {
        override fun cacheFrameState(context: RenderFrameContext) {
            ClientRenderEntityManager.cacheFrameState(context.tickDelta, context.viewMatrix, context.projMatrix)
        }

        override fun renderWorldPass(context: RenderFrameContext) {
            ClientRenderEntityManager.renderWorldPass(context.tickDelta, context.viewMatrix, context.projMatrix)
        }

        override fun preparePostProcess(context: RenderFrameContext) {
            ClientRenderEntityManager.preparePostProcess(context.tickDelta, context.viewMatrix, context.projMatrix)
        }

        override fun flushFrameComposites(context: RenderFrameContext) {
            ClientRenderEntityManager.flushFrameComposites()
        }

        override fun runFramePost(context: RenderFrameContext) {
            ClientRenderEntityManager.runFramePost(context)
        }
    }

    fun register(pipe: ShaderPipeManager) {
        registerPipeLines[pipe.pipeID] = pipe
        if (initialized) {
            pipe.depthSupplier = Supplier {
                currentSceneDepthTextureId()
            }
            pipe.init()
        }
    }

    fun init() {
        initialized = true
        for (manager in registerPipeLines.values) {
            manager.depthSupplier = Supplier {
                currentSceneDepthTextureId()
            }
            manager.resize(width, height)
            manager.init()
        }
    }

    fun release() {
        initialized = false
        currentFrameContext = null
        registerPipeLines.forEach {
            it.value.release()
        }
    }

    fun setActiveBackend(backend: RenderBackend) {
        activeBackend = backend
    }

    fun beginFrame(tickDelta: Float, viewMatrix: Matrix4f, projMatrix: Matrix4f) {
        runStages(
            listOf(RenderFrameStage.FRAME_BEGIN),
            tickDelta,
            viewMatrix,
            projMatrix
        )
    }

    fun finishLevelRender(tickDelta: Float, viewMatrix: Matrix4f, projMatrix: Matrix4f) {
        runStages(
            listOf(
                RenderFrameStage.WORLD_PASS,
                RenderFrameStage.POST_PROCESS_PREPARE,
                RenderFrameStage.FRAME_POST,
                RenderFrameStage.FRAME_END
            ),
            tickDelta,
            viewMatrix,
            projMatrix
        )
        currentFrameContext = null
    }

    fun endFrame() {
        currentFrameContext = null
    }

    private fun runStages(
        stages: List<RenderFrameStage>,
        tickDelta: Float,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f
    ) {
        stages.forEach { stage ->
            val context = buildFrameContext(tickDelta, viewMatrix, projMatrix, stage)
            currentFrameContext = context
            activeBackend.runStage(stage, context, backendHooks)
        }
    }

    private fun buildFrameContext(
        tickDelta: Float,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        stage: RenderFrameStage
    ): RenderFrameContext {
        val resolvedTargets = ClientRenderTargetResolver.resolveCurrentTargets()
        val sceneResources = ClientRenderSceneResourcesResolver.resolveCurrentResources()
        logResolvedTargets(resolvedTargets)
        val sceneColorTextureId =
            if (activeBackend.supports(RenderBackendCapability.SCENE_COLOR_COPY)) resolvedTargets.sceneColorTextureId else null
        val sceneDepthTextureId =
            if (activeBackend.supports(RenderBackendCapability.SCENE_DEPTH_READ)) resolvedTargets.sceneDepthTextureId else null
        return RenderFrameContext(
            tickDelta = tickDelta,
            viewMatrix = Matrix4f(viewMatrix),
            projMatrix = Matrix4f(projMatrix),
            backend = activeBackend,
            stage = stage,
            sceneResources = sceneResources,
            sceneColorTextureId = sceneColorTextureId,
            sceneDepthTextureId = sceneDepthTextureId,
            finalCompositeTarget = resolvedTargets.finalCompositeTarget,
            resolvedTargetLabel = resolvedTargets.targetLabel,
            boundFramebufferId = resolvedTargets.boundFramebufferId,
            targetWidth = resolvedTargets.width,
            targetHeight = resolvedTargets.height
        )
    }

    fun currentSceneColorTextureId(): Int {
        val context = currentFrameContext
        if (context == null) {
            return minecraft.mainRenderTarget.colorTextureId
        }
        return context.sceneColorTextureId
            ?: context.sceneResources.get(RenderSceneTargets.SCENE_COLOR)?.colorTextureId
            ?: minecraft.mainRenderTarget.colorTextureId
    }

    fun currentSceneDepthTextureId(): Int {
        val context = currentFrameContext
        if (context == null) {
            return minecraft.mainRenderTarget.depthTextureId
        }
        return context.sceneDepthTextureId
            ?: context.sceneResources.get(RenderSceneTargets.SCENE_DEPTH)?.depthTextureId
            ?: minecraft.mainRenderTarget.depthTextureId
    }

    fun currentFinalCompositeTarget(): RenderTarget {
        val context = currentFrameContext
        if (context == null) {
            return minecraft.mainRenderTarget
        }
        return context.sceneResources.get(RenderSceneTargets.POST)?.target
            ?: context.finalCompositeTarget
            ?: minecraft.mainRenderTarget
    }

    fun currentRenderTargetLabel(): String {
        return currentFrameContext?.resolvedTargetLabel ?: "main"
    }

    fun currentRenderWidth(): Int {
        return currentFrameContext?.targetWidth ?: minecraft.mainRenderTarget.width
    }

    fun currentRenderHeight(): Int {
        return currentFrameContext?.targetHeight ?: minecraft.mainRenderTarget.height
    }

    private fun logResolvedTargets(targets: ResolvedRenderTargets) {
        val signature = buildString {
            append(targets.targetLabel)
            append(':')
            append(targets.boundFramebufferId)
            append(':')
            append(targets.finalCompositeTarget.frameBufferId)
            append(':')
            append(targets.sceneColorTextureId)
            append(':')
            append(targets.sceneDepthTextureId)
        }
        if (signature == lastLoggedTargetSignature) {
            return
        }
        lastLoggedTargetSignature = signature
        CooParticlesConstants.logger.info(
            "Resolved post target label={} boundFbo={} targetFbo={} sceneColor={} sceneDepth={}",
            targets.targetLabel,
            targets.boundFramebufferId,
            targets.finalCompositeTarget.frameBufferId,
            targets.sceneColorTextureId,
            targets.sceneDepthTextureId
        )
    }

    fun resizeTo(width: Int, height: Int) {
        this.width = width
        this.height = height
        registerPipeLines.onEach {
            it.value.resize(width, height)
        }
    }
}
