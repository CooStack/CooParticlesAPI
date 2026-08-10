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

    internal fun reinitialize() {
        pipelineRuntime = resolvePipelineRuntime()
        compiledPostEffect = pipelineRuntime.compiledPostEffect
    }

    internal fun updateFrom(profile: RenderEntity) {
        entity.loadProfileFromEntity(profile)
    }

    internal fun render(
        tickDelta: Float,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        modelMatrix: Matrix4fStack,
        stateGuard: RenderStateGuard
    ) {
        if (consumeIrisWorldPass()) return
        renderWorld(tickDelta, viewMatrix, projMatrix, modelMatrix, stateGuard)
    }

    internal fun renderIrisWorldPass(
        tickDelta: Float,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        modelMatrix: Matrix4fStack,
        stateGuard: RenderStateGuard
    ) {
        if (!hasWorldPass()) return
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

    internal fun beginWorldRenderFrame() {
        irisWorldPassSubmitted = false
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

    internal fun markRemoved() {
        entity.canceled = true
    }

    internal fun collectEffects(context: RenderFrameContext, collector: RenderEffectCollector) {
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
                    attachments = pipelineRuntime.worldAttachments
                ) { output ->
                    renderOffscreen(context, output)
                }
            )
        }
        BuiltinRenderEffectDescriptors.collectEntity(entity, context, collector)
    }

    private fun renderOffscreen(context: RenderFrameContext, output: CooPipelineOutputPort) {
        val node = requireNotNull(pipelineRuntime.compiledPipeline.nodes.firstOrNull { it.name == output.node }) {
            "Pipeline output '${output.node}.${output.name}' has no source node"
        }
        require(node.kind == CooPipelineNodeKind.WORLD) {
            "Only world pipeline outputs can request RenderEntity geometry"
        }
        val stack = Matrix4fStack(16)
        stack.set(RenderUtil.buildModelMatrix(entity, context.tickDelta))
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
