package cn.coostack.cooparticlesapi.renderer.runtime

import cn.coostack.cooparticlesapi.compat.IrisCompat
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameStage
import cn.coostack.cooparticlesapi.renderer.client.RenderUtil
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectCollector
import cn.coostack.cooparticlesapi.renderer.effects.builtin.BuiltinRenderEffectDescriptors
import cn.coostack.cooparticlesapi.renderer.pipeline.CooCompiledPostEffect
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineNodeKind
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineOutputPort
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineRuntimeEffect
import cn.coostack.cooparticlesapi.renderer.state.RenderStateGuard
import org.joml.Matrix4f
import org.joml.Matrix4fStack

/**
 * 单个 RenderEntity 的客户端运行时实例。
 *
 * @property entity 当前客户端镜像实体
 * @property renderer 该实体类型共享的 renderer
 */
class RenderEntityInstance<T : RenderEntity>(
    val entity: T,
    val renderer: RenderEntityRenderer<T>
) {
    /** V2 renderer 共享的静态编译结果；旧式实体自带 renderer 时保持实例隔离。 */
    private var pipelineRuntime = resolvePipelineRuntime()
    /** 当前共享 runtime 提供的后处理定义引用。 */
    private var compiledPostEffect: CooCompiledPostEffect? = pipelineRuntime.compiledPostEffect
    private val offscreenStateGuard = RenderStateGuard()
    private var irisWorldPassSubmitted = false
    private val frameWorldModelMatrix = Matrix4f()
    private var frameWorldModelPrepared = false

    /**
     * 执行 `RenderEntityInstance` 定义的 `reinitialize` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`reinitialize()`。
     */
    internal fun reinitialize() {
        pipelineRuntime = resolvePipelineRuntime()
        compiledPostEffect = pipelineRuntime.compiledPostEffect
    }

    /**
     * 更新 `RenderEntityInstance` 的 `updateFrom` 状态；修改会影响后续查询、构建或当前帧绘制。
     *
     * 示例：`updateFrom(profile = profile)`。
     *
     * @param profile 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    internal fun updateFrom(profile: RenderEntity) {
        entity.loadProfileFromEntity(profile)
    }

    /**
     * 执行 `RenderEntityInstance` 的 `render` 渲染操作，处理传入数据并更新当前帧或 GPU 状态。
     *
     * 示例：`render(tickDelta = tickDelta, viewMatrix = viewMatrix, projMatrix = projMatrix, modelMatrix = modelMatrix, stateGuard = stateGuard)`。
     *
     * @param tickDelta 当前 tick 内的插值比例，通常位于 0 到 1
     *
     * @param viewMatrix 把世界坐标变换到相机空间的视图矩阵
     *
     * @param projMatrix 把相机空间坐标变换到裁剪空间的投影矩阵
     *
     * @param modelMatrix 当前对象使用的模型变换或矩阵栈
     *
     * @param stateGuard 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    internal fun render(
        tickDelta: Float,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        modelMatrix: Matrix4fStack,
        stateGuard: RenderStateGuard
    ) {
        if (consumeIrisWorldPass()) return
        frameWorldModelMatrix.set(modelMatrix)
        frameWorldModelPrepared = true
        renderWorld(tickDelta, viewMatrix, projMatrix, modelMatrix, stateGuard)
    }

    /**
     * 执行 `RenderEntityInstance` 的 `renderIrisWorldPass` 渲染操作，处理传入数据并更新当前帧或 GPU 状态。
     *
     * 示例：`renderIrisWorldPass(tickDelta = tickDelta, viewMatrix = viewMatrix, projMatrix = projMatrix, modelMatrix = modelMatrix, stateGuard = stateGuard)`。
     *
     * @param tickDelta 当前 tick 内的插值比例，通常位于 0 到 1
     *
     * @param viewMatrix 把世界坐标变换到相机空间的视图矩阵
     *
     * @param projMatrix 把相机空间坐标变换到裁剪空间的投影矩阵
     *
     * @param modelMatrix 当前对象使用的模型变换或矩阵栈
     *
     * @param stateGuard 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    internal fun renderIrisWorldPass(
        tickDelta: Float,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        modelMatrix: Matrix4fStack,
        stateGuard: RenderStateGuard
    ) {
        if (!renderer.shaderPackHandled || !hasWorldPass()) return
        // Iris 后续会再次捕获同一实体的 mask；复用本次可见绘制的插值矩阵，避免位置更新后两次绘制错位。
        frameWorldModelMatrix.set(modelMatrix)
        frameWorldModelPrepared = true
        IrisCompat.runWithRenderEntityShader {
            renderWorld(tickDelta, viewMatrix, projMatrix, modelMatrix, stateGuard)
        }
        irisWorldPassSubmitted = true
    }

    private fun renderWorld(
        tickDelta: Float,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        modelMatrix: Matrix4fStack,
        stateGuard: RenderStateGuard
    ) {
        if (!hasWorldPass()) return
        pipelineRuntime.worldNodes.forEach { node ->
            stateGuard.use { renderState ->
                renderer.render(
                    RenderInput(
                        entity = entity,
                        tickDelta = tickDelta,
                        viewMatrix = viewMatrix,
                        projMatrix = projMatrix,
                        modelMatrix = modelMatrix,
                        renderState = renderState,
                        pipeline = renderer.pipeline,
                        node = node,
                        phase = RenderPhase.WORLD
                    )
                )
            }
        }
    }

    /**
     * 初始化或准备 `RenderEntityInstance` 的 `beginWorldRenderFrame` 阶段，使后续渲染调用可以使用相关资源。
     *
     * 示例：`beginWorldRenderFrame()`。
     */
    internal fun beginWorldRenderFrame() {
        irisWorldPassSubmitted = false
        frameWorldModelPrepared = false
    }

    private fun consumeIrisWorldPass(): Boolean {
        val submitted = irisWorldPassSubmitted
        irisWorldPassSubmitted = false
        return submitted
    }

    private fun hasWorldPass(): Boolean {
        return RenderFrameStage.WORLD_PASS in pipelineRuntime.compiledPipeline.stages &&
            pipelineRuntime.worldNodes.isNotEmpty()
    }

    /** @return 当前 Pipeline 的 fullscreen 节点是否在场景 composite 前执行。 */
    internal fun usesScenePost(): Boolean {
        return RenderFrameStage.SCENE_POST in pipelineRuntime.compiledPipeline.stages
    }

    /** @return 可见世界几何是否交给当前 shader pack 处理 */
    internal fun isShaderPackHandled(): Boolean = renderer.shaderPackHandled

    /**
     * 更新 `RenderEntityInstance` 的 `markRemoved` 状态；修改会影响后续查询、构建或当前帧绘制。
     *
     * 示例：`markRemoved()`。
     */
    internal fun markRemoved() {
        entity.canceled = true
    }

    /**
     * 执行 `RenderEntityInstance` 定义的 `collectEffects` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`collectEffects(context = context, collector = collector)`。
     *
     * @param context 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param collector 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    internal fun collectEffects(context: RenderFrameContext, collector: RenderEffectCollector) {
        collectPipelineEffect(context, collector)
        BuiltinRenderEffectDescriptors.collectEntity(entity, context, collector)
    }

    /** 只提交 Pipeline attachment 捕获与 fullscreen graph，不重复提交其他内置效果。 */
    internal fun collectPipelineEffect(context: RenderFrameContext, collector: RenderEffectCollector) {
        val postEffect = compiledPostEffect
        if (postEffect != null) {
            val instance = postEffect.type.create(
                instanceId = "${entity.uuid}:pipeline",
                params = postEffect.defaultParams,
                sourceId = entity.uuid.toString(),
                subject = entity
            )
            collector.submit(
                CooPipelineRuntimeEffect.descriptor(
                    owner = entity.uuid.toString(),
                    compiled = pipelineRuntime.compiledPipeline,
                    postEffect = instance,
                    attachments = pipelineRuntime.worldAttachments,
                    shaderPackHandled = renderer.shaderPackHandled,
                    renderWorld = { renderSceneWorld(context) }
                ) { output ->
                    renderOffscreen(context, output)
                }
            )
        }
    }

    /** 在场景后处理调度点提交一次世界几何，并缓存矩阵供失败回退的离屏捕获复用。 */
    private fun renderSceneWorld(context: RenderFrameContext) {
        if (consumeIrisWorldPass()) return
        val stack = Matrix4fStack(16)
        stack.set(RenderUtil.buildModelMatrix(entity, context.tickDelta))
        frameWorldModelMatrix.set(stack)
        frameWorldModelPrepared = true
        renderWorld(context.tickDelta, context.viewMatrix, context.projMatrix, stack, offscreenStateGuard)
        entity.lastRenderPos = entity.pos
    }

    private fun renderOffscreen(context: RenderFrameContext, output: CooPipelineOutputPort) {
        val node = requireNotNull(pipelineRuntime.compiledPipeline.nodes.firstOrNull { it.name == output.node }) {
            "Pipeline output '${output.node}.${output.name}' has no source node"
        }
        require(node.kind == CooPipelineNodeKind.WORLD) {
            "Only world pipeline outputs can request RenderEntity geometry"
        }
        val stack = Matrix4fStack(16)
        if (frameWorldModelPrepared) {
            stack.set(frameWorldModelMatrix)
        } else {
            stack.set(RenderUtil.buildModelMatrix(entity, context.tickDelta))
        }
        offscreenStateGuard.use { renderState ->
            renderer.render(
                RenderInput(
                    entity = entity,
                    tickDelta = context.tickDelta,
                    viewMatrix = context.viewMatrix,
                    projMatrix = context.projMatrix,
                    modelMatrix = stack,
                    renderState = renderState,
                    pipeline = renderer.pipeline,
                    node = node,
                    phase = RenderPhase.OFFSCREEN,
                    output = output
                )
            )
        }
    }

    /** @return 共享 renderer 的缓存 runtime；旧式实体 renderer 使用独立 runtime */
    private fun resolvePipelineRuntime(): RenderEntityPipelineRuntime {
        return if (renderer === entity) {
            RenderEntityPipelineRuntime(renderer.pipeline)
        } else {
            RenderEntityPipelineRuntimeCache.get(renderer)
        }
    }
}
