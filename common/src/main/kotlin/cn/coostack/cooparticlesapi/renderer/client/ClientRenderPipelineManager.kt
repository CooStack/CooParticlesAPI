package cn.coostack.cooparticlesapi.renderer.client

import cn.coostack.cooparticlesapi.CooParticlesAPIClient
import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackend
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendHooks
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameStage
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneTargets
import cn.coostack.cooparticlesapi.renderer.backend.VanillaSafeRenderBackend
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineRuntimeEffect
import cn.coostack.cooparticlesapi.renderer.post.PostEffectFrameExecutor
import com.mojang.blaze3d.pipeline.RenderTarget
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix4f

object ClientRenderPipelineManager {
    val minecraft: Minecraft get() = Minecraft.getInstance()
    var width = 1920
    var height = 1080
    var initialized = false
    var activeBackend: RenderBackend = VanillaSafeRenderBackend
        private set
    private var currentFrameContext: RenderFrameContext? = null
    private var frameActive = false
    private var scenePostCaptured = false
    private var scenePostExecuted = false
    private var irisScenePostFallbackLogged = false
    private var frameTickDelta = 0F
    private val frameViewMatrix = Matrix4f()
    private val frameProjectionMatrix = Matrix4f()
    private var lastLoggedTargetSignature: String? = null
    internal var cooFxWorldPassDelegate: ((RenderFrameContext) -> Unit)? = null
    private val backendHooks = object : RenderBackendHooks {
        /**
         * 执行 `ClientRenderPipelineManager` 定义的 `cacheFrameState` 操作；输入和返回值用于该组件当前的渲染职责。
         *
         * 示例：`cacheFrameState(context = context)`。
         *
         * @param context 当前操作需要的输入值；其语义由方法名和所属组件共同限定
         */
        override fun cacheFrameState(context: RenderFrameContext) {
            ClientRenderEntityManager.cacheFrameState(context.tickDelta, context.viewMatrix, context.projMatrix)
        }

        /**
         * 执行 `ClientRenderPipelineManager` 的 `renderWorldPass` 渲染操作，处理传入数据并更新当前帧或 GPU 状态。
         *
         * 示例：`renderWorldPass(context = context)`。
         *
         * @param context 当前操作需要的输入值；其语义由方法名和所属组件共同限定
         */
        override fun renderWorldPass(context: RenderFrameContext) {
            ClientRenderEntityManager.renderWorldPass(context.tickDelta, context.viewMatrix, context.projMatrix)
            if (!CooParticlesAPIClient.checkIrisShaderPackUsed()) {
                cooFxWorldPassDelegate?.invoke(context)
            }
        }

        /** 在 Iris final pass 前捕获带有效世界深度的场景 attachment。 */
        override fun captureScenePost(context: RenderFrameContext) {
            if (scenePostCaptured) return
            ClientRenderEntityManager.preparePostProcess(context.tickDelta, context.viewMatrix, context.projMatrix)
            PostEffectFrameExecutor.prepareFrame(context)
            ClientRenderEntityManager.captureScenePost(context)
            scenePostCaptured = true
        }

        /** 在云层、天气或 Iris final pass 完成后执行最终场景后处理。 */
        override fun runScenePost(context: RenderFrameContext) {
            if (!scenePostCaptured) {
                ClientRenderEntityManager.preparePostProcess(context.tickDelta, context.viewMatrix, context.projMatrix)
                PostEffectFrameExecutor.prepareFrame(context)
            }
            ClientRenderEntityManager.runScenePost(context)
        }

        /**
         * 初始化或准备 `ClientRenderPipelineManager` 的 `preparePostProcess` 阶段，使后续渲染调用可以使用相关资源。
         *
         * 示例：`preparePostProcess(context = context)`。
         *
         * @param context 当前操作需要的输入值；其语义由方法名和所属组件共同限定
         */
        override fun preparePostProcess(context: RenderFrameContext) {
            ClientRenderEntityManager.preparePostProcess(context.tickDelta, context.viewMatrix, context.projMatrix)
            PostEffectFrameExecutor.prepareFrame(context)
        }

        /**
         * 执行 `ClientRenderPipelineManager` 的 `flushFrameComposites` 渲染操作，处理传入数据并更新当前帧或 GPU 状态。
         *
         * 示例：`flushFrameComposites(context = context)`。
         *
         * @param context 当前操作需要的输入值；其语义由方法名和所属组件共同限定
         */
        override fun flushFrameComposites(context: RenderFrameContext) {
            ClientRenderEntityManager.flushFrameComposites()
        }

        /**
         * 执行 `ClientRenderPipelineManager` 定义的 `runFramePost` 操作；输入和返回值用于该组件当前的渲染职责。
         *
         * 示例：`runFramePost(context = context)`。
         *
         * @param context 当前操作需要的输入值；其语义由方法名和所属组件共同限定
         */
        override fun runFramePost(context: RenderFrameContext) {
            ClientRenderEntityManager.runFramePost(context)
        }
    }

