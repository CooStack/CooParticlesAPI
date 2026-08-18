package cn.coostack.cooparticlesapi.renderer.pipeline

import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.CooTextureFormat
import net.minecraft.resources.ResourceLocation

/**
 * 单个 Pipeline 节点的构建器。
 *
 * 构造函数由 [CooRenderPipelineBuilder] 创建，调用方通过 `world`、`pass` 或 `pingPong`
 * 的 DSL block 配置节点，不应脱离所属 graph 单独持有 builder。
 *
 * @param name 节点在当前 graph 中的唯一名称
 * @param kind 节点的执行模型，影响可用输出和 shader 要求
 */
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
    private var outputFormat = CooTextureFormat.RGBA8
    private var outputMipLevels = 1
    private var order = 0

    /**
     * 在 `CooPipelineNodeBuilder` 中配置 `shader`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`shader(shader = shader)`。
     *
     * @param shader 要加载、编译或绑定的 shader 资源
     */
    fun shader(shader: ResourceLocation) = apply {
        coreShader = shader
    }

    /**
     * 在 `CooPipelineNodeBuilder` 中配置 `vertex`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`vertex(shader = shader)`。
     *
     * @param shader 要加载、编译或绑定的 shader 资源
     */
    fun vertex(shader: ResourceLocation) = apply {
        vertexShader = shader
    }

    /**
     * 在 `CooPipelineNodeBuilder` 中配置 `fragment`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`fragment(shader = shader)`。
     *
     * @param shader 要加载、编译或绑定的 shader 资源
     */
    fun fragment(shader: ResourceLocation) = apply {
        fragmentShader = shader
    }

    /**
     * 在 `CooPipelineNodeBuilder` 中配置 `order`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`order(value = value)`。
     *
     * @param value 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun order(value: Int) = apply {
        order = value
    }

    /** 声明一个尚未连线的 sampler 输入端口。 */
    fun input(
        sampler: String,
        optional: Boolean = false,
        textureSlot: Int = inputs.size,
        format: CooTextureFormat? = null,
        mipLevels: Int = 1
    ) = apply {
        require(sampler !in inputs) { "Node '$name' already declares input '$sampler'" }
        require(mipLevels > 0) { "Node '$name' input '$sampler' must require at least one mip level" }
        inputs[sampler] = CooPipelineInputPort(name, sampler, optional, textureSlot, format, mipLevels)
    }

    /** 声明端口并直接把一个外部资源连到它。 */
    fun input(
        sampler: String,
        source: CooPipelineTextureSource,
        optional: Boolean = false,
        textureSlot: Int = inputs.size,
        format: CooTextureFormat? = null,
        mipLevels: Int = 1
    ) = apply {
        input(sampler, optional, textureSlot, format, mipLevels)
        sources[sampler] = source
    }

    /**
     * 在 `CooPipelineNodeBuilder` 中配置 `inputTexture`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`inputTexture(sampler = sampler, texture = texture, optional = optional, textureSlot = textureSlot)`。
     *
     * @param sampler 用于查找、绑定或记录目标的名称
     *
     * @param texture 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param optional 控制是否启用对应分支或强制执行操作的开关
     *
     * @param textureSlot 数量或从零开始的索引值，具体上限由当前资源配置决定
     */
    fun inputTexture(
        sampler: String,
        texture: ResourceLocation,
        optional: Boolean = false,
        textureSlot: Int = inputs.size
    ) = input(sampler, CooPipelineTextureSource.Texture(texture), optional, textureSlot)

    /**
     * 在 `CooPipelineNodeBuilder` 中配置 `inputBlockAtlas`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`inputBlockAtlas(sampler = sampler, optional = optional, textureSlot = textureSlot)`。
     *
     * @param sampler 用于查找、绑定或记录目标的名称
     *
     * @param optional 控制是否启用对应分支或强制执行操作的开关
     *
     * @param textureSlot 数量或从零开始的索引值，具体上限由当前资源配置决定
     */
    fun inputBlockAtlas(
        sampler: String = "BaseSampler",
        optional: Boolean = false,
        textureSlot: Int = inputs.size
    ) = input(sampler, CooPipelineTextureSource.BlockAtlas, optional, textureSlot)

    /**
     * 在 `CooPipelineNodeBuilder` 中配置 `inputSceneColor`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`inputSceneColor(sampler = sampler, optional = optional, textureSlot = textureSlot)`。
     *
     * @param sampler 用于查找、绑定或记录目标的名称
     *
     * @param optional 控制是否启用对应分支或强制执行操作的开关
     *
     * @param textureSlot 数量或从零开始的索引值，具体上限由当前资源配置决定
     */
    fun inputSceneColor(
        sampler: String = "SceneColor",
        optional: Boolean = false,
        textureSlot: Int = inputs.size
    ) = input(sampler, CooPipelineTextureSource.SceneColor, optional, textureSlot)

    /**
     * 在 `CooPipelineNodeBuilder` 中配置 `inputSceneDepth`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`inputSceneDepth(sampler = sampler, optional = optional, textureSlot = textureSlot)`。
     *
     * @param sampler 用于查找、绑定或记录目标的名称
     *
     * @param optional 控制是否启用对应分支或强制执行操作的开关
     *
     * @param textureSlot 数量或从零开始的索引值，具体上限由当前资源配置决定
     */
    fun inputSceneDepth(
        sampler: String = "SceneDepth",
        optional: Boolean = false,
        textureSlot: Int = inputs.size
    ) = input(sampler, CooPipelineTextureSource.SceneDepth, optional, textureSlot)

    /** 声明 terrain opaque depth 输入；默认 optional，缺失时绑定纹理 0 并由 shader 处理不可用状态。 */
    fun inputTerrainDepth(
        sampler: String = "TerrainDepth",
        optional: Boolean = true,
        textureSlot: Int = inputs.size
    ) = input(sampler, CooPipelineTextureSource.TerrainDepth, optional, textureSlot)

    /**
     * 在 `CooPipelineNodeBuilder` 中配置 `inputFramebuffer`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`inputFramebuffer(sampler = sampler, target = target, attachment = attachment, optional = optional, textureSlot = textureSlot)`。
     *
     * @param sampler 用于查找、绑定或记录目标的名称
     *
     * @param target 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param attachment 数量或从零开始的索引值，具体上限由当前资源配置决定
     *
     * @param optional 控制是否启用对应分支或强制执行操作的开关
     *
     * @param textureSlot 数量或从零开始的索引值，具体上限由当前资源配置决定
     */
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

    /**
     * 在 `CooPipelineNodeBuilder` 中配置 `inputMask`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`inputMask(sampler = sampler, optional = optional, textureSlot = textureSlot)`。
     *
     * @param sampler 用于查找、绑定或记录目标的名称
     *
     * @param optional 控制是否启用对应分支或强制执行操作的开关
     *
     * @param textureSlot 数量或从零开始的索引值，具体上限由当前资源配置决定
     */
    fun inputMask(
        sampler: String = "Mask",
        optional: Boolean = false,
        textureSlot: Int = inputs.size
    ) = input(sampler, CooPipelineTextureSource.Mask, optional, textureSlot)

    /**
     * 在 `CooPipelineNodeBuilder` 中配置 `inputTemporary`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`inputTemporary(sampler = sampler, optional = optional, textureSlot = textureSlot)`。
     *
     * @param sampler 用于查找、绑定或记录目标的名称
     *
     * @param optional 控制是否启用对应分支或强制执行操作的开关
     *
     * @param textureSlot 数量或从零开始的索引值，具体上限由当前资源配置决定
     */
    fun inputTemporary(
        sampler: String = "Temporary",
        optional: Boolean = false,
        textureSlot: Int = inputs.size
    ) = input(sampler, CooPipelineTextureSource.Temporary, optional, textureSlot)

    /**
     * 在 `CooPipelineNodeBuilder` 中配置 `inputBloom`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`inputBloom(sampler = sampler, optional = optional, textureSlot = textureSlot)`。
     *
     * @param sampler 用于查找、绑定或记录目标的名称
     *
     * @param optional 控制是否启用对应分支或强制执行操作的开关
     *
     * @param textureSlot 数量或从零开始的索引值，具体上限由当前资源配置决定
     */
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

    /** 声明当前节点全部颜色输出的实际存储格式。 */
    fun outputFormat(format: CooTextureFormat) = apply {
        outputFormat = format
    }

    /** 声明当前节点全部颜色输出分配的 mip 层数。 */
    fun mipLevels(levels: Int) = apply {
        require(levels > 0) { "Node '$name' must allocate at least one mip level" }
        outputMipLevels = levels
    }

    /** 为 world 节点增加独立 mask 输出端口。 */
    fun maskOutput() = apply {
        require(kind == CooPipelineNodeKind.WORLD) { "Only world nodes can declare a mask output" }
        hasMaskOutput = true
    }

    /**
     * 在 `CooPipelineNodeBuilder` 中配置 `outputToWorld`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`outputToWorld()`。
     */
    fun outputToWorld() = apply { targets += CooPipelineTarget.World }
    /**
     * 在 `CooPipelineNodeBuilder` 中配置 `outputToScreen`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`outputToScreen()`。
     */
    fun outputToScreen() = apply { targets += CooPipelineTarget.FinalScreen }
    /**
     * 在 `CooPipelineNodeBuilder` 中配置 `outputToMask`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`outputToMask()`。
     */
    fun outputToMask() = apply { targets += CooPipelineTarget.Mask }
    /**
     * 在 `CooPipelineNodeBuilder` 中配置 `outputToTemporary`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`outputToTemporary()`。
     */
    fun outputToTemporary() = apply { targets += CooPipelineTarget.Temporary }
    /**
     * 在 `CooPipelineNodeBuilder` 中配置 `outputToBloom`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`outputToBloom()`。
     */
    fun outputToBloom() = apply { targets += CooPipelineTarget.Bloom }
    /**
     * 在 `CooPipelineNodeBuilder` 中配置 `outputToFramebuffer`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`outputToFramebuffer(target = target, attachment = attachment)`。
     *
     * @param target 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param attachment 数量或从零开始的索引值，具体上限由当前资源配置决定
     */
    fun outputToFramebuffer(target: ResourceLocation, attachment: Int = 0) = apply {
        targets += CooPipelineTarget.FramebufferColor(target, attachment)
    }

    /**
     * 在 `CooPipelineNodeBuilder` 中配置 `uniform`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`uniform(name = name, value = value)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @param value 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun uniform(name: String, value: Float) = apply {
        setUniform(name) { CooUniformValue.FloatValue(value) }
    }

    /**
     * 在 `CooPipelineNodeBuilder` 中配置 `uniform`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`uniform(name = name, value = value)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @param value 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun uniform(name: String, value: CooUniformValue) = apply {
        setUniform(name) { value }
    }

    /**
     * 在 `CooPipelineNodeBuilder` 中配置 `uniform`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`uniform(name = name, provider = provider)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @param provider 在当前生命周期或数据上下文中执行的回调
     */
    fun <R : Any> uniform(name: String, provider: (R) -> Float) = apply {
        setUniform(name) { subject ->
            @Suppress("UNCHECKED_CAST")
            CooUniformValue.FloatValue(provider(subject as R))
        }
    }

    /**
     * 在 `CooPipelineNodeBuilder` 中配置 `uniformValue`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`uniformValue(name = name, provider = provider)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @param provider 在当前生命周期或数据上下文中执行的回调
     */
    fun <R : Any> uniformValue(name: String, provider: CooUniformProvider<R>) = apply {
        setUniform(name) { subject ->
            @Suppress("UNCHECKED_CAST")
            provider.resolve(subject as R)
        }
    }

    /**
     * 在 `CooPipelineNodeBuilder` 中配置 `alternate`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`alternate(name = name, ping = ping, pong = pong)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @param ping 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param pong 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun alternate(name: String, ping: Float, pong: Float) = alternate(
        name,
        CooUniformValue.FloatValue(ping),
        CooUniformValue.FloatValue(pong)
    )

    /**
     * 在 `CooPipelineNodeBuilder` 中配置 `alternate`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`alternate(name = name, ping = ping, pong = pong)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @param ping 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param pong 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun alternate(name: String, ping: Int, pong: Int) = alternate(
        name,
        CooUniformValue.IntValue(ping),
        CooUniformValue.IntValue(pong)
    )

    /**
     * 在 `CooPipelineNodeBuilder` 中配置 `alternate`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`alternate(name = name, ping = ping, pong = pong)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @param ping 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param pong 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun alternate(name: String, ping: Boolean, pong: Boolean) = alternate(
        name,
        CooUniformValue.BoolValue(ping),
        CooUniformValue.BoolValue(pong)
    )

    /**
     * 在 `CooPipelineNodeBuilder` 中配置 `alternate`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`alternate(name = name, ping = ping, pong = pong)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @param ping 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param pong 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun alternate(name: String, ping: CooUniformValue, pong: CooUniformValue) = apply {
        require(kind == CooPipelineNodeKind.PING_PONG) {
            "Alternating uniforms are only available on ping-pong nodes"
        }
        setIterationUniform(name) { iteration ->
            if (iteration.isPing) ping else pong
        }
    }

    /**
     * 在 `CooPipelineNodeBuilder` 中配置 `iterationUniform`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`iterationUniform(name = name, provider = provider)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @param provider 在当前生命周期或数据上下文中执行的回调
     */
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

    /**
     * 在 `CooPipelineNodeBuilder` 中配置 `pingPong`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`pingPong(iterations = iterations, feedbackSampler = feedbackSampler)`。
     *
     * @param iterations 数量或从零开始的索引值，具体上限由当前资源配置决定
     *
     * @param feedbackSampler 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    internal fun pingPong(
        iterations: Int,
        feedbackSampler: String,
        inputFormat: CooTextureFormat?,
        inputMipLevels: Int
    ) {
        require(kind == CooPipelineNodeKind.PING_PONG)
        require(iterations > 0) { "Ping-pong node '$name' must execute at least once" }
        pingPongIterations = iterations
        pingPongFeedbackSampler = feedbackSampler
        input(feedbackSampler, format = inputFormat, mipLevels = inputMipLevels)
    }

    /**
     * 在 `CooPipelineNodeBuilder` 中配置 `ensureInput`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`ensureInput(sampler = sampler, source = source, optional = optional, textureSlot = textureSlot)`。
     *
     * @param sampler 用于查找、绑定或记录目标的名称
     *
     * @param source 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param optional 控制是否启用对应分支或强制执行操作的开关
     *
     * @param textureSlot 数量或从零开始的索引值，具体上限由当前资源配置决定
     */
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

    /**
     * 根据输入和 `CooPipelineNodeBuilder` 当前配置创建 `build` 结果；返回对象保留本次配置的语义。
     *
     * 示例：`build(sequence = sequence)`。
     *
     * @param sequence 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
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
                        attachment = attachment,
                        format = outputFormat,
                        mipLevels = outputMipLevels
                    )
                )
            }
            if (hasMaskOutput) {
                add(
                    CooPipelineOutputPort(
                        name,
                        "Mask",
                        CooPipelineOutputSemantic.MASK,
                        colorAttachmentCount,
                        outputFormat,
                        outputMipLevels
                    )
                )
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

/**
 * 不可变 [CooRenderPipeline] 的 graph 构建器。
 *
 * 内部构造函数由 [CooPipelines] 和 shader effect facade 统一调用，以确保 id、域和注册
 * 生命周期一致。示例：`CooPipelines.entity<MyEntity>(id) { world { shader(shaderId) } }`。
 *
 * @param id Pipeline 的资源标识
 * @param domain 实体、方块、通用或屏幕使用域
 */
