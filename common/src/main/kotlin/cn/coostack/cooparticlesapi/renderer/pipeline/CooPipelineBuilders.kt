package cn.coostack.cooparticlesapi.renderer.pipeline

import net.minecraft.resources.ResourceLocation

class CooPipelineNodeBuilder<T : Any> internal constructor(
    private val name: String,
    private val kind: CooPipelineNodeKind
) {
    private var coreShader: ResourceLocation? = null
    private var vertexShader: ResourceLocation? = null
    private var fragmentShader: ResourceLocation? = null
    private val inputs = LinkedHashMap<String, CooPipelineInputPort>()
    private val sources = LinkedHashMap<String, CooPipelineTextureSource>()
    private val targets = ArrayList<CooPipelineTarget>()
    private val uniforms = LinkedHashMap<String, CooUniformProvider<Any>>()
    private var pingPongIterations: Int? = null
    private var pingPongFeedbackSampler: String? = null
    private val iterationUniforms = LinkedHashMap<String, CooIterationUniformProvider>()
    private var colorAttachmentCount = 1
    private var hasMaskOutput = false
    private var order = 0

    fun shader(shader: ResourceLocation) = apply {
        coreShader = shader
    }

    fun vertex(shader: ResourceLocation) = apply {
        vertexShader = shader
    }

    fun fragment(shader: ResourceLocation) = apply {
        fragmentShader = shader
    }

    fun order(value: Int) = apply {
        order = value
    }

    /** 声明一个尚未连线的 sampler 输入端口。 */
    fun input(sampler: String, optional: Boolean = false, textureSlot: Int = inputs.size) = apply {
        require(sampler !in inputs) { "Node '$name' already declares input '$sampler'" }
        inputs[sampler] = CooPipelineInputPort(name, sampler, optional, textureSlot)
    }

    /** 声明端口并直接把一个外部资源连到它。 */
    fun input(
        sampler: String,
        source: CooPipelineTextureSource,
        optional: Boolean = false,
        textureSlot: Int = inputs.size
    ) = apply {
        input(sampler, optional, textureSlot)
        sources[sampler] = source
    }

    fun inputTexture(
        sampler: String,
        texture: ResourceLocation,
        optional: Boolean = false,
        textureSlot: Int = inputs.size
    ) = input(sampler, CooPipelineTextureSource.Texture(texture), optional, textureSlot)

    fun inputBlockAtlas(
        sampler: String = "BaseSampler",
        optional: Boolean = false,
        textureSlot: Int = inputs.size
    ) = input(sampler, CooPipelineTextureSource.BlockAtlas, optional, textureSlot)

    fun inputSceneColor(
        sampler: String = "SceneColor",
        optional: Boolean = false,
        textureSlot: Int = inputs.size
    ) = input(sampler, CooPipelineTextureSource.SceneColor, optional, textureSlot)

    fun inputSceneDepth(
        sampler: String = "SceneDepth",
        optional: Boolean = false,
        textureSlot: Int = inputs.size
    ) = input(sampler, CooPipelineTextureSource.SceneDepth, optional, textureSlot)

    fun inputFramebuffer(
        sampler: String,
        target: ResourceLocation,
        attachment: Int = 0,
        optional: Boolean = false,
        textureSlot: Int = inputs.size
    ) = input(
        sampler,
        CooPipelineTextureSource.FramebufferColor(target, attachment),
        optional,
        textureSlot
    )

    fun inputMask(
        sampler: String = "Mask",
        optional: Boolean = false,
        textureSlot: Int = inputs.size
    ) = input(sampler, CooPipelineTextureSource.Mask, optional, textureSlot)

    fun inputTemporary(
        sampler: String = "Temporary",
        optional: Boolean = false,
        textureSlot: Int = inputs.size
    ) = input(sampler, CooPipelineTextureSource.Temporary, optional, textureSlot)

    fun inputBloom(
        sampler: String = "Bloom",
        optional: Boolean = false,
        textureSlot: Int = inputs.size
    ) = input(sampler, CooPipelineTextureSource.Bloom, optional, textureSlot)

    /** 当前节点写出多少个颜色 attachment。 */
    fun colorAttachments(count: Int) = apply {
        require(count > 0) { "Node '$name' must have at least one color attachment" }
        colorAttachmentCount = count
    }

    /** 为 world 节点增加独立 mask 输出端口。 */
    fun maskOutput() = apply {
        require(kind == CooPipelineNodeKind.WORLD) { "Only world nodes can declare a mask output" }
        hasMaskOutput = true
    }

    fun outputToWorld() = apply { targets += CooPipelineTarget.World }
    fun outputToScreen() = apply { targets += CooPipelineTarget.FinalScreen }
    fun outputToMask() = apply { targets += CooPipelineTarget.Mask }
    fun outputToTemporary() = apply { targets += CooPipelineTarget.Temporary }
    fun outputToBloom() = apply { targets += CooPipelineTarget.Bloom }
    fun outputToFramebuffer(target: ResourceLocation, attachment: Int = 0) = apply {
        targets += CooPipelineTarget.FramebufferColor(target, attachment)
    }

    fun uniform(name: String, value: Float) = apply {
        setUniform(name) { CooUniformValue.FloatValue(value) }
    }

    fun uniform(name: String, value: CooUniformValue) = apply {
        setUniform(name) { value }
    }

    fun <R : Any> uniform(name: String, provider: (R) -> Float) = apply {
        setUniform(name) { subject ->
            @Suppress("UNCHECKED_CAST")
            CooUniformValue.FloatValue(provider(subject as R))
        }
    }

    fun <R : Any> uniformValue(name: String, provider: CooUniformProvider<R>) = apply {
        setUniform(name) { subject ->
            @Suppress("UNCHECKED_CAST")
            provider.resolve(subject as R)
        }
    }

    fun alternate(name: String, ping: Float, pong: Float) = alternate(
        name,
        CooUniformValue.FloatValue(ping),
        CooUniformValue.FloatValue(pong)
    )

    fun alternate(name: String, ping: Int, pong: Int) = alternate(
        name,
        CooUniformValue.IntValue(ping),
        CooUniformValue.IntValue(pong)
    )

    fun alternate(name: String, ping: CooUniformValue, pong: CooUniformValue) = apply {
        require(kind == CooPipelineNodeKind.PING_PONG) {
            "Alternating uniforms are only available on ping-pong nodes"
        }
        setIterationUniform(name) { iteration ->
            if (iteration.isPing) ping else pong
        }
    }

    fun iterationUniform(name: String, provider: (CooPipelineIteration) -> CooUniformValue) = apply {
        require(kind == CooPipelineNodeKind.PING_PONG) {
            "Iteration uniforms are only available on ping-pong nodes"
        }
        setIterationUniform(name) { iteration -> provider(iteration) }
    }

    /** 存储节点 uniform provider，并让调用点直接传入 lambda。 */
    private fun setUniform(name: String, provider: CooUniformProvider<Any>) {
        uniforms[name] = provider
    }

    /** 存储迭代 uniform provider，并让调用点直接传入 lambda。 */
    private fun setIterationUniform(name: String, provider: CooIterationUniformProvider) {
        iterationUniforms[name] = provider
    }

    internal fun pingPong(iterations: Int, feedbackSampler: String) {
        require(kind == CooPipelineNodeKind.PING_PONG)
        require(iterations > 0) { "Ping-pong node '$name' must execute at least once" }
        pingPongIterations = iterations
        pingPongFeedbackSampler = feedbackSampler
        input(feedbackSampler)
    }

    internal fun ensureInput(
        sampler: String,
        source: CooPipelineTextureSource,
        optional: Boolean = false,
        textureSlot: Int = inputs.size
    ) {
        if (sampler !in inputs) {
            input(sampler, source, optional, textureSlot)
        }
    }

    internal fun build(sequence: Int): CooNodeBuildResult {
        val shader = when {
            coreShader != null -> {
                require(vertexShader == null && fragmentShader == null) {
                    "Node '$name' cannot combine a core shader with vertex/fragment stages"
                }
                CooPipelineShader.Core(requireNotNull(coreShader))
            }
            fragmentShader != null -> CooPipelineShader.Stages(vertexShader, requireNotNull(fragmentShader))
            else -> null
        }
        if (kind != CooPipelineNodeKind.WORLD) {
            require(shader is CooPipelineShader.Stages) { "Fullscreen node '$name' requires a fragment shader" }
        }
        val pingPong = if (kind == CooPipelineNodeKind.PING_PONG) {
            val feedbackSampler = requireNotNull(pingPongFeedbackSampler)
            require(feedbackSampler in inputs) {
                "Ping-pong node '$name' does not declare feedback sampler '$feedbackSampler'"
            }
            require(iterationUniforms.keys.none { it in uniforms }) {
                "Ping-pong node '$name' declares the same uniform as static and iteration-based"
            }
            CooPingPongExecution(
                iterations = requireNotNull(pingPongIterations),
                feedbackSampler = feedbackSampler,
                uniforms = iterationUniforms.toMap()
            )
        } else {
            null
        }
        val outputs = buildList {
            repeat(colorAttachmentCount) { attachment ->
                add(
                    CooPipelineOutputPort(
                        node = name,
                        name = if (attachment == 0) "Color" else "Color$attachment",
                        semantic = CooPipelineOutputSemantic.COLOR,
                        attachment = attachment
                    )
                )
            }
            if (hasMaskOutput) {
                add(CooPipelineOutputPort(name, "Mask", CooPipelineOutputSemantic.MASK, colorAttachmentCount))
            }
        }
        return CooNodeBuildResult(
            node = CooPipelineNode(
                name = name,
                kind = kind,
                shader = shader,
                inputs = inputs.values.toList(),
                outputs = outputs,
                uniforms = uniforms,
                pingPong = pingPong,
                order = order,
                sequence = sequence
            ),
            sources = sources.toMap(),
            targets = targets.toList()
        )
    }
}

