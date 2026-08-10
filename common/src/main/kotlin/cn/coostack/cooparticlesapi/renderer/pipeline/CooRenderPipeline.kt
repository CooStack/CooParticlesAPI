package cn.coostack.cooparticlesapi.renderer.pipeline

import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameStage
import net.minecraft.resources.ResourceLocation

enum class CooPipelineDomain {
    ENTITY,
    BLOCK,
    GENERIC,
    SCREEN
}

enum class CooTerrainLayer {
    SOLID,
    CUTOUT_MIPPED,
    CUTOUT,
    TRANSLUCENT,
    INHERIT
}

/** BaseUV 始终保留方块图集坐标；该枚举只控制独立 EffectUV。 */
enum class CooEffectUvMode(internal val shaderValue: Int) {
    BASE_UV(0),
    FACE_LOCAL(1),
    WORLD_XZ(2),
    WORLD_XY(3),
    WORLD_YZ(4)
}

sealed interface CooUniformValue {
    data class FloatValue(val value: Float) : CooUniformValue
    data class IntValue(val value: Int) : CooUniformValue
    data class Vec2Value(val x: Float, val y: Float) : CooUniformValue
    data class Vec3Value(val x: Float, val y: Float, val z: Float) : CooUniformValue
    data class Vec4Value(val x: Float, val y: Float, val z: Float, val w: Float) : CooUniformValue
}

fun interface CooUniformProvider<in T : Any> {
    fun resolve(subject: T): CooUniformValue
}

enum class CooPipelineNodeKind {
    WORLD,
    FULLSCREEN,
    PING_PONG
}

data class CooPipelineIteration(
    val index: Int,
    val count: Int
) {
    val isPing: Boolean get() = index % 2 == 0
}

fun interface CooIterationUniformProvider {
    fun resolve(iteration: CooPipelineIteration): CooUniformValue
}

internal data class CooPingPongExecution(
    val iterations: Int,
    val feedbackSampler: String,
    val uniforms: Map<String, CooIterationUniformProvider>
)

sealed interface CooPipelineShader {
    /** Minecraft core shader id，主要用于 terrain/world 节点。 */
    data class Core(val id: ResourceLocation) : CooPipelineShader

    /** 独立 vertex/fragment 源，主要用于全屏节点。 */
    data class Stages(
        val vertex: ResourceLocation?,
        val fragment: ResourceLocation
    ) : CooPipelineShader
}

enum class CooPipelineOutputSemantic {
    COLOR,
    DEPTH,
    MASK
}

/** 可作为 line 输出端的纹理资源。 */
sealed interface CooPipelineTextureSource {
    data class Texture(val texture: ResourceLocation) : CooPipelineTextureSource
    data class Parameter(val name: String) : CooPipelineTextureSource
    data object BlockAtlas : CooPipelineTextureSource
    data object SceneColor : CooPipelineTextureSource
    data object SceneDepth : CooPipelineTextureSource
    data class FramebufferColor(val target: ResourceLocation, val attachment: Int = 0) : CooPipelineTextureSource
    data object Mask : CooPipelineTextureSource
    data object Temporary : CooPipelineTextureSource
    data object Bloom : CooPipelineTextureSource
}

/** 一个节点上的 sampler 输入端口。 */
@ConsistentCopyVisibility
data class CooPipelineInputPort internal constructor(
    val node: String,
    val sampler: String,
    val optional: Boolean,
    val textureSlot: Int
) : CooPipelineLineInput

/** 一个节点写出的 FBO attachment；它可以直接连接到另一个节点的输入端口。 */
@ConsistentCopyVisibility
data class CooPipelineOutputPort internal constructor(
    val node: String,
    val name: String,
    val semantic: CooPipelineOutputSemantic,
    val attachment: Int
) : CooPipelineTextureSource

/** line 的输入端，可以是 shader sampler，也可以是 graph 的最终输出端口。 */
sealed interface CooPipelineLineInput

