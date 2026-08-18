package cn.coostack.cooparticlesapi.coofx.client

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.coofx.adapter.CooFxFrameRequest
import cn.coostack.cooparticlesapi.coofx.adapter.CooFxModelPlayRequest
import cn.coostack.cooparticlesapi.coofx.adapter.CooFxModelPlayResult
import cn.coostack.cooparticlesapi.coofx.adapter.CooFxPlaybackHandle
import cn.coostack.cooparticlesapi.coofx.adapter.CooFxPlayRequest
import cn.coostack.cooparticlesapi.coofx.adapter.CooFxPlayResult
import cn.coostack.cooparticlesapi.coofx.adapter.CooFxWorldTransform
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderPipelineManager
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import org.joml.Matrix4fc

internal fun hasFiniteCooFxFrame(viewMatrix: Matrix4fc, projectionMatrix: Matrix4fc): Boolean {
    return viewMatrix.isFinite && projectionMatrix.isFinite
}

/**
 * CooFX 的唯一客户端入口。
 *
 * [init] 只创建 CPU owner；shader、buffer、texture 和 draw 只会从渲染线程的 WORLD_PASS 委托进入。
 * 服务端入口不得引用本对象。资源 reload 和 world clear 由已有 common client 生命周期统一转发。
 */
object CooFXClient {
    private var runtime: CooFxClientRuntime? = null
    private var resourcesPrepared = false
    private var invalidFrameWarningLogged = false

    @JvmStatic
    fun init() {
        if (runtime == null) runtime = CooFxClientRuntime()
    }

    @JvmStatic
    fun play(request: CooFxPlayRequest): CooFxPlayResult {
        init()
        return requireNotNull(runtime).play(request)
    }

    /** 创建不依赖 emitter 的静态或动画模型实例。 */
    @JvmStatic
    fun playModel(request: CooFxModelPlayRequest): CooFxModelPlayResult {
        init()
        return requireNotNull(runtime).playModel(request)
    }

    /** 客户端 scene registry 用于原位更新模型，不创建新的模型实例。 */
    internal fun updateModel(handle: CooFxPlaybackHandle, request: CooFxModelPlayRequest): Boolean {
        return runtime?.updateModel(handle.instanceId, request) == true
    }

    /** 客户端 scene registry 用于原位更新 emitter 世界变换。 */
    internal fun updateParticle(handle: CooFxPlaybackHandle, request: CooFxPlayRequest): Boolean {
        return runtime?.updateParticle(handle.instanceId, request) == true
    }

    /** 客户端 scene registry 用于采样选定 asset camera 的 node world pose。 */
    internal fun sampleCamera(
        resourceId: ResourceLocation,
        cameraSelector: String?,
        clipId: String?,
        rawTimeSeconds: Float,
        transform: CooFxWorldTransform,
    ): CooFxCameraPose? {
        return runtime?.sampleCamera(resourceId, cameraSelector, clipId, rawTimeSeconds, transform)
    }

    /** 取消尚未进入粒子模拟器的客户端排队请求。 */
    internal fun cancelQueuedParticle(requestId: Long): Boolean {
        return runtime?.cancelQueued(requestId) == true
    }

    /** 取消尚未进入模型实例表的客户端排队请求。 */
    internal fun cancelQueuedModel(requestId: Long): Boolean {
        return runtime?.cancelQueuedModel(requestId) == true
    }

    internal fun onRenderPipelineReady() {
        RenderSystem.assertOnRenderThread()
        init()
        ClientRenderPipelineManager.cooFxWorldPassDelegate = { context ->
            renderWorldFrame(context)
        }
    }

    internal fun tickClient() {
        runtime?.tickClient()
    }

    internal fun clearTransientWorldState() {
        runtime?.clearTransientWorldState()
    }

    internal fun prepareResourcesIfNeeded(resourceManager: ResourceManager) {
        if (!resourcesPrepared) reloadResources(resourceManager)
    }

    internal fun reloadResources(resourceManager: ResourceManager) {
        init()
        requireNotNull(runtime).reloadResources(resourceManager)
        resourcesPrepared = true
    }

    internal fun releaseRenderResources() {
        ClientRenderPipelineManager.cooFxWorldPassDelegate = null
        runtime?.releaseRenderResources()
    }

    @JvmStatic
    fun stopClient() {
        ClientRenderPipelineManager.cooFxWorldPassDelegate = null
        runtime?.stopClient()
        runtime = null
        resourcesPrepared = false
        invalidFrameWarningLogged = false
    }

    private fun renderWorldFrame(context: RenderFrameContext) {
        if (!hasFiniteCooFxFrame(context.viewMatrix, context.projMatrix)) {
            if (!invalidFrameWarningLogged) {
                CooParticlesConstants.logger.warn("CooFX 跳过 view/projection matrix 尚未有效的世界渲染帧")
                invalidFrameWarningLogged = true
            }
            return
        }
        invalidFrameWarningLogged = false
        val capabilities = context.backend.capabilities
            .map { capability -> capability.name }
            .sorted()
            .joinToString(",")
        val backendSignature = "${context.backend.javaClass.name}:$capabilities"
        val cameraPosition = Minecraft.getInstance().gameRenderer.mainCamera.position
        runtime?.renderWorldFrame(
            CooFxFrameRequest(
                partialTick = context.tickDelta,
                backendCapabilitySignature = backendSignature,
                viewMatrix = context.viewMatrix,
                projectionMatrix = context.projMatrix,
                cameraX = cameraPosition.x,
                cameraY = cameraPosition.y,
                cameraZ = cameraPosition.z,
            )
        )
    }
}
