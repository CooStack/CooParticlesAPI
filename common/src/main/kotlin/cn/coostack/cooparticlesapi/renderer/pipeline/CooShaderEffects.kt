package cn.coostack.cooparticlesapi.renderer.pipeline

import cn.coostack.cooparticlesapi.renderer.post.CooPostEffects
import cn.coostack.cooparticlesapi.renderer.post.PostEffectLifecycle
import cn.coostack.cooparticlesapi.renderer.post.PostEffectParamValue
import cn.coostack.cooparticlesapi.renderer.post.PostEffectParams
import cn.coostack.cooparticlesapi.renderer.post.PostEffectParamsBuilder
import cn.coostack.cooparticlesapi.renderer.post.PostEffectRuntimeRegistry
import cn.coostack.cooparticlesapi.renderer.post.PostEffectType
import cn.coostack.cooparticlesapi.renderer.post.toPostEffectParamValue
import cn.coostack.cooparticlesapi.renderer.post.withParamUniforms
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/** 独立屏幕 ShaderEffect 的注册和播放入口。 */
object CooShaderEffects {
    private val effects = LinkedHashMap<ResourceLocation, CooShaderEffect>()

    /**
     * 把输入对象加入 `CooShaderEffects` 的 `register` 管理范围，后续查询、构建或绘制会使用该绑定。
     *
     * 示例：`register(id = id, block = block)`。
     *
     * @param id 用于定位目标资源、实体或运行时实例的唯一标识
     *
     * @param block 在当前生命周期或数据上下文中执行的回调
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun register(id: ResourceLocation, block: CooShaderEffectBuilder.() -> Unit): CooShaderEffect {
        require(id !in effects) { "Shader effect already registered: $id" }
        val effect = CooShaderEffectBuilder(id).apply(block).build()
        PostEffectRuntimeRegistry.registerType(effect.postType)
        effects[id] = effect
        return effect
    }

    /**
     * 从 `CooShaderEffects` 当前维护的状态中读取 `get` 结果，不创建新的渲染资源。
     *
     * 示例：`get(id = id)`。
     *
     * @param id 用于定位目标资源、实体或运行时实例的唯一标识
     *
     * @return 匹配当前条件的对象或状态；可空返回值表示没有可用结果
     */
    fun get(id: ResourceLocation): CooShaderEffect? = effects[id]

