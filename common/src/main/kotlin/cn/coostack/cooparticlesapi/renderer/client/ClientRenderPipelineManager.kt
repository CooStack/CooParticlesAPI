package cn.coostack.cooparticlesapi.renderer.client

import cn.coostack.cooparticlesapi.renderer.backend.RenderBackend
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendHooks
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.backend.VanillaSafeRenderBackend
import cn.coostack.cooparticlesapi.renderer.shader.pipe.manager.ShaderPipeManager
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
                minecraft.mainRenderTarget.depthTextureId
            }
            pipe.init()
        }
    }

    fun init() {
        initialized = true
        for (manager in registerPipeLines.values) {
            manager.depthSupplier = Supplier {
                minecraft.mainRenderTarget.depthTextureId
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
        val context = buildFrameContext(tickDelta, viewMatrix, projMatrix)
        currentFrameContext = context
        activeBackend.beginFrame(context, backendHooks)
    }

    fun finishLevelRender(tickDelta: Float, viewMatrix: Matrix4f, projMatrix: Matrix4f) {
        val context = currentFrameContext ?: buildFrameContext(tickDelta, viewMatrix, projMatrix).also {
            currentFrameContext = it
        }
        activeBackend.finishLevelRender(context, backendHooks)
    }

    fun endFrame() {
        val context = currentFrameContext ?: return
        activeBackend.endFrame(context, backendHooks)
        currentFrameContext = null
    }

    private fun buildFrameContext(tickDelta: Float, viewMatrix: Matrix4f, projMatrix: Matrix4f): RenderFrameContext {
        val sceneColorTextureId =
            if (activeBackend.supports(RenderBackendCapability.SCENE_COLOR_COPY)) minecraft.mainRenderTarget.colorTextureId else null
        val sceneDepthTextureId =
            if (activeBackend.supports(RenderBackendCapability.SCENE_DEPTH_READ)) minecraft.mainRenderTarget.depthTextureId else null
        return RenderFrameContext(
            tickDelta = tickDelta,
            viewMatrix = Matrix4f(viewMatrix),
            projMatrix = Matrix4f(projMatrix),
            backend = activeBackend,
            sceneColorTextureId = sceneColorTextureId,
            sceneDepthTextureId = sceneDepthTextureId
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
