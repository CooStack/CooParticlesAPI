package cn.coostack.cooparticlesapi.renderer.runtime

import cn.coostack.cooparticlesapi.renderer.pipeline.CooCompiledAttachment
import cn.coostack.cooparticlesapi.renderer.pipeline.CooCompiledPipeline
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineCompiler
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineInputPort
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineNode
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineNodeKind
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineOutputPort
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelinePostEffectCompiler
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineTarget
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineTextureSource
import cn.coostack.cooparticlesapi.renderer.pipeline.CooRenderPipeline
import java.util.WeakHashMap

/**
 * 同一个不可变 pipeline 在多个 RenderEntity 实例间共享的静态编译结果。
 *
 * @param pipeline 需要编译一次并复用结果的 renderer pipeline
 */
internal class RenderEntityPipelineRuntime(pipeline: CooRenderPipeline<*>) {
    /** Pipeline 的拓扑、渲染阶段和 attachment 生命周期快照。 */
    val compiledPipeline: CooCompiledPipeline = CooPipelineCompiler.compile(pipeline)

    /** 全部实体共享的全屏 pass 链和 shader 绑定定义。 */
    val compiledPostEffect = CooPipelinePostEffectCompiler.compile(pipeline, compiledPipeline)

    /** 直接写入世界渲染目标的节点。 */
    val worldNodes: List<CooPipelineNode> = collectWorldNodes(compiledPipeline)

    /** 世界节点向后处理节点提供的 attachment。 */
    val worldAttachments: List<CooCompiledAttachment> = collectWorldAttachments(compiledPipeline)

    /** @return 按 pipeline 连线顺序筛出的世界渲染节点 */
    private fun collectWorldNodes(compiled: CooCompiledPipeline): List<CooPipelineNode> {
        val worldNodeNames = compiled.lines.mapNotNull { line ->
            val output = line.output as? CooPipelineOutputPort ?: return@mapNotNull null
            if (line.input !is CooPipelineTarget.World) {
                return@mapNotNull null
            }
            output.node
        }.toSet()
        return compiled.nodes.filter { node ->
            node.kind == CooPipelineNodeKind.WORLD && node.name in worldNodeNames
        }
    }

    /** @return 世界节点输出并由全屏节点消费的 attachment */
    private fun collectWorldAttachments(compiled: CooCompiledPipeline): List<CooCompiledAttachment> {
        val nodes = compiled.nodes.associateBy { it.name }
        return compiled.attachments.filter { attachment ->
            if (nodes.getValue(attachment.output.node).kind != CooPipelineNodeKind.WORLD) {
                return@filter false
            }
            compiled.lines.any { line ->
                val input = line.input as? CooPipelineInputPort ?: return@any false
                if (nodes.getValue(input.node).kind == CooPipelineNodeKind.WORLD) {
                    return@any false
                }
                line.output == attachment.output ||
                    line.output == CooPipelineTextureSource.FramebufferColor(
                        attachment.framebuffer,
                        attachment.output.attachment
                    )
            }
        }
    }
}

/** 按 pipeline 对象身份缓存静态编译结果；弱键允许已替换的 renderer pipeline 被回收。 */
internal object RenderEntityPipelineRuntimeCache {
    /** 弱键缓存不会延长已失效 pipeline 的生命周期。 */
    private val runtimes = WeakHashMap<CooRenderPipeline<*>, RenderEntityPipelineRuntime>()

    /**
     * 返回 renderer 所属 pipeline 的共享编译结果。
     *
     * @param renderer 当前实体使用的 renderer
     * @return 同一 pipeline 对象对应的唯一 runtime
     */
    @Synchronized
    fun get(renderer: RenderEntityRenderer<*>): RenderEntityPipelineRuntime {
        val pipeline = renderer.pipeline
        return runtimes.getOrPut(pipeline) {
            RenderEntityPipelineRuntime(pipeline)
        }
    }

    /**
     * 使全部共享编译结果失效。
     *
     * Shader 资源重载后调用；后续实体重新初始化时，每个 pipeline 只会重新编译一次。
     */
    @Synchronized
    fun invalidate() {
        runtimes.clear()
    }
}
