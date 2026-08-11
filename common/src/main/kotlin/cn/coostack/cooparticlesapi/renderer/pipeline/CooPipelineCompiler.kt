package cn.coostack.cooparticlesapi.renderer.pipeline

import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameStage
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneTargets
import net.minecraft.resources.ResourceLocation
import java.util.PriorityQueue

/** Pipeline output 在编译后对应的 FBO attachment。 */
internal data class CooCompiledAttachment(
    val output: CooPipelineOutputPort,
    val framebuffer: ResourceLocation,
    val firstUse: Int,
    val lastUse: Int
)

/** Pipeline DAG 编译后的执行与资源快照。 */
internal data class CooCompiledPipeline(
    val stages: Set<RenderFrameStage>,
    val sceneTargets: Set<ResourceLocation>,
    val requiredCapabilities: Set<RenderBackendCapability>,
    val optionalCapabilities: Set<RenderBackendCapability>,
    val nodes: List<CooPipelineNode>,
    val lines: List<CooPipelineLine>,
    val attachments: List<CooCompiledAttachment>
) {
    /**
     * 把输入对象加入 `CooCompiledPipeline` 的 `attachment` 管理范围，后续查询、构建或绘制会使用该绑定。
     *
     * 示例：`attachment(output = output)`。
     *
     * @param output 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun attachment(output: CooPipelineOutputPort): CooCompiledAttachment? {
        return attachments.firstOrNull { it.output == output }
    }
}

/** 把 typed port/line 图编译成稳定拓扑和 FBO attachment 生命周期。 */
internal object CooPipelineCompiler {
    /**
     * 初始化或准备 `CooPipelineCompiler` 的 `compile` 阶段，使后续渲染调用可以使用相关资源。
     *
     * 示例：`compile(pipeline = pipeline)`。
     *
     * @param pipeline 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun compile(pipeline: CooRenderPipeline<*>): CooCompiledPipeline {
        val orderedNodes = sortNodes(pipeline.nodes, pipeline.lines)
        val nodeIndex = orderedNodes.mapIndexed { index, node -> node.name to index }.toMap()
        val sceneTargets = linkedSetOf<ResourceLocation>()
        val required = linkedSetOf<RenderBackendCapability>()
        val optional = linkedSetOf<RenderBackendCapability>()

        validateTextureContracts(pipeline)

        pipeline.lines.forEach { line ->
            val input = line.input
            if (input is CooPipelineInputPort) {
                includeSource(line.output, input.optional, sceneTargets, required, optional)
            } else if (input is CooPipelineTarget) {
                includeTarget(input, sceneTargets, required)
            }
        }
        if (orderedNodes.any { it.kind != CooPipelineNodeKind.WORLD }) {
            required += RenderBackendCapability.FINAL_FRAME_POST
        }
        if (pipeline.lines.any { line ->
                val output = line.output as? CooPipelineOutputPort ?: return@any false
                val input = line.input as? CooPipelineInputPort ?: return@any false
                orderedNodes.first { it.name == output.node }.kind == CooPipelineNodeKind.WORLD &&
                    orderedNodes.first { it.name == input.node }.kind != CooPipelineNodeKind.WORLD
            }
        ) {
            required += RenderBackendCapability.SAFE_WORLD_COMPOSITE
        }

        val attachments = compileAttachments(pipeline, orderedNodes, nodeIndex, sceneTargets)
        return CooCompiledPipeline(
            stages = pipeline.stages,
            sceneTargets = sceneTargets,
            requiredCapabilities = required,
            optionalCapabilities = optional,
            nodes = orderedNodes,
            lines = pipeline.lines,
            attachments = attachments
        )
    }

    /** 校验能够在构建阶段确定的节点输出和 sampler 输入格式与 mip 层数。 */
    private fun validateTextureContracts(pipeline: CooRenderPipeline<*>) {
        val namedWriters = pipeline.lines.mapNotNull { line ->
            val output = line.output as? CooPipelineOutputPort ?: return@mapNotNull null
            val target = line.input as? CooPipelineTarget.FramebufferColor ?: return@mapNotNull null
            (target.target to target.attachment) to output
        }.toMap()
        pipeline.lines.forEach { line ->
            val input = line.input as? CooPipelineInputPort ?: return@forEach
            val source = when (val output = line.output) {
                is CooPipelineOutputPort -> output
                is CooPipelineTextureSource.FramebufferColor -> namedWriters[output.target to output.attachment]
                else -> null
            } ?: return@forEach
            input.expectedFormat?.let { expected ->
                require(source.format == expected) {
                    "Pipeline input '${input.node}.${input.sampler}' expects $expected but receives ${source.format}"
                }
            }
            require(source.mipLevels >= input.minimumMipLevels) {
                "Pipeline input '${input.node}.${input.sampler}' requires ${input.minimumMipLevels} mip levels " +
                    "but receives ${source.mipLevels}"
            }
        }
    }

    private fun includeSource(
        source: CooPipelineTextureSource,
        optional: Boolean,
        targets: MutableSet<ResourceLocation>,
        required: MutableSet<RenderBackendCapability>,
        optionalCapabilities: MutableSet<RenderBackendCapability>
    ) {
        /**
         * 执行 `CooPipelineCompiler` 定义的 `capability` 操作；输入和返回值用于该组件当前的渲染职责。
         *
         * 示例：`capability(value = value)`。
         *
         * @param value 当前操作需要的输入值；其语义由方法名和所属组件共同限定
         */
        fun capability(value: RenderBackendCapability) {
            if (optional) optionalCapabilities += value else required += value
        }
        when (source) {
            CooPipelineTextureSource.SceneColor -> {
                targets += RenderSceneTargets.SCENE_COLOR
                capability(RenderBackendCapability.SCENE_COLOR_COPY)
            }
            CooPipelineTextureSource.SceneDepth -> {
                targets += RenderSceneTargets.SCENE_DEPTH
                capability(RenderBackendCapability.SCENE_DEPTH_READ)
            }
            is CooPipelineTextureSource.FramebufferColor -> targets += source.target
            CooPipelineTextureSource.Mask -> targets += RenderSceneTargets.MASK
            CooPipelineTextureSource.Temporary -> targets += RenderSceneTargets.TEMPORARY
            CooPipelineTextureSource.Bloom -> targets += RenderSceneTargets.BLOOM
            is CooPipelineOutputPort,
            is CooPipelineTextureSource.Texture,
            is CooPipelineTextureSource.Parameter,
            CooPipelineTextureSource.BlockAtlas -> Unit
        }
    }

    private fun includeTarget(
        target: CooPipelineTarget,
        targets: MutableSet<ResourceLocation>,
        required: MutableSet<RenderBackendCapability>
    ) {
        when (target) {
            CooPipelineTarget.World -> Unit
            CooPipelineTarget.FinalScreen -> {
                targets += RenderSceneTargets.POST
                required += RenderBackendCapability.FINAL_FRAME_POST
            }
            CooPipelineTarget.Mask -> targets += RenderSceneTargets.MASK
            CooPipelineTarget.Temporary -> targets += RenderSceneTargets.TEMPORARY
            CooPipelineTarget.Bloom -> targets += RenderSceneTargets.BLOOM
            is CooPipelineTarget.FramebufferColor -> targets += target.target
        }
    }

    private fun compileAttachments(
        pipeline: CooRenderPipeline<*>,
        orderedNodes: List<CooPipelineNode>,
        nodeIndex: Map<String, Int>,
        sceneTargets: MutableSet<ResourceLocation>
    ): List<CooCompiledAttachment> {
        val lastPipelineStep = orderedNodes.lastIndex.coerceAtLeast(0)
        return orderedNodes.flatMap { node ->
            val nodeTarget = resolveNodeFramebuffer(pipeline, node)
            node.outputs.mapNotNull { output ->
                val directConsumers = pipeline.lines.filter {
                    it.output == output && it.input is CooPipelineInputPort
                }
                val targetLines = pipeline.lines.filter { it.output == output && it.input is CooPipelineTarget }
                val namedConsumers = targetLines.mapNotNull { line ->
                    line.input as? CooPipelineTarget.FramebufferColor
                }.flatMap { target ->
                    pipeline.lines.filter { line ->
                        val source = line.output as? CooPipelineTextureSource.FramebufferColor
                        source?.target == target.target &&
                            source.attachment == target.attachment &&
                            line.input is CooPipelineInputPort
                    }
                }
                val consumers = directConsumers + namedConsumers
                val hasOffscreenTarget = targetLines.any { it.input !is CooPipelineTarget.World && it.input !is CooPipelineTarget.FinalScreen }
                val requiresFramebuffer = node.kind == CooPipelineNodeKind.PING_PONG ||
                    node.kind != CooPipelineNodeKind.WORLD &&
                    targetLines.none { it.input is CooPipelineTarget.FinalScreen } ||
                    consumers.isNotEmpty() || hasOffscreenTarget ||
                    output.semantic != CooPipelineOutputSemantic.COLOR
                if (!requiresFramebuffer) return@mapNotNull null

                val framebuffer = requireNotNull(nodeTarget) {
                    "Node '${node.name}' output '${output.name}' requires an FBO but no framebuffer was compiled"
                }
                sceneTargets += framebuffer
                CooCompiledAttachment(
                    output = output,
                    framebuffer = framebuffer,
                    firstUse = nodeIndex.getValue(output.node),
                    lastUse = maxOf(
                        consumers.maxOfOrNull { line ->
                            nodeIndex.getValue((line.input as CooPipelineInputPort).node)
                        } ?: nodeIndex.getValue(output.node),
                        if (targetLines.any { it.input !is CooPipelineTarget.World }) {
                            lastPipelineStep
                        } else {
                            nodeIndex.getValue(output.node)
                        }
                    )
                )
            }
        }
    }

    private fun resolveNodeFramebuffer(
        pipeline: CooRenderPipeline<*>,
        node: CooPipelineNode
    ): ResourceLocation? {
        val nodeLines = pipeline.lines.filter { line ->
            val output = line.output as? CooPipelineOutputPort
            output?.node == node.name
        }
        nodeLines.forEach { line ->
            val output = line.output as CooPipelineOutputPort
            val target = line.input as? CooPipelineTarget.FramebufferColor ?: return@forEach
            require(output.attachment == target.attachment) {
                "Line '${output.node}.${output.name}' cannot write attachment ${target.attachment}; " +
                    "the output is attachment ${output.attachment}"
            }
        }
        val explicitTargets = nodeLines.mapNotNull { line ->
            when (val target = line.input) {
                CooPipelineTarget.Mask -> RenderSceneTargets.MASK
                CooPipelineTarget.Temporary -> RenderSceneTargets.TEMPORARY
                CooPipelineTarget.Bloom -> RenderSceneTargets.BLOOM
                is CooPipelineTarget.FramebufferColor -> target.target
                else -> null
            }
        }.distinct()
        require(explicitTargets.size <= 1) {
            "Node '${node.name}' cannot write one shader invocation to multiple FBOs: $explicitTargets"
        }
        val finalScreenLines = nodeLines.filter { it.input is CooPipelineTarget.FinalScreen }
        if (finalScreenLines.isNotEmpty()) {
            require(finalScreenLines.size == 1) {
                "Node '${node.name}' must have exactly one line to the final screen"
            }
            val screenOutput = finalScreenLines.single().output as CooPipelineOutputPort
            require(
                screenOutput.semantic == CooPipelineOutputSemantic.COLOR && screenOutput.attachment == 0
            ) {
                "Node '${node.name}' can only connect color attachment 0 to the final screen"
            }
            require(nodeLines.none { it.input is CooPipelineInputPort }) {
                "Node '${node.name}' is connected to the final screen and cannot also feed another node"
            }
            require(nodeLines.none { line ->
                line.input is CooPipelineTarget && line.input !is CooPipelineTarget.FinalScreen
            }) {
                "Node '${node.name}' is connected to the final screen and cannot also write another target"
            }
        }
        if (explicitTargets.isNotEmpty()) return explicitTargets.single()
        if (node.outputs.none { output ->
                nodeLines.any { it.output == output && it.input !is CooPipelineTarget.World }
            }
        ) {
            return null
        }
        val safeNode = node.name.map { character ->
            if (character.isLowerCase() || character.isDigit() || character == '_' || character == '-' || character == '.') {
                character
            } else {
                '_'
            }
        }.joinToString("")
        return ResourceLocation.fromNamespaceAndPath(
            pipeline.id.namespace,
            "pipeline/${pipeline.id.path}/$safeNode"
        )
    }

    private fun sortNodes(nodes: List<CooPipelineNode>, lines: List<CooPipelineLine>): List<CooPipelineNode> {
        require(nodes.map(CooPipelineNode::name).toSet().size == nodes.size) {
            "Pipeline node names must be unique"
        }
        val byName = nodes.associateBy(CooPipelineNode::name)
        val outgoing = nodes.associateWith { linkedSetOf<CooPipelineNode>() }
        val indegree = nodes.associateWith { 0 }.toMutableMap()
        val namedWriters = lines.mapNotNull { line ->
            val output = line.output as? CooPipelineOutputPort ?: return@mapNotNull null
            val target = line.input as? CooPipelineTarget.FramebufferColor ?: return@mapNotNull null
            (target.target to target.attachment) to output
        }.groupBy({ it.first }, { it.second })
        lines.forEach { line ->
            val input = line.input as? CooPipelineInputPort ?: return@forEach
            val targetNode = requireNotNull(byName[input.node]) { "Unknown target node '${input.node}'" }
            val sourceOutput = when (val output = line.output) {
                is CooPipelineOutputPort -> output
                is CooPipelineTextureSource.FramebufferColor -> {
                    val writers = namedWriters[output.target to output.attachment].orEmpty()
                    require(writers.size <= 1) {
                        "Named framebuffer '${output.target}' attachment ${output.attachment} has multiple writers"
                    }
                    writers.singleOrNull() ?: return@forEach
                }
                else -> return@forEach
            }
            val sourceNode = requireNotNull(byName[sourceOutput.node]) {
                "Unknown source node '${sourceOutput.node}'"
            }
            if (outgoing.getValue(sourceNode).add(targetNode)) {
                indegree[targetNode] = indegree.getValue(targetNode) + 1
            }
        }
        val ready = PriorityQueue(compareBy<CooPipelineNode>({ it.order }, { it.sequence }))
        indegree.filterValues { it == 0 }.keys.forEach(ready::add)
        val result = ArrayList<CooPipelineNode>(nodes.size)
        while (ready.isNotEmpty()) {
            val node = ready.remove()
            result += node
            outgoing.getValue(node).forEach { next ->
                val remaining = indegree.getValue(next) - 1
                indegree[next] = remaining
                if (remaining == 0) ready += next
            }
        }
        require(result.size == nodes.size) { "Pipeline line graph contains a cycle" }
        return result
    }
}