internal data class CooNodeBuildResult(
    val node: CooPipelineNode,
    val sources: Map<String, CooPipelineTextureSource>,
    val targets: List<CooPipelineTarget>
)

class CooRenderPipelineBuilder<T : Any> internal constructor(
    private val id: ResourceLocation,
    private val domain: CooPipelineDomain
) {
    private var terrainLayer = CooTerrainLayer.INHERIT
    private var effectUvMode = CooEffectUvMode.BASE_UV
    private val nodes = ArrayList<CooPipelineNode>()
    private val lines = ArrayList<CooPipelineLine>()
    private val parameters = LinkedHashMap<String, MutableList<CooPipelineParameterBinding>>()
    private val implicitWorld = CooPipelineNodeBuilder<T>("world", CooPipelineNodeKind.WORLD)
    private var implicitWorldUsed = false
    private var sequence = 0

    fun terrainLayer(layer: CooTerrainLayer) = apply { terrainLayer = layer }
    fun effectUv(mode: CooEffectUvMode) = apply { effectUvMode = mode }

    fun shader(shader: ResourceLocation) = apply {
        implicitWorldUsed = true
        implicitWorld.shader(shader)
    }

    fun inputTexture(sampler: String, texture: ResourceLocation, textureSlot: Int = 0) = apply {
        implicitWorldUsed = true
        implicitWorld.inputTexture(sampler, texture, textureSlot = textureSlot)
    }

    fun inputBlockAtlas(sampler: String = "BaseSampler", textureSlot: Int = 0) = apply {
        implicitWorldUsed = true
        implicitWorld.inputBlockAtlas(sampler, textureSlot = textureSlot)
    }

    fun inputSceneColor(sampler: String = "SceneColor", optional: Boolean = false, textureSlot: Int = 0) = apply {
        implicitWorldUsed = true
        implicitWorld.inputSceneColor(sampler, optional, textureSlot)
    }

    fun inputSceneDepth(sampler: String = "SceneDepth", optional: Boolean = false, textureSlot: Int = 1) = apply {
        implicitWorldUsed = true
        implicitWorld.inputSceneDepth(sampler, optional, textureSlot)
    }

    fun uniform(name: String, value: Float) = apply {
        implicitWorldUsed = true
        implicitWorld.uniform(name, value)
    }

    fun uniform(name: String, value: CooUniformValue) = apply {
        implicitWorldUsed = true
        implicitWorld.uniform(name, value)
    }

    fun world(name: String = "world", block: CooPipelineNodeBuilder<T>.() -> Unit = {}): CooPipelineNode {
        return addNode(CooPipelineNodeBuilder<T>(name, CooPipelineNodeKind.WORLD).apply(block))
    }

    fun pass(name: String, block: CooPipelineNodeBuilder<T>.() -> Unit): CooPipelineNode {
        return addNode(CooPipelineNodeBuilder<T>(name, CooPipelineNodeKind.FULLSCREEN).apply(block))
    }

    fun pingPong(
        name: String,
        iterations: Int,
        feedbackSampler: String = "Input",
        block: CooPipelineNodeBuilder<T>.() -> Unit
    ): CooPipelineNode {
        val builder = CooPipelineNodeBuilder<T>(name, CooPipelineNodeKind.PING_PONG)
        builder.pingPong(iterations, feedbackSampler)
        return addNode(builder.apply(block))
    }

    internal fun addPreparedNode(builder: CooPipelineNodeBuilder<T>): CooPipelineNode {
        return addNode(builder)
    }

    /** 建立一条纹理/FBO attachment 到 sampler 或 graph 输出端口的 line。 */
    fun line(output: CooPipelineTextureSource, input: CooPipelineLineInput) = apply {
        require(input !is CooPipelineTarget || output is CooPipelineOutputPort) {
            "Only a node FBO output can be connected to a pipeline target"
        }
        lines += CooPipelineLine(output, input)
    }

    /**
     * 按 fragment output attachment 和纹理单元连接两个节点。
     *
     * `fromChannel` 对应源 fragment shader 的 `layout(location = n)`，
     * `toChannel` 对应目标 sampler 声明的 `textureSlot = n`。
     *
     * 示例：`line(source, 1, composite, 0)` 把 `layout(location = 1)` 接到纹理单元 0。
     *
     * @param from 提供输出 attachment 的源节点
     * @param fromChannel 源节点的 fragment output location
     * @param to 接收纹理输入的目标节点
     * @param toChannel 目标节点的纹理单元序号
     * @return 当前 pipeline builder
     * @throws IllegalArgumentException 任一通道不存在或不唯一时抛出
     */
    fun line(
        from: CooPipelineNode,
        fromChannel: Int,
        to: CooPipelineNode,
        toChannel: Int
    ) = line(from.output(fromChannel), to.input(toChannel))

    fun texture(texture: ResourceLocation): CooPipelineTextureSource = CooPipelineTextureSource.Texture(texture)
    fun blockAtlas(): CooPipelineTextureSource = CooPipelineTextureSource.BlockAtlas
    fun sceneColor(): CooPipelineTextureSource = CooPipelineTextureSource.SceneColor
    fun sceneDepth(): CooPipelineTextureSource = CooPipelineTextureSource.SceneDepth
    fun framebuffer(target: ResourceLocation, attachment: Int = 0): CooPipelineTextureSource =
        CooPipelineTextureSource.FramebufferColor(target, attachment)
    fun mask(): CooPipelineTextureSource = CooPipelineTextureSource.Mask
    fun temporary(): CooPipelineTextureSource = CooPipelineTextureSource.Temporary
    fun bloom(): CooPipelineTextureSource = CooPipelineTextureSource.Bloom

    fun worldTarget(): CooPipelineTarget = CooPipelineTarget.World
    fun screenTarget(): CooPipelineTarget = CooPipelineTarget.FinalScreen
    fun maskTarget(): CooPipelineTarget = CooPipelineTarget.Mask
    fun temporaryTarget(): CooPipelineTarget = CooPipelineTarget.Temporary
    fun bloomTarget(): CooPipelineTarget = CooPipelineTarget.Bloom
    fun framebufferTarget(target: ResourceLocation, attachment: Int = 0): CooPipelineTarget =
        CooPipelineTarget.FramebufferColor(target, attachment)

    /** 把 fluent 参数绑定到一个或多个节点 uniform。 */
    fun parameter(name: String, node: CooPipelineNode, uniform: String) = apply {
        require(node in nodes) { "Parameter node '${node.name}' is not part of pipeline '$id'" }
        require(uniform in node.uniforms) { "Node '${node.name}' has no uniform '$uniform'" }
        parameters.getOrPut(name, ::ArrayList) += CooPipelineParameterBinding(node.name, uniform)
    }

    internal fun build(): CooRenderPipeline<T> {
        val needsImplicitWorld = implicitWorldUsed ||
            (domain == CooPipelineDomain.ENTITY || domain == CooPipelineDomain.BLOCK) &&
            nodes.none { it.kind == CooPipelineNodeKind.WORLD }
        if (needsImplicitWorld) {
            if (domain == CooPipelineDomain.BLOCK) {
                implicitWorld.ensureInput("BaseSampler", CooPipelineTextureSource.BlockAtlas)
            }
            val result = implicitWorld.build(sequence++)
            require(nodes.none { it.name == result.node.name }) { "Duplicate pipeline node '${result.node.name}'" }
            nodes.add(0, result.node)
            addInitialConnections(result)
        }
        nodes.filter { it.kind == CooPipelineNodeKind.WORLD }.forEach { node ->
            if (lines.none { line ->
                    val output = line.output as? CooPipelineOutputPort
                    output?.node == node.name &&
                        output.semantic == CooPipelineOutputSemantic.COLOR &&
                        line.input is CooPipelineTarget
                }
            ) {
                lines += CooPipelineLine(node.color(), CooPipelineTarget.World)
            }
        }
        validateGraph()
        return CooRenderPipeline(
            id = id,
            domain = domain,
            terrainLayer = terrainLayer,
            effectUvMode = effectUvMode,
            nodes = nodes,
            lines = lines,
            primaryNode = nodes.firstOrNull { it.kind == CooPipelineNodeKind.WORLD }?.name
                ?: nodes.firstOrNull()?.name,
            parameterBindings = parameters
        )
    }

    internal fun hasNodes(): Boolean = nodes.isNotEmpty()

    private fun addNode(builder: CooPipelineNodeBuilder<T>): CooPipelineNode {
        val result = builder.build(sequence++)
        require(nodes.none { it.name == result.node.name }) { "Duplicate pipeline node '${result.node.name}'" }
        nodes += result.node
        addInitialConnections(result)
        return result.node
    }

    private fun addInitialConnections(result: CooNodeBuildResult) {
        result.sources.forEach { (sampler, source) ->
            lines += CooPipelineLine(source, result.node.input(sampler))
        }
        result.targets.forEach { target ->
            lines += CooPipelineLine(result.node.color(), target)
        }
    }

    private fun validateGraph() {
        val byName = nodes.associateBy(CooPipelineNode::name)
        lines.forEach { line ->
            val input = line.input
            if (input is CooPipelineInputPort) {
                val targetNode = requireNotNull(byName[input.node]) { "Unknown target node '${input.node}'" }
                require(input in targetNode.inputs) { "Input port '${input.sampler}' does not belong to '${input.node}'" }
                if (line.output is CooPipelineOutputPort) {
                    require(targetNode.kind != CooPipelineNodeKind.WORLD) {
                        "World node '${targetNode.name}' cannot consume another node output; " +
                            "move the dependent stage to a fullscreen pass"
                    }
                }
            }
            val output = line.output
            if (output is CooPipelineOutputPort) {
                val sourceNode = requireNotNull(byName[output.node]) { "Unknown source node '${output.node}'" }
                require(output in sourceNode.outputs) { "Output port '${output.name}' does not belong to '${output.node}'" }
            }
        }
        nodes.flatMap(CooPipelineNode::inputs).forEach { input ->
            val count = lines.count { it.input == input }
            require(count <= 1) { "Input '${input.node}.${input.sampler}' has more than one line" }
            require(input.optional || count == 1) { "Required input '${input.node}.${input.sampler}' is not connected" }
        }
        lines.filter { it.input is CooPipelineTarget }.forEach { line ->
            require(line.output is CooPipelineOutputPort) {
                "Pipeline target '${line.input}' must be connected from a node output"
            }
        }
    }
}