sealed interface CooPipelineTarget : CooPipelineLineInput {
    data object World : CooPipelineTarget
    data object FinalScreen : CooPipelineTarget
    data object Mask : CooPipelineTarget
    data object Temporary : CooPipelineTarget
    data object Bloom : CooPipelineTarget
    data class FramebufferColor(val target: ResourceLocation, val attachment: Int = 0) : CooPipelineTarget
}

/** 一条完整的 `output -> input` 连线。 */
@ConsistentCopyVisibility
data class CooPipelineLine internal constructor(
    val output: CooPipelineTextureSource,
    val input: CooPipelineLineInput
)

class CooPipelineNode internal constructor(
    val name: String,
    val kind: CooPipelineNodeKind,
    val shader: CooPipelineShader?,
    inputs: List<CooPipelineInputPort>,
    outputs: List<CooPipelineOutputPort>,
    uniforms: Map<String, CooUniformProvider<Any>>,
    internal val pingPong: CooPingPongExecution?,
    val order: Int,
    internal val sequence: Int
) {
    val inputs: List<CooPipelineInputPort> = inputs.toList()
    val outputs: List<CooPipelineOutputPort> = outputs.toList()
    internal val uniforms: Map<String, CooUniformProvider<Any>> = uniforms.toMap()

    fun input(sampler: String): CooPipelineInputPort {
        return requireNotNull(inputs.firstOrNull { it.sampler == sampler }) {
            "Node '$name' has no input port '$sampler'"
        }
    }

    /**
     * 返回绑定到指定纹理单元的输入端口。
     *
     * @param textureSlot shader sampler 使用的纹理单元序号
     * @return 对应纹理单元的输入端口
     */
    fun input(textureSlot: Int): CooPipelineInputPort {
        return requireNotNull(inputs.singleOrNull { it.textureSlot == textureSlot }) {
            "Node '$name' has no unique input port at texture slot $textureSlot"
        }
    }

    fun output(name: String): CooPipelineOutputPort {
        return requireNotNull(outputs.firstOrNull { it.name == name }) {
            "Node '${this.name}' has no output port '$name'"
        }
    }

    fun color(attachment: Int = 0): CooPipelineOutputPort {
        return requireNotNull(outputs.firstOrNull {
            it.semantic == CooPipelineOutputSemantic.COLOR && it.attachment == attachment
        }) { "Node '$name' has no color attachment $attachment" }
    }

    fun mask(): CooPipelineOutputPort {
        return requireNotNull(outputs.firstOrNull { it.semantic == CooPipelineOutputSemantic.MASK }) {
            "Node '$name' does not declare a mask output"
        }
    }

    fun resolveUniform(name: String, subject: Any): CooUniformValue? {
        return uniforms[name]?.resolve(subject)
    }

    internal fun withUniform(name: String, provider: CooUniformProvider<Any>): CooPipelineNode {
        return CooPipelineNode(
            name = this.name,
            kind = kind,
            shader = shader,
            inputs = inputs,
            outputs = outputs,
            uniforms = uniforms + (name to provider),
            pingPong = pingPong,
            order = order,
            sequence = sequence
        )
    }
}

internal data class CooPipelineParameterBinding(
    val node: String,
    val uniform: String
)

/**
 * 不可变渲染图。节点描述 shader 卡片，line 描述 FBO attachment 到 sampler 的连接。
 */