    /**
     * 初始化或准备 `ClientRenderPipelineManager` 的 `init` 阶段，使后续渲染调用可以使用相关资源。
     *
     * 示例：`init()`。
     */
    fun init() {
        initialized = true
    }

    /**
     * 释放 `ClientRenderPipelineManager` 在 `release` 中管理的资源；再次使用前必须重新初始化。
     *
     * 示例：`release()`。
     */
    fun release() {
        initialized = false
        currentFrameContext = null
        frameActive = false
        scenePostCaptured = false
        scenePostExecuted = false
        cooFxWorldPassDelegate = null
    }

    /**
     * 更新 `ClientRenderPipelineManager` 的 `setActiveBackend` 状态；修改会影响后续查询、构建或当前帧绘制。
     *
     * 示例：`setActiveBackend(backend = backend)`。
     *
     * @param backend 本次调用选用的渲染 backend，其能力会影响可执行阶段和资源来源
     */
    fun setActiveBackend(backend: RenderBackend) {
        activeBackend = backend
    }

    /**
     * 初始化或准备 `ClientRenderPipelineManager` 的 `beginFrame` 阶段，使后续渲染调用可以使用相关资源。
     *
     * 示例：`beginFrame(tickDelta = tickDelta, viewMatrix = viewMatrix, projMatrix = projMatrix)`。
     *
     * @param tickDelta 当前 tick 内的插值比例，通常位于 0 到 1
     *
     * @param viewMatrix 把世界坐标变换到相机空间的视图矩阵
     *
     * @param projMatrix 把相机空间坐标变换到裁剪空间的投影矩阵
     */
    fun beginFrame(tickDelta: Float, viewMatrix: Matrix4f, projMatrix: Matrix4f) {
        frameActive = true
        scenePostCaptured = false
        scenePostExecuted = false
        CooPipelineRuntimeEffect.beginFrame()
        frameTickDelta = tickDelta
        frameViewMatrix.set(viewMatrix)
        frameProjectionMatrix.set(projMatrix)
        runStages(
            listOf(RenderFrameStage.FRAME_BEGIN),
            tickDelta,
            viewMatrix,
            projMatrix
        )
    }

    /** Iris 在 shader pack final pass 前调用，此时世界深度 attachment 仍然有效。 */
    fun captureIrisScenePost() {
        if (!frameActive || !CooParticlesAPIClient.checkIrisShaderPackUsed()) return
        runStages(listOf(RenderFrameStage.SCENE_CAPTURE), frameTickDelta, frameViewMatrix, frameProjectionMatrix)
    }

    /** Iris entity G-buffer 仍有效时提交 CooFX 世界批次。 */
    fun renderIrisCooFxWorldPass() {
        if (!frameActive || !CooParticlesAPIClient.checkIrisShaderPackUsed()) return
        val context = buildFrameContext(
            frameTickDelta,
            frameViewMatrix,
            frameProjectionMatrix,
            RenderFrameStage.WORLD_PASS,
        )
        currentFrameContext = context
        cooFxWorldPassDelegate?.invoke(context)
    }

