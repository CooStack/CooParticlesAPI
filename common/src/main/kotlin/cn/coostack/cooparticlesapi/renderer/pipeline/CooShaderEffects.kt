package cn.coostack.cooparticlesapi.renderer.pipeline

import cn.coostack.cooparticlesapi.renderer.post.CooPostEffects
import cn.coostack.cooparticlesapi.renderer.post.PostEffectLifecycle
import cn.coostack.cooparticlesapi.renderer.post.PostEffectParamValue
import cn.coostack.cooparticlesapi.renderer.post.PostEffectParams
import cn.coostack.cooparticlesapi.renderer.post.PostEffectParamsBuilder
import cn.coostack.cooparticlesapi.renderer.post.PostEffectRuntimeRegistry
import cn.coostack.cooparticlesapi.renderer.post.PostEffectType
import cn.coostack.cooparticlesapi.renderer.post.withParamUniforms
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/** 独立屏幕 ShaderEffect 的注册和播放入口。 */
object CooShaderEffects {
    private val effects = LinkedHashMap<ResourceLocation, CooShaderEffect>()

    fun register(id: ResourceLocation, block: CooShaderEffectBuilder.() -> Unit): CooShaderEffect {
        require(id !in effects) { "Shader effect already registered: $id" }
        val effect = CooShaderEffectBuilder(id).apply(block).build()
        PostEffectRuntimeRegistry.registerType(effect.postType)
        effects[id] = effect
        return effect
    }

    fun get(id: ResourceLocation): CooShaderEffect? = effects[id]

    fun all(): Collection<CooShaderEffect> = effects.values.toList()
}

/** 注册后的不可变屏幕渲染图。 */
class CooShaderEffect internal constructor(
    val pipeline: CooRenderPipeline<Any>,
    internal val postType: PostEffectType,
    private val defaultParams: PostEffectParams
) {
    val id: ResourceLocation get() = pipeline.id

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

class CooShaderEffectPlayback internal constructor(
    val effect: CooShaderEffect,
    val instanceId: String,
    private val stopAction: (String) -> Unit,
    private val playingQuery: (String) -> Boolean
) {
    private var stopped = false

    fun stop() {
        if (stopped) return
        stopped = true
        stopAction(instanceId)
    }

    fun isPlaying(): Boolean {
        return !stopped && playingQuery(instanceId)
    }
}

class CooShaderEffectPlayBuilder {
    private var durationTicks = 1
    private val params = PostEffectParamsBuilder()
    private val uniformNames = linkedSetOf<String>()

    fun duration(ticks: Int) = apply {
        require(ticks > 0) { "Shader effect duration must be greater than zero" }
        durationTicks = ticks
    }

    fun uniform(name: String, value: Boolean) = uniform(name, PostEffectParamValue.BoolValue(value))
    fun uniform(name: String, value: Int) = uniform(name, PostEffectParamValue.IntValue(value))
    fun uniform(name: String, value: Long) = uniform(name, PostEffectParamValue.LongValue(value))
    fun uniform(name: String, value: Float) = uniform(name, PostEffectParamValue.FloatValue(value))
    fun uniform(name: String, value: Double) = uniform(name, PostEffectParamValue.DoubleValue(value))

    fun uniform(name: String, value: CooUniformValue) = apply {
        uniform(name, value.toPostEffectValue())
    }

    fun texture(name: String, texture: ResourceLocation) = apply {
        params.resource(name, texture)
    }

    private fun uniform(name: String, value: PostEffectParamValue) = apply {
        require(name.isNotBlank()) { "Shader effect uniform name cannot be blank" }
        uniformNames += name
        params.put(name, value)
    }

    internal fun build(): CooShaderEffectPlayRequest {
        return CooShaderEffectPlayRequest(durationTicks, params.build(), uniformNames.toSet())
    }

    private fun CooUniformValue.toPostEffectValue(): PostEffectParamValue {
        return when (this) {
            is CooUniformValue.FloatValue -> PostEffectParamValue.FloatValue(value)
            is CooUniformValue.IntValue -> PostEffectParamValue.IntValue(value)
            is CooUniformValue.Vec2Value -> PostEffectParamValue.Vec2Value(x, y)
            is CooUniformValue.Vec3Value -> PostEffectParamValue.Vec3Value(x.toDouble(), y.toDouble(), z.toDouble())
            is CooUniformValue.Vec4Value -> PostEffectParamValue.ColorValue(x, y, z, w)
        }
    }
}

internal data class CooShaderEffectPlayRequest(
    val durationTicks: Int,
    val params: PostEffectParams,
    val uniformNames: Set<String>
)

/**
 * 单 pass facade 与高级 graph builder 共用同一个 [CooRenderPipelineBuilder]。
 */
class CooShaderEffectBuilder internal constructor(id: ResourceLocation) {
    private val graph = CooRenderPipelineBuilder<Any>(id, CooPipelineDomain.SCREEN)
    private val main = CooPipelineNodeBuilder<Any>("main", CooPipelineNodeKind.FULLSCREEN)
    private var mainTouched = false

    fun fragment(shader: ResourceLocation) = apply {
        mainTouched = true
        main.fragment(shader)
    }

    fun inputSceneColor(
        sampler: String = "SceneColor",
        optional: Boolean = false,
        textureSlot: Int? = null
    ) = apply {
        mainTouched = true
        if (textureSlot == null) main.inputSceneColor(sampler, optional)
        else main.inputSceneColor(sampler, optional, textureSlot)
    }

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

    fun outputToScreen() = apply {
        mainTouched = true
        main.outputToScreen()
    }

    /** 声明高级图中的一个全屏节点。 */
    fun pass(name: String, block: CooPipelineNodeBuilder<Any>.() -> Unit): CooPipelineNode {
        require(name != "main") { "Shader effect node name is reserved: main" }
        return graph.pass(name, block)
    }

    fun pingPong(
        name: String,
        iterations: Int,
        feedbackSampler: String = "Input",
        block: CooPipelineNodeBuilder<Any>.() -> Unit
    ): CooPipelineNode {
        require(name != "main") { "Shader effect node name is reserved: main" }
        return graph.pingPong(name, iterations, feedbackSampler, block)
    }

    fun line(output: CooPipelineTextureSource, input: CooPipelineLineInput) = apply {
        graph.line(output, input)
    }

    fun sceneColor(): CooPipelineTextureSource = graph.sceneColor()
    fun sceneDepth(): CooPipelineTextureSource = graph.sceneDepth()
    fun mask(): CooPipelineTextureSource = graph.mask()
    fun bloom(): CooPipelineTextureSource = graph.bloom()
    fun texture(texture: ResourceLocation): CooPipelineTextureSource = graph.texture(texture)
    fun framebuffer(target: ResourceLocation, attachment: Int = 0): CooPipelineTextureSource =
        graph.framebuffer(target, attachment)

    fun screenTarget(): CooPipelineTarget = graph.screenTarget()
    fun maskTarget(): CooPipelineTarget = graph.maskTarget()
    fun bloomTarget(): CooPipelineTarget = graph.bloomTarget()
    fun temporaryTarget(): CooPipelineTarget = graph.temporaryTarget()
    fun framebufferTarget(target: ResourceLocation, attachment: Int = 0): CooPipelineTarget =
        graph.framebufferTarget(target, attachment)

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