class CooRenderPipeline<out T : Any> internal constructor(
    val id: ResourceLocation,
    val domain: CooPipelineDomain,
    val terrainLayer: CooTerrainLayer,
    val effectUvMode: CooEffectUvMode,
    nodes: List<CooPipelineNode>,
    lines: List<CooPipelineLine>,
    private val primaryNode: String?,
    parameterBindings: Map<String, List<CooPipelineParameterBinding>>
) {
    val nodes: List<CooPipelineNode> = nodes.toList()
    val lines: List<CooPipelineLine> = lines.toList()
    internal val parameterBindings: Map<String, List<CooPipelineParameterBinding>> =
        parameterBindings.mapValues { it.value.toList() }

    val stages: Set<RenderFrameStage>
        get() = buildSet {
            if (nodes.any { it.kind == CooPipelineNodeKind.WORLD }) add(RenderFrameStage.WORLD_PASS)
            if (nodes.any { it.kind != CooPipelineNodeKind.WORLD }) add(RenderFrameStage.FRAME_POST)
        }

    val terrainShader: ResourceLocation?
        get() = nodes.asSequence()
            .filter { it.kind == CooPipelineNodeKind.WORLD }
            .mapNotNull { (it.shader as? CooPipelineShader.Core)?.id }
            .firstOrNull()

    fun blurSigma(value: Float): CooRenderPipeline<T> = withFloatParameter("blurSigma") { value }

    fun blurRange(value: Float): CooRenderPipeline<T> = withFloatParameter("blurRange") { value }

    fun <R : Any> intensity(provider: (R) -> Float): CooRenderPipeline<R> {
        return withFloatParameter("intensity", provider)
    }

    fun uniform(name: String, value: Float): CooRenderPipeline<T> {
        return withPrimaryUniform(name, CooUniformProvider { CooUniformValue.FloatValue(value) })
    }

    internal fun uniformValue(name: String, value: CooUniformValue): CooRenderPipeline<T> {
        return withPrimaryUniform(name, CooUniformProvider { value })
    }

    internal fun uniformValues(values: Map<String, CooUniformValue>): CooRenderPipeline<T> {
        var result: CooRenderPipeline<T> = this
        values.forEach { (name, value) ->
            result = result.uniformValue(name, value)
        }
        return result
    }

    fun <R : Any> uniform(name: String, provider: (R) -> Float): CooRenderPipeline<R> {
        val resolved = CooUniformProvider<Any> { subject ->
            @Suppress("UNCHECKED_CAST")
            CooUniformValue.FloatValue(provider(subject as R))
        }
        return withPrimaryUniform(name, resolved)
    }

    fun <R : Any> uniformValue(name: String, provider: CooUniformProvider<R>): CooRenderPipeline<R> {
        val resolved = CooUniformProvider<Any> { subject ->
            @Suppress("UNCHECKED_CAST")
            provider.resolve(subject as R)
        }
        return withPrimaryUniform(name, resolved)
    }

    internal fun resolveUniform(name: String, subject: Any): CooUniformValue? {
        val node = primaryNode?.let { nodeName -> nodes.firstOrNull { it.name == nodeName } } ?: return null
        return node.resolveUniform(name, subject)
    }

    internal fun resolveUniform(node: String, name: String, subject: Any): CooUniformValue? {
        return nodes.firstOrNull { it.name == node }?.resolveUniform(name, subject)
    }

    internal fun resolveIntensity(subject: Any): Float {
        val binding = parameterBindings["intensity"]?.firstOrNull() ?: return 0F
        val value = resolveUniform(binding.node, binding.uniform, subject)
        return (value as? CooUniformValue.FloatValue)?.value ?: 0F
    }

    private fun <R : Any> withFloatParameter(
        name: String,
        provider: (R) -> Float
    ): CooRenderPipeline<R> {
        val bindings = requireNotNull(parameterBindings[name]) {
            "Pipeline '$id' does not declare parameter '$name'"
        }
        val resolved = CooUniformProvider<Any> { subject ->
            @Suppress("UNCHECKED_CAST")
            CooUniformValue.FloatValue(provider(subject as R))
        }
        var updated = nodes
        bindings.forEach { binding ->
            updated = updated.map { node ->
                if (node.name == binding.node) node.withUniform(binding.uniform, resolved) else node
            }
        }
        return copy(nodes = updated)
    }

    private fun <R : Any> withPrimaryUniform(
        name: String,
        provider: CooUniformProvider<Any>
    ): CooRenderPipeline<R> {
        val target = requireNotNull(primaryNode) { "Pipeline '$id' has no primary shader node" }
        return copy(nodes = nodes.map { if (it.name == target) it.withUniform(name, provider) else it })
    }

    private fun <R : Any> copy(nodes: List<CooPipelineNode>): CooRenderPipeline<R> {
        return CooRenderPipeline(
            id = id,
            domain = domain,
            terrainLayer = terrainLayer,
            effectUvMode = effectUvMode,
            nodes = nodes,
            lines = lines,
            primaryNode = primaryNode,
            parameterBindings = parameterBindings
        )
    }
}