class CooRenderPipelineBuilder<T : Any> internal constructor(
    private val id: ResourceLocation,
    private val domain: CooPipelineDomain
) {
    private var terrainLayer = CooTerrainLayer.INHERIT
    private var effectUvMode = CooEffectUvMode.BASE_UV
    private var postInScene = false
    private val nodes = ArrayList<CooPipelineNode>()
    private val lines = ArrayList<CooPipelineLine>()
    private val parameters = LinkedHashMap<String, MutableList<CooPipelineParameterBinding>>()
    private val implicitWorld = CooPipelineNodeBuilder<T>("world", CooPipelineNodeKind.WORLD)
    private var implicitWorldUsed = false
    private var sequence = 0

    /**
     * 在 `CooRenderPipelineBuilder` 中配置 `terrainLayer`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`terrainLayer(layer = layer)`。
     *
     * @param layer 模型分组或地形渲染层，决定图元筛选及 RenderType 行为
     */
    fun terrainLayer(layer: CooTerrainLayer) = apply { terrainLayer = layer }
    /**
     * 在 `CooRenderPipelineBuilder` 中配置 `effectUv`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`effectUv(mode = mode)`。
     *
     * @param mode 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun effectUv(mode: CooEffectUvMode) = apply { effectUvMode = mode }

    /** 把 fullscreen 节点安排到最终场景颜色准备完成后的专用阶段。 */
    internal fun postInScene() = apply { postInScene = true }

    /**
     * 在 `CooRenderPipelineBuilder` 中配置 `shader`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`shader(shader = shader)`。
     *
     * @param shader 要加载、编译或绑定的 shader 资源
     */
    fun shader(shader: ResourceLocation) = apply {
        implicitWorldUsed = true
        implicitWorld.shader(shader)
    }

    /**
     * 在 `CooRenderPipelineBuilder` 中配置 `inputTexture`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`inputTexture(sampler = sampler, texture = texture, textureSlot = textureSlot)`。
     *
     * @param sampler 用于查找、绑定或记录目标的名称
     *
     * @param texture 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param textureSlot 数量或从零开始的索引值，具体上限由当前资源配置决定
     */
    fun inputTexture(sampler: String, texture: ResourceLocation, textureSlot: Int = 0) = apply {
        implicitWorldUsed = true
        implicitWorld.inputTexture(sampler, texture, textureSlot = textureSlot)
    }

    /**
     * 在 `CooRenderPipelineBuilder` 中配置 `inputBlockAtlas`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`inputBlockAtlas(sampler = sampler, textureSlot = textureSlot)`。
     *
     * @param sampler 用于查找、绑定或记录目标的名称
     *
     * @param textureSlot 数量或从零开始的索引值，具体上限由当前资源配置决定
     */
    fun inputBlockAtlas(sampler: String = "BaseSampler", textureSlot: Int = 0) = apply {
        implicitWorldUsed = true
        implicitWorld.inputBlockAtlas(sampler, textureSlot = textureSlot)
    }

    /**
     * 在 `CooRenderPipelineBuilder` 中配置 `inputSceneColor`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`inputSceneColor(sampler = sampler, optional = optional, textureSlot = textureSlot)`。
     *
     * @param sampler 用于查找、绑定或记录目标的名称
     *
     * @param optional 控制是否启用对应分支或强制执行操作的开关
     *
     * @param textureSlot 数量或从零开始的索引值，具体上限由当前资源配置决定
     */
    fun inputSceneColor(sampler: String = "SceneColor", optional: Boolean = false, textureSlot: Int = 0) = apply {
        implicitWorldUsed = true
        implicitWorld.inputSceneColor(sampler, optional, textureSlot)
    }

    /**
     * 在 `CooRenderPipelineBuilder` 中配置 `inputSceneDepth`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`inputSceneDepth(sampler = sampler, optional = optional, textureSlot = textureSlot)`。
     *
     * @param sampler 用于查找、绑定或记录目标的名称
     *
     * @param optional 控制是否启用对应分支或强制执行操作的开关
     *
     * @param textureSlot 数量或从零开始的索引值，具体上限由当前资源配置决定
     */
    fun inputSceneDepth(sampler: String = "SceneDepth", optional: Boolean = false, textureSlot: Int = 1) = apply {
        implicitWorldUsed = true
        implicitWorld.inputSceneDepth(sampler, optional, textureSlot)
    }

    /** 声明 terrain opaque depth 输入；默认 optional，缺失时绑定纹理 0。 */
    fun inputTerrainDepth(sampler: String = "TerrainDepth", optional: Boolean = true, textureSlot: Int = 1) = apply {
        implicitWorldUsed = true
        implicitWorld.inputTerrainDepth(sampler, optional, textureSlot)
    }

    /**
     * 在 `CooRenderPipelineBuilder` 中配置 `uniform`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`uniform(name = name, value = value)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @param value 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun uniform(name: String, value: Float) = apply {
        implicitWorldUsed = true
        implicitWorld.uniform(name, value)
    }

    /**
     * 在 `CooRenderPipelineBuilder` 中配置 `uniform`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`uniform(name = name, value = value)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @param value 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun uniform(name: String, value: CooUniformValue) = apply {
        implicitWorldUsed = true
        implicitWorld.uniform(name, value)
    }

    /**
     * 在 `CooRenderPipelineBuilder` 中配置 `world`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`world(name = name, block = block)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @param block 在当前生命周期或数据上下文中执行的回调
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun world(name: String = "world", block: CooPipelineNodeBuilder<T>.() -> Unit = {}): CooPipelineNode {
        return addNode(CooPipelineNodeBuilder<T>(name, CooPipelineNodeKind.WORLD).apply(block))
    }

    /**
     * 在 `CooRenderPipelineBuilder` 中配置 `pass`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`pass(name = name, block = block)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @param block 在当前生命周期或数据上下文中执行的回调
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun pass(name: String, block: CooPipelineNodeBuilder<T>.() -> Unit): CooPipelineNode {
        return addNode(CooPipelineNodeBuilder<T>(name, CooPipelineNodeKind.FULLSCREEN).apply(block))
    }

    /**
     * 在 `CooRenderPipelineBuilder` 中配置 `pingPong`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`pingPong(name = name, iterations = iterations, feedbackSampler = feedbackSampler, block = block)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @param iterations 数量或从零开始的索引值，具体上限由当前资源配置决定
     *
     * @param feedbackSampler 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param block 在当前生命周期或数据上下文中执行的回调
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun pingPong(
        name: String,
        iterations: Int,
        feedbackSampler: String = "Input",
        block: CooPipelineNodeBuilder<T>.() -> Unit
    ): CooPipelineNode {
        val builder = CooPipelineNodeBuilder<T>(name, CooPipelineNodeKind.PING_PONG)
        builder.pingPong(iterations, feedbackSampler, null, 1)
        return addNode(builder.apply(block))
    }

    /**
     * 创建带显式反馈纹理契约的 ping-pong 节点。
     *
     * @param name 节点名称
     * @param iterations 循环次数
     * @param feedbackSampler 每轮读取前一轮结果的 sampler
     * @param inputFormat 反馈输入的期望格式
     * @param inputMipLevels 反馈输入至少需要的 mip 层数
     * @param block 节点配置
     */
    fun pingPong(
        name: String,
        iterations: Int,
        feedbackSampler: String = "Input",
        inputFormat: CooTextureFormat,
        inputMipLevels: Int = 1,
        block: CooPipelineNodeBuilder<T>.() -> Unit
    ): CooPipelineNode {
        val builder = CooPipelineNodeBuilder<T>(name, CooPipelineNodeKind.PING_PONG)
        builder.pingPong(iterations, feedbackSampler, inputFormat, inputMipLevels)
        return addNode(builder.apply(block))
    }

    /**
     * 在 `CooRenderPipelineBuilder` 中配置 `addPreparedNode`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`addPreparedNode(builder = builder)`。
     *
     * @param builder 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
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

    /**
     * 在 `CooRenderPipelineBuilder` 中配置 `texture`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`texture(texture = texture)`。
     *
     * @param texture 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun texture(texture: ResourceLocation): CooPipelineTextureSource = CooPipelineTextureSource.Texture(texture)
    /**
     * 在 `CooRenderPipelineBuilder` 中配置 `blockAtlas`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`blockAtlas()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun blockAtlas(): CooPipelineTextureSource = CooPipelineTextureSource.BlockAtlas
    /**
     * 在 `CooRenderPipelineBuilder` 中配置 `sceneColor`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`sceneColor()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun sceneColor(): CooPipelineTextureSource = CooPipelineTextureSource.SceneColor
    /**
     * 在 `CooRenderPipelineBuilder` 中配置 `sceneDepth`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`sceneDepth()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun sceneDepth(): CooPipelineTextureSource = CooPipelineTextureSource.SceneDepth
    /**
     * 在 `CooRenderPipelineBuilder` 中配置 `framebuffer`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`framebuffer(target = target, attachment = attachment)`。
     *
     * @param target 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param attachment 数量或从零开始的索引值，具体上限由当前资源配置决定
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun framebuffer(target: ResourceLocation, attachment: Int = 0): CooPipelineTextureSource =
        CooPipelineTextureSource.FramebufferColor(target, attachment)
    /**
     * 在 `CooRenderPipelineBuilder` 中配置 `mask`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`mask()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun mask(): CooPipelineTextureSource = CooPipelineTextureSource.Mask
    /**
     * 在 `CooRenderPipelineBuilder` 中配置 `temporary`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`temporary()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun temporary(): CooPipelineTextureSource = CooPipelineTextureSource.Temporary
    /**
     * 在 `CooRenderPipelineBuilder` 中配置 `bloom`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`bloom()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun bloom(): CooPipelineTextureSource = CooPipelineTextureSource.Bloom

    /**
     * 在 `CooRenderPipelineBuilder` 中配置 `worldTarget`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`worldTarget()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun worldTarget(): CooPipelineTarget = CooPipelineTarget.World
    /**
     * 在 `CooRenderPipelineBuilder` 中配置 `screenTarget`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`screenTarget()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun screenTarget(): CooPipelineTarget = CooPipelineTarget.FinalScreen
    /**
     * 在 `CooRenderPipelineBuilder` 中配置 `maskTarget`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`maskTarget()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun maskTarget(): CooPipelineTarget = CooPipelineTarget.Mask
    /**
     * 在 `CooRenderPipelineBuilder` 中配置 `temporaryTarget`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`temporaryTarget()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun temporaryTarget(): CooPipelineTarget = CooPipelineTarget.Temporary
    /**
     * 在 `CooRenderPipelineBuilder` 中配置 `bloomTarget`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`bloomTarget()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun bloomTarget(): CooPipelineTarget = CooPipelineTarget.Bloom
    /**
     * 在 `CooRenderPipelineBuilder` 中配置 `framebufferTarget`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`framebufferTarget(target = target, attachment = attachment)`。
     *
     * @param target 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param attachment 数量或从零开始的索引值，具体上限由当前资源配置决定
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun framebufferTarget(target: ResourceLocation, attachment: Int = 0): CooPipelineTarget =
        CooPipelineTarget.FramebufferColor(target, attachment)

    /** 把 fluent 参数绑定到一个或多个节点 uniform。 */
    fun parameter(name: String, node: CooPipelineNode, uniform: String) = apply {
        require(node in nodes) { "Parameter node '${node.name}' is not part of pipeline '$id'" }
        require(uniform in node.uniforms) { "Node '${node.name}' has no uniform '$uniform'" }
        parameters.getOrPut(name, ::ArrayList) += CooPipelineParameterBinding(node.name, uniform)
    }

    /**
     * 根据输入和 `CooRenderPipelineBuilder` 当前配置创建 `build` 结果；返回对象保留本次配置的语义。
     *
     * 示例：`build()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
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
            postInScene = postInScene,
            nodes = nodes,
            lines = lines,
            primaryNode = nodes.firstOrNull { it.kind == CooPipelineNodeKind.WORLD }?.name
                ?: nodes.firstOrNull()?.name,
            parameterBindings = parameters
        )
    }

    /**
     * 在 `CooRenderPipelineBuilder` 中配置 `hasNodes`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`hasNodes()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
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
