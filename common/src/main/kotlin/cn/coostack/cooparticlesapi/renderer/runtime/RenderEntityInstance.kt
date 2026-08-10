package cn.coostack.cooparticlesapi.renderer.runtime

import cn.coostack.cooparticlesapi.compat.IrisCompat
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameStage
import cn.coostack.cooparticlesapi.renderer.client.RenderUtil
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectCollector
import cn.coostack.cooparticlesapi.renderer.effects.builtin.BuiltinRenderEffectDescriptors
import cn.coostack.cooparticlesapi.renderer.pipeline.CooCompiledAttachment
import cn.coostack.cooparticlesapi.renderer.pipeline.CooCompiledPostEffect
import cn.coostack.cooparticlesapi.renderer.pipeline.CooCompiledPipeline
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineCompiler
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineInputPort
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineNode
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineNodeKind
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineOutputPort
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelinePostEffectCompiler
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineRuntimeEffect
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineTarget
import cn.coostack.cooparticlesapi.renderer.state.RenderStateGuard
import org.joml.Matrix4f
import org.joml.Matrix4fStack

/** 单个 RenderEntity 的客户端运行时实例。 */
class RenderEntityInstance<T : RenderEntity>(
    val entity: T,
    val renderer: RenderEntityRenderer<T>
) {
    private var compiledPipeline: CooCompiledPipeline = CooPipelineCompiler.compile(renderer.pipeline)
    private var compiledPostEffect: CooCompiledPostEffect? = compilePostEffect()
    private var worldNodes: List<CooPipelineNode> = collectWorldNodes()
    private var worldAttachments: List<CooCompiledAttachment> = collectWorldAttachments()
    private val offscreenStateGuard = RenderStateGuard()
    private var irisWorldPassSubmitted = false

    internal fun reinitialize() {
        compiledPipeline = CooPipelineCompiler.compile(renderer.pipeline)
        compiledPostEffect = compilePostEffect()
        worldNodes = collectWorldNodes()
        worldAttachments = collectWorldAttachments()
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
        worldNodes.forEach { node ->
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
        return RenderFrameStage.WORLD_PASS in compiledPipeline.stages && worldNodes.isNotEmpty()
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
                sourceId = entity.uuid.toString()
            )
            collector.submit(
                CooPipelineRuntimeEffect.descriptor(
                    owner = entity.uuid.toString(),
                    compiled = compiledPipeline,
                    postEffect = instance,
                    attachments = worldAttachments
                ) { output ->
                    renderOffscreen(context, output)
                }
            )
        }
        BuiltinRenderEffectDescriptors.collectEntity(entity, context, collector)
    }

    private fun renderOffscreen(context: RenderFrameContext, output: CooPipelineOutputPort) {
        val node = requireNotNull(compiledPipeline.nodes.firstOrNull { it.name == output.node }) {
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

    private fun compilePostEffect(): CooCompiledPostEffect? {
        return CooPipelinePostEffectCompiler.compile(renderer.pipeline, entity)
    }

    private fun collectWorldNodes(): List<CooPipelineNode> {
        val worldNodeNames = compiledPipeline.lines.mapNotNull { line ->
            val output = line.output as? CooPipelineOutputPort ?: return@mapNotNull null
            if (line.input !is CooPipelineTarget.World) {
                return@mapNotNull null
            }
            output.node
        }.toSet()
        return compiledPipeline.nodes.filter { node ->
            node.kind == CooPipelineNodeKind.WORLD && node.name in worldNodeNames
        }
    }

    private fun collectWorldAttachments(): List<CooCompiledAttachment> {
        val nodes = compiledPipeline.nodes.associateBy { it.name }
        return compiledPipeline.lines.mapNotNull { line ->
            val output = line.output as? CooPipelineOutputPort ?: return@mapNotNull null
            val input = line.input as? CooPipelineInputPort ?: return@mapNotNull null
            if (nodes.getValue(output.node).kind != CooPipelineNodeKind.WORLD ||
                nodes.getValue(input.node).kind == CooPipelineNodeKind.WORLD
            ) {
                return@mapNotNull null
            }
            compiledPipeline.attachment(output)
        }.distinctBy { it.output }
    }
}