    /**
     * 执行 `CooShaderEffects` 定义的 `all` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`all()`。
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun all(): Collection<CooShaderEffect> = effects.values.toList()
}

/**
 * 注册后的不可变屏幕渲染图。
 *
 * 构造函数由 [CooShaderEffects] 注册流程调用；业务代码通过注册结果的 [play] 方法创建
 * 播放实例，而不是手动组合内部 post 类型。
 *
 * @property pipeline 已编译的屏幕 Pipeline
 * @param postType 负责创建 runtime 实例的内部 post 类型
 * @param defaultParams 每次播放都会合并的默认参数快照
 */
class CooShaderEffect internal constructor(
    val pipeline: CooRenderPipeline<Any>,
    internal val postType: PostEffectType,
    private val defaultParams: PostEffectParams
) {
    val id: ResourceLocation get() = pipeline.id

    /**
     * 执行 `CooShaderEffect` 定义的 `play` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`play(block = block)`。
     *
     * @param block 在当前生命周期或数据上下文中执行的回调
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun play(block: CooShaderEffectPlayBuilder.() -> Unit = {}): CooShaderEffectPlayback {
        val instance = createInstance(block)
        CooPostEffects.client.add(instance)
        return CooShaderEffectPlayback(
            effect = this,
            instanceId = instance.instanceId,
            stopAction = CooPostEffects.client::remove,
            playingQuery = { instanceId ->
                CooPostEffects.client.activeInstances().any { it.instanceId == instanceId }
            }
        )
    }

    /** 从服务端向指定玩家播放同一个已注册屏幕效果。 */
    fun play(
        player: ServerPlayer,
        block: CooShaderEffectPlayBuilder.() -> Unit = {}
    ): CooShaderEffectPlayback {
        val instance = createInstance(block)
        CooPostEffects.server.send(player, instance)
        return CooShaderEffectPlayback(
            effect = this,
            instanceId = instance.instanceId,
            stopAction = { instanceId -> CooPostEffects.server.remove(player, instanceId) },
            playingQuery = { true }
        )
    }

    private fun createInstance(block: CooShaderEffectPlayBuilder.() -> Unit) =
        CooShaderEffectPlayBuilder().apply(block).build().let { request ->
        val runtimeType = postType.withParamUniforms(request.uniformNames)
        val params = request.params.asMap().entries.fold(defaultParams) { current, (name, value) ->
            current.plus(name, value)
        }
        runtimeType.create(
            lifecycle = PostEffectLifecycle(durationTicks = request.durationTicks),
            params = params
        )
    }
}

/**
 * 一次 shader effect 播放的控制句柄。
 *
 * 内部构造函数把客户端或服务端的停止动作封装成相同接口。示例：
 * `val playback = effect.play(); playback.stop()`。
 *
 * @property effect 本次播放使用的已注册效果
 * @property instanceId runtime 实例 id，用于停止和状态查询
 * @param stopAction 按实例 id 执行的客户端或服务端停止动作
 * @param playingQuery 查询实例是否仍存在的函数
 */
class CooShaderEffectPlayback internal constructor(
    val effect: CooShaderEffect,
    val instanceId: String,
    private val stopAction: (String) -> Unit,
    private val playingQuery: (String) -> Boolean
) {
    private var stopped = false

    /**
     * 执行 `CooShaderEffectPlayback` 定义的 `stop` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`stop()`。
     */
    fun stop() {
        if (stopped) return
        stopped = true
        stopAction(instanceId)
    }

    /**
     * 执行 `CooShaderEffectPlayback` 定义的 `isPlaying` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`isPlaying()`。
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun isPlaying(): Boolean {
        return !stopped && playingQuery(instanceId)
    }
}

class CooShaderEffectPlayBuilder {
    private var durationTicks = 1
    private val params = PostEffectParamsBuilder()
    private val uniformNames = linkedSetOf<String>()

    /**
     * 在 `CooShaderEffectPlayBuilder` 中配置 `duration`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`duration(ticks = ticks)`。
     *
     * @param ticks 以 Minecraft tick 为单位的时间或年龄值
     */
    fun duration(ticks: Int) = apply {
        require(ticks > 0) { "Shader effect duration must be greater than zero" }
        durationTicks = ticks
    }

    /**
     * 在 `CooShaderEffectPlayBuilder` 中配置 `uniform`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`uniform(name = name, value = value)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @param value 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun uniform(name: String, value: Boolean) = uniform(name, PostEffectParamValue.BoolValue(value))
    /**
     * 在 `CooShaderEffectPlayBuilder` 中配置 `uniform`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`uniform(name = name, value = value)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @param value 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun uniform(name: String, value: Int) = uniform(name, PostEffectParamValue.IntValue(value))
    /**
     * 在 `CooShaderEffectPlayBuilder` 中配置 `uniform`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`uniform(name = name, value = value)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @param value 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun uniform(name: String, value: Long) = uniform(name, PostEffectParamValue.LongValue(value))
    /**
     * 在 `CooShaderEffectPlayBuilder` 中配置 `uniform`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`uniform(name = name, value = value)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @param value 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun uniform(name: String, value: Float) = uniform(name, PostEffectParamValue.FloatValue(value))
    /**
     * 在 `CooShaderEffectPlayBuilder` 中配置 `uniform`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`uniform(name = name, value = value)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @param value 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun uniform(name: String, value: Double) = uniform(name, PostEffectParamValue.DoubleValue(value))

    /**
     * 在 `CooShaderEffectPlayBuilder` 中配置 `uniform`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`uniform(name = name, value = value)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @param value 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun uniform(name: String, value: CooUniformValue) = apply {
        uniform(name, value.toPostEffectValue())
    }

    /**
     * 在 `CooShaderEffectPlayBuilder` 中配置 `texture`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`texture(name = name, texture = texture)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @param texture 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun texture(name: String, texture: ResourceLocation) = apply {
        params.resource(name, texture)
    }

    private fun uniform(name: String, value: PostEffectParamValue) = apply {
        require(name.isNotBlank()) { "Shader effect uniform name cannot be blank" }
        uniformNames += name
        params.put(name, value)
    }

    /**
     * 根据输入和 `CooShaderEffectPlayBuilder` 当前配置创建 `build` 结果；返回对象保留本次配置的语义。
     *
     * 示例：`build()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
    internal fun build(): CooShaderEffectPlayRequest {
        return CooShaderEffectPlayRequest(durationTicks, params.build(), uniformNames.toSet())
    }

    private fun CooUniformValue.toPostEffectValue(): PostEffectParamValue {
        return toPostEffectParamValue()
    }
}

internal data class CooShaderEffectPlayRequest(
    val durationTicks: Int,
    val params: PostEffectParams,
    val uniformNames: Set<String>
)

/**
 * 单 pass facade 与高级 graph builder 共用同一个 [CooRenderPipelineBuilder]。
 *
 * 内部构造函数由 [CooShaderEffects.register] 创建，确保 effect id 与最终 Pipeline id 一致。
 *
 * @param id 要注册的 shader effect 资源标识
 */
class CooShaderEffectBuilder internal constructor(id: ResourceLocation) {
    private val graph = CooRenderPipelineBuilder<Any>(id, CooPipelineDomain.SCREEN)
    private val main = CooPipelineNodeBuilder<Any>("main", CooPipelineNodeKind.FULLSCREEN)
    private var mainTouched = false

    /**
     * 在 `CooShaderEffectBuilder` 中配置 `fragment`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`fragment(shader = shader)`。
     *
     * @param shader 要加载、编译或绑定的 shader 资源
     */
    fun fragment(shader: ResourceLocation) = apply {
        mainTouched = true
        main.fragment(shader)
    }

    /**
     * 在 `CooShaderEffectBuilder` 中配置 `inputSceneColor`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
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
        textureSlot: Int? = null
    ) = apply {
        mainTouched = true
        if (textureSlot == null) main.inputSceneColor(sampler, optional)
        else main.inputSceneColor(sampler, optional, textureSlot)
    }

    /**
     * 在 `CooShaderEffectBuilder` 中配置 `inputSceneDepth`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
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
        optional: Boolean = true,
        textureSlot: Int? = null
    ) = apply {
        mainTouched = true
        if (textureSlot == null) main.inputSceneDepth(sampler, optional)
        else main.inputSceneDepth(sampler, optional, textureSlot)
    }

    /** 播放时通过 `texture(sampler, ...)` 提供的纹理端口。 */
    fun inputTexture(
        sampler: String,
        optional: Boolean = false,
        textureSlot: Int? = null
    ) = apply {
        mainTouched = true
        if (textureSlot == null) main.input(sampler, CooPipelineTextureSource.Parameter(sampler), optional)
        else main.input(sampler, CooPipelineTextureSource.Parameter(sampler), optional, textureSlot)
    }

    /**
     * 在 `CooShaderEffectBuilder` 中配置 `outputToScreen`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`outputToScreen()`。
     */
    fun outputToScreen() = apply {
        mainTouched = true
        main.outputToScreen()
    }

    /** 声明高级图中的一个全屏节点。 */
    fun pass(name: String, block: CooPipelineNodeBuilder<Any>.() -> Unit): CooPipelineNode {
        require(name != "main") { "Shader effect node name is reserved: main" }
        return graph.pass(name, block)
    }

    /**
     * 在 `CooShaderEffectBuilder` 中配置 `pingPong`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
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
        block: CooPipelineNodeBuilder<Any>.() -> Unit
    ): CooPipelineNode {
        require(name != "main") { "Shader effect node name is reserved: main" }
        return graph.pingPong(name, iterations, feedbackSampler, block)
    }

    /**
     * 在 `CooShaderEffectBuilder` 中配置 `line`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`line(output = output, input = input)`。
     *
     * @param output 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param input 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun line(output: CooPipelineTextureSource, input: CooPipelineLineInput) = apply {
        graph.line(output, input)
    }

    /**
     * 在 `CooShaderEffectBuilder` 中配置 `sceneColor`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`sceneColor()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun sceneColor(): CooPipelineTextureSource = graph.sceneColor()
    /**
     * 在 `CooShaderEffectBuilder` 中配置 `sceneDepth`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`sceneDepth()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun sceneDepth(): CooPipelineTextureSource = graph.sceneDepth()
    /**
     * 在 `CooShaderEffectBuilder` 中配置 `mask`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`mask()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun mask(): CooPipelineTextureSource = graph.mask()
    /**
     * 在 `CooShaderEffectBuilder` 中配置 `bloom`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`bloom()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun bloom(): CooPipelineTextureSource = graph.bloom()
    /**
     * 在 `CooShaderEffectBuilder` 中配置 `texture`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`texture(texture = texture)`。
     *
     * @param texture 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun texture(texture: ResourceLocation): CooPipelineTextureSource = graph.texture(texture)
    /**
     * 在 `CooShaderEffectBuilder` 中配置 `framebuffer`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
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
        graph.framebuffer(target, attachment)

    /**
     * 在 `CooShaderEffectBuilder` 中配置 `screenTarget`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`screenTarget()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun screenTarget(): CooPipelineTarget = graph.screenTarget()
    /**
     * 在 `CooShaderEffectBuilder` 中配置 `maskTarget`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`maskTarget()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun maskTarget(): CooPipelineTarget = graph.maskTarget()
    /**
     * 在 `CooShaderEffectBuilder` 中配置 `bloomTarget`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`bloomTarget()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun bloomTarget(): CooPipelineTarget = graph.bloomTarget()
    /**
     * 在 `CooShaderEffectBuilder` 中配置 `temporaryTarget`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`temporaryTarget()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun temporaryTarget(): CooPipelineTarget = graph.temporaryTarget()
    /**
     * 在 `CooShaderEffectBuilder` 中配置 `framebufferTarget`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
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
        graph.framebufferTarget(target, attachment)

    /**
     * 根据输入和 `CooShaderEffectBuilder` 当前配置创建 `build` 结果；返回对象保留本次配置的语义。
     *
     * 示例：`build()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
    internal fun build(): CooShaderEffect {
        if (mainTouched || !graph.hasNodes()) {
            graph.addPreparedNode(main)
        }
        val pipeline = graph.build()
        val compiled = requireNotNull(CooPipelinePostEffectCompiler.compile(pipeline)) {
            "Shader effect '${pipeline.id}' has no fullscreen nodes"
        }
        return CooShaderEffect(pipeline, compiled.type, compiled.defaultParams)
    }
}