    /** Iris 在 shader pack final pass 完成后调用。 */
    fun renderIrisScenePost() {
        if (!frameActive || !CooParticlesAPIClient.checkIrisShaderPackUsed()) return
        runScenePost(frameTickDelta, frameViewMatrix, frameProjectionMatrix)
    }

    private fun runScenePost(tickDelta: Float, viewMatrix: Matrix4f, projMatrix: Matrix4f) {
        if (scenePostExecuted) return
        scenePostExecuted = true
        runStages(listOf(RenderFrameStage.SCENE_POST), tickDelta, viewMatrix, projMatrix)
    }

    /**
     * 执行 `ClientRenderPipelineManager` 定义的 `finishLevelRender` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`finishLevelRender(tickDelta = tickDelta, viewMatrix = viewMatrix, projMatrix = projMatrix)`。
     *
     * @param tickDelta 当前 tick 内的插值比例，通常位于 0 到 1
     *
     * @param viewMatrix 把世界坐标变换到相机空间的视图矩阵
     *
     * @param projMatrix 把相机空间坐标变换到裁剪空间的投影矩阵
     */
    fun finishLevelRender(tickDelta: Float, viewMatrix: Matrix4f, projMatrix: Matrix4f) {
        try {
            if (frameActive && !scenePostExecuted) {
                if (CooParticlesAPIClient.checkIrisShaderPackUsed() && !irisScenePostFallbackLogged) {
                    CooParticlesConstants.logger.warn(
                        "Iris scene-post hook did not run; executing the fallback at the end of level rendering"
                    )
                    irisScenePostFallbackLogged = true
                }
                runScenePost(tickDelta, viewMatrix, projMatrix)
            }
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
        } finally {
            currentFrameContext = null
            frameActive = false
        }
    }

    /**
     * 执行 `ClientRenderPipelineManager` 定义的 `endFrame` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`endFrame()`。
     */
    fun endFrame() {
        currentFrameContext = null
        frameActive = false
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
            sceneColorFramebufferId = if (activeBackend.supports(RenderBackendCapability.SCENE_COLOR_COPY)) {
                resolvedTargets.sceneColorFramebufferId
            } else {
                null
            },
            sceneDepthTextureId = sceneDepthTextureId,
            sceneDepthFramebufferId = if (activeBackend.supports(RenderBackendCapability.SCENE_DEPTH_READ)) {
                resolvedTargets.sceneDepthFramebufferId
            } else {
                null
            },
            finalCompositeTarget = resolvedTargets.finalCompositeTarget,
            finalCompositeFramebufferId = resolvedTargets.finalCompositeFramebufferId,
            externalFramebuffer = resolvedTargets.externalFramebuffer,
            resolvedTargetLabel = resolvedTargets.targetLabel,
            boundFramebufferId = resolvedTargets.boundFramebufferId,
            targetWidth = resolvedTargets.width,
            targetHeight = resolvedTargets.height
        )
    }

    /**
     * 从 `ClientRenderPipelineManager` 当前维护的状态中读取 `currentSceneColorTextureId` 结果，不创建新的渲染资源。
     *
     * 示例：`currentSceneColorTextureId()`。
     *
     * @return 匹配当前条件的对象或状态；可空返回值表示没有可用结果
     */
    fun currentSceneColorTextureId(): Int {
        val context = currentFrameContext
        if (context == null) {
            return minecraft.mainRenderTarget.colorTextureId
        }
        return context.sceneColorTextureId
            ?: context.sceneResources.get(RenderSceneTargets.SCENE_COLOR)?.colorTextureId
            ?: minecraft.mainRenderTarget.colorTextureId
    }

    /**
     * 从 `ClientRenderPipelineManager` 当前维护的状态中读取 `currentSceneDepthTextureId` 结果，不创建新的渲染资源。
     *
     * 示例：`currentSceneDepthTextureId()`。
     *
     * @return 匹配当前条件的对象或状态；可空返回值表示没有可用结果
     */
    fun currentSceneDepthTextureId(): Int {
        val context = currentFrameContext
        if (context == null) {
            return minecraft.mainRenderTarget.depthTextureId
        }
        return context.sceneDepthTextureId
            ?: context.sceneResources.get(RenderSceneTargets.SCENE_DEPTH)?.depthTextureId
            ?: minecraft.mainRenderTarget.depthTextureId
    }

    /**
     * 从 `ClientRenderPipelineManager` 当前维护的状态中读取 `currentSceneResourceTextureId` 结果，不创建新的渲染资源。
     *
     * 示例：`currentSceneResourceTextureId(id = id, attachment = attachment)`。
     *
     * @param id 用于定位目标资源、实体或运行时实例的唯一标识
     *
     * @param attachment 数量或从零开始的索引值，具体上限由当前资源配置决定
     *
     * @return 匹配当前条件的对象或状态；可空返回值表示没有可用结果
     */
    internal fun currentSceneResourceTextureId(id: ResourceLocation, attachment: Int = 0): Int? {
        return currentFrameContext?.sceneResources?.get(id)?.colorTextureId(attachment)
    }

    /**
     * 从 `ClientRenderPipelineManager` 当前维护的状态中读取 `currentFinalCompositeTarget` 结果，不创建新的渲染资源。
     *
     * 示例：`currentFinalCompositeTarget()`。
     *
     * @return 匹配当前条件的对象或状态；可空返回值表示没有可用结果
     */
    fun currentFinalCompositeTarget(): RenderTarget {
        val context = currentFrameContext
        if (context == null) {
            return minecraft.mainRenderTarget
        }
        return context.sceneResources.get(RenderSceneTargets.POST)?.target
            ?: context.finalCompositeTarget
            ?: minecraft.mainRenderTarget
    }

    /**
     * 从 `ClientRenderPipelineManager` 当前维护的状态中读取 `currentRenderTargetLabel` 结果，不创建新的渲染资源。
     *
     * 示例：`currentRenderTargetLabel()`。
     *
     * @return 匹配当前条件的对象或状态；可空返回值表示没有可用结果
     */
    fun currentRenderTargetLabel(): String {
        return currentFrameContext?.resolvedTargetLabel ?: "main"
    }

    /**
     * 从 `ClientRenderPipelineManager` 当前维护的状态中读取 `currentRenderWidth` 结果，不创建新的渲染资源。
     *
     * 示例：`currentRenderWidth()`。
     *
     * @return 匹配当前条件的对象或状态；可空返回值表示没有可用结果
     */
    fun currentRenderWidth(): Int {
        return currentFrameContext?.targetWidth ?: minecraft.mainRenderTarget.width
    }

    /**
     * 从 `ClientRenderPipelineManager` 当前维护的状态中读取 `currentRenderHeight` 结果，不创建新的渲染资源。
     *
     * 示例：`currentRenderHeight()`。
     *
     * @return 匹配当前条件的对象或状态；可空返回值表示没有可用结果
     */
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
            append(':')
            append(targets.finalCompositeFramebufferId)
            append(':')
            append(targets.externalFramebuffer)
        }
        if (signature == lastLoggedTargetSignature) {
            return
        }
        lastLoggedTargetSignature = signature
        CooParticlesConstants.logger.info(
            "Resolved post target label={} boundFbo={} targetFbo={} finalFbo={} sceneColor={} sceneDepth={} external={}",
            targets.targetLabel,
            targets.boundFramebufferId,
            targets.finalCompositeTarget.frameBufferId,
            targets.finalCompositeFramebufferId,
            targets.sceneColorTextureId,
            targets.sceneDepthTextureId,
            targets.externalFramebuffer
        )
    }

    /**
     * 更新 `ClientRenderPipelineManager` 的 `resizeTo` 状态；修改会影响后续查询、构建或当前帧绘制。
     *
     * 示例：`resizeTo(width = width, height = height)`。
     *
     * @param width 目标尺寸的像素数，必须与相关纹理或 framebuffer 尺寸一致
     *
     * @param height 目标尺寸的像素数，必须与相关纹理或 framebuffer 尺寸一致
     */
    fun resizeTo(width: Int, height: Int) {
        this.width = width
        this.height = height
    }
}
