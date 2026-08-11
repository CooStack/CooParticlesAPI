package cn.coostack.cooparticlesapi.renderer.pipeline

import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameStage
import net.minecraft.resources.ResourceLocation

/**
 * Pipeline 的使用域。
 *
 * 域会影响默认节点的创建方式、可接收的输入资源以及 runtime 选择的绘制路径。
 * 例如实体域会自动准备 world 节点，screen 域则只适合全屏或后处理节点。
 */
enum class CooPipelineDomain {
    /** 绑定到实体实例的世界空间绘制，节点会按实体逐个求值 uniform。 */
    ENTITY,

    /** 绑定到方块状态的地形绘制，会使用方块图集作为默认纹理输入。 */
    BLOCK,

    /** 不附带实体或方块语义的通用图，适合由渲染管理器直接触发。 */
    GENERIC,

    /** 只在屏幕空间运行的后处理图，输入通常来自场景颜色、深度或其他 pass。 */
    SCREEN
}

/**
 * 方块 Pipeline 采用的地形渲染层。
 *
 * 该值会影响 Minecraft RenderType 的深度写入、透明度测试和 mipmap 采样规则；
 * [INHERIT] 表示沿用调用方或基础渲染层，而不是强制改写原始层。
 */
enum class CooTerrainLayer {
    /** 不透明几何，通常开启深度写入。 */
    SOLID,

    /** 带 mipmap 的 cutout 材质，适合远处仍需硬裁剪的纹理。 */
    CUTOUT_MIPPED,

    /** 不带 mipmap 的 cutout 材质，适合保持像素边缘锐利的纹理。 */
    CUTOUT,

    /** 半透明材质，按绘制顺序混合颜色并避免透明像素写入深度。 */
    TRANSLUCENT,

    /** 继承基础渲染层，由当前 backend 决定实际 RenderType。 */
    INHERIT
}

/**
 * BaseUV 始终保留方块图集坐标；该枚举只控制独立 EffectUV。
 *
 * @property shaderValue 上传给 terrain shader 的整数模式值，必须与 GLSL 中的模式常量一致
 */
enum class CooEffectUvMode(internal val shaderValue: Int) {
    /** 直接复用 Minecraft 方块图集坐标，不改变基础材质采样。 */
    BASE_UV(0),

    /** 使用当前面的局部坐标生成独立 EffectUV。 */
    FACE_LOCAL(1),

    /** 使用世界 XZ 平面投影生成 EffectUV，适合地面方向的流动效果。 */
    WORLD_XZ(2),

    /** 使用世界 XY 平面投影生成 EffectUV，适合正面投影效果。 */
    WORLD_XY(3),

    /** 使用世界 YZ 平面投影生成 EffectUV，适合侧面投影效果。 */
    WORLD_YZ(4)
}

fun interface CooUniformProvider<in T : Any> {
    /**
     * 根据当前渲染对象计算 uniform。
     *
     * 示例：`CooUniformProvider<Entity> { CooUniformValue.FloatValue(it.age.toFloat()) }`。
     *
     * @param subject 当前正在绘制的实体、方块状态或其他业务对象
     * @return 要上传到 shader 的 uniform 值
     */
    fun resolve(subject: T): CooUniformValue
}

/** Pipeline 节点的执行模型，决定输入来源和所在帧阶段。 */
enum class CooPipelineNodeKind {
    /** 在世界几何绘制期间执行，通常按实体或地形面写入颜色或 mask。 */
    WORLD,

    /** 在屏幕空间执行一次，使用全屏四边形读取上游纹理。 */
    FULLSCREEN,

    /** 在两个临时目标之间交替执行多次，用于模糊、扩散等反馈算法。 */
    PING_PONG
}

/**
 * 一次 ping-pong 迭代的只读上下文。
 *
 * @param index 从 0 开始的当前迭代索引
 * @param count 本次节点计划执行的总迭代次数
 */
data class CooPipelineIteration(
    val index: Int,
    val count: Int
) {
    val isPing: Boolean get() = index % 2 == 0
}

fun interface CooIterationUniformProvider {
    /**
     * 根据当前 ping-pong 迭代计算 uniform。
     *
     * @param iteration 当前迭代索引和总次数；可通过 [CooPipelineIteration.isPing] 区分两侧目标
     * @return 本次迭代要上传的 uniform 值
     */
    fun resolve(iteration: CooPipelineIteration): CooUniformValue
}

internal data class CooPingPongExecution(
    val iterations: Int,
    val feedbackSampler: String,
    val uniforms: Map<String, CooIterationUniformProvider>
)

/**
 * Pipeline 节点使用的 shader 来源。
 *
 * [Core] 交给 Minecraft core shader 管线；[Stages] 由 API 加载 vertex/fragment 源，
 * 选择的实现会影响资源重载、uniform 绑定和节点允许的执行域。
 */
sealed interface CooPipelineShader {
    /**
     * Minecraft core shader id，主要用于 terrain/world 节点。
     *
     * @property id 通过资源管理器解析的 core shader 标识
     * 示例：`CooPipelineShader.Core(ResourceLocation("minecraft", "rendertype_solid"))`。
     */
    data class Core(val id: ResourceLocation) : CooPipelineShader

    /**
     * 独立 vertex/fragment 源，主要用于全屏节点。
     *
     * @property vertex 可选 vertex shader 资源；为空时使用 backend 默认全屏顶点源
     * @property fragment 必填 fragment shader 资源
     */
    data class Stages(
        val vertex: ResourceLocation?,
        val fragment: ResourceLocation
    ) : CooPipelineShader
}

/** 节点 framebuffer attachment 的语义，决定它可连接到哪些目标以及 backend 如何处理它。 */
enum class CooPipelineOutputSemantic {
    /** 普通颜色输出，会参与颜色合成或作为后续 sampler 输入。 */
    COLOR,

    /** 深度输出，用于深度测试、遮挡或后续深度采样。 */
    DEPTH,

    /** 蒙版输出，只表示效果覆盖范围，通常供 bloom 或条件合成使用。 */
    MASK
}

/**
 * 可作为 line 输出端的纹理资源。
 *
 * 不同来源会影响资源解析和生命周期：外部纹理按资源位置加载，场景与临时纹理由
 * backend 管理，节点输出则引用 graph 中已经创建的 attachment。
 */
sealed interface CooPipelineTextureSource {
    /**
     * 直接从资源管理器加载指定纹理。
     * @property texture 纹理资源位置
     */
    data class Texture(val texture: ResourceLocation) : CooPipelineTextureSource

    /**
     * 通过参数名解析运行时纹理。
     * @property name post effect 参数或 sampler 名称
     */
    data class Parameter(val name: String) : CooPipelineTextureSource

    /** Minecraft 方块图集，影响 world/block 节点的基础材质采样。 */
    data object BlockAtlas : CooPipelineTextureSource

    /** 当前帧场景颜色副本；可用性受 backend capability 影响。 */
    data object SceneColor : CooPipelineTextureSource

    /** 当前帧场景深度纹理；可用性受 backend capability 影响。 */
    data object SceneDepth : CooPipelineTextureSource

    /**
     * 指定 framebuffer 的颜色 attachment。
     * @property target framebuffer 的资源标识
     * @property attachment 颜色 attachment 索引，默认读取 0
     */
    data class FramebufferColor(val target: ResourceLocation, val attachment: Int = 0) : CooPipelineTextureSource

    /** 当前图生成的 mask 纹理。 */
    data object Mask : CooPipelineTextureSource

    /** backend 分配的临时中间纹理。 */
    data object Temporary : CooPipelineTextureSource

    /** bloom 亮部提取或模糊后的中间纹理。 */
    data object Bloom : CooPipelineTextureSource
}

/**
 * 一个节点上的 sampler 输入端口。
 *
 * @param node 所属节点名称；必须与 Pipeline 中的节点名称一致
 * @param sampler shader 中声明的 sampler 名称
 * @param optional 是否允许未连接；为 false 时编译阶段会拒绝缺失连线
 * @param textureSlot shader 读取该 sampler 时使用的纹理单元
 */
@ConsistentCopyVisibility
data class CooPipelineInputPort internal constructor(
    val node: String,
    val sampler: String,
    val optional: Boolean,
    val textureSlot: Int
) : CooPipelineLineInput

/**
 * 一个节点写出的 FBO attachment；它可以直接连接到另一个节点的输入端口。
 *
 * @param node 产生该 attachment 的节点名称
 * @param name attachment 的逻辑名称
 * @param semantic 颜色、深度或 mask 语义，影响目标解析和后处理用途
 * @param attachment fragment output location 对应的颜色槽位
 */
@ConsistentCopyVisibility
data class CooPipelineOutputPort internal constructor(
    val node: String,
    val name: String,
    val semantic: CooPipelineOutputSemantic,
    val attachment: Int
) : CooPipelineTextureSource

/** line 的输入端，可以是 shader sampler，也可以是 graph 的最终输出端口。 */
sealed interface CooPipelineLineInput

/**
 * Pipeline 图的最终输出目标。
 *
 * 目标决定结果写回世界、屏幕、中间纹理或指定 framebuffer；最终目标只允许接收
 * [CooPipelineOutputPort]，避免把未经过节点处理的外部纹理直接写入目标。
 */
sealed interface CooPipelineTarget : CooPipelineLineInput {
    /** 把颜色结果合成回世界渲染目标。 */
    data object World : CooPipelineTarget

    /** 把结果写入最终屏幕目标。 */
    data object FinalScreen : CooPipelineTarget

    /** 把结果写入 mask 目标，供后续筛选或合成使用。 */
    data object Mask : CooPipelineTarget

    /** 把结果写入可复用的临时目标。 */
    data object Temporary : CooPipelineTarget

    /** 把结果写入 bloom 中间目标。 */
    data object Bloom : CooPipelineTarget

    /**
     * 把结果写入指定 framebuffer 的颜色 attachment。
     * @property target framebuffer 的资源标识
     * @property attachment 要写入的颜色 attachment 索引
     */
    data class FramebufferColor(val target: ResourceLocation, val attachment: Int = 0) : CooPipelineTarget
}

/**
 * 一条完整的 `output -> input` 连线。
 *
 * @param output 外部纹理或节点 attachment 来源
 * @param input sampler 输入端口或 Pipeline 最终目标
 */
@ConsistentCopyVisibility
data class CooPipelineLine internal constructor(
    val output: CooPipelineTextureSource,
    val input: CooPipelineLineInput
)

/**
 * 已编译的 Pipeline 节点。
 *
 * @param name 节点唯一名称，用于声明连线和定位 uniform
 * @param kind 节点执行模型
 * @param shader 节点使用的 shader 来源；纯数据节点可为空
 * @param inputs sampler 输入端口列表
 * @param outputs 节点写出的 attachment 列表
 * @param uniforms 节点的 uniform provider 映射
 * @param pingPong ping-pong 节点的迭代配置，普通节点为 null
 * @param order 同一阶段内的排序值
 * @param sequence 构建顺序，用于稳定排序
 */
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

    /**
     * 按 shader sampler 名称取得输入端口。
     *
     * 示例：`val source = node.input("SceneColor")`。
     *
     * @param sampler shader 中声明的 sampler 名称
     * @return 对应的输入端口
     * @throws IllegalArgumentException 节点未声明该 sampler 时抛出
     */
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

    /**
     * 按逻辑名称取得输出端口。
     *
     * 示例：`pipeline.line(world.color(), blur.input("Input"))`。
     *
     * @param name 输出端口名称，例如 `Color` 或 `Mask`
     * @return 对应的输出端口
     * @throws IllegalArgumentException 节点未声明该名称时抛出
     */
    fun output(name: String): CooPipelineOutputPort {
        return requireNotNull(outputs.firstOrNull { it.name == name }) {
            "Node '${this.name}' has no output port '$name'"
        }
    }

    /**
     * 返回指定 fragment output location 对应的输出端口。
     *
     * 示例：`node.output(1)` 对应 GLSL 的 `layout(location = 1)`。
     *
     * @param attachment fragment output location
     * @return 唯一占用该 attachment 的输出端口
     * @throws IllegalArgumentException attachment 不存在或不唯一时抛出
     */
    fun output(attachment: Int): CooPipelineOutputPort {
        return requireNotNull(outputs.singleOrNull { it.attachment == attachment }) {
            "Node '$name' has no unique output at attachment $attachment"
        }
    }

    /**
     * 取得指定颜色 attachment 的输出端口。
     *
     * @param attachment fragment output location，默认是 0
     * @return 对应的颜色输出端口
     * @throws IllegalArgumentException 节点没有该颜色 attachment 时抛出
     */
    fun color(attachment: Int = 0): CooPipelineOutputPort {
        return requireNotNull(outputs.firstOrNull {
            it.semantic == CooPipelineOutputSemantic.COLOR && it.attachment == attachment
        }) { "Node '$name' has no color attachment $attachment" }
    }

    /**
     * 取得节点声明的 mask 输出端口。
     *
     * 示例：`pipeline.line(world.mask(), CooPipelineTarget.Mask)`。
     *
     * @return 唯一的 mask 输出端口
     * @throws IllegalArgumentException 节点未声明 mask 输出时抛出
     */
    fun mask(): CooPipelineOutputPort {
        return requireNotNull(outputs.firstOrNull { it.semantic == CooPipelineOutputSemantic.MASK }) {
            "Node '$name' does not declare a mask output"
        }
    }

    /**
     * 为指定对象解析节点 uniform。
     *
     * @param name uniform 名称
     * @param subject 当前实体、方块状态或其他 provider 输入对象
     * @return provider 产生的 uniform 值；未声明或 provider 不存在时返回 null
     */
    fun resolveUniform(name: String, subject: Any): CooUniformValue? {
        return uniforms[name]?.resolve(subject)
    }

    /**
     * 返回只替换一个节点 uniform provider 的不可变节点副本。
     *
     * @param name 要写入的 uniform 名称
     * @param provider 解析当前绘制对象并返回 uniform 值的 provider
     * @return 保留节点其余配置的新节点
     */
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
 *
 * @property id Pipeline 的资源标识，用于注册、日志和资源查找
 * @property domain Pipeline 的使用域
 * @property terrainLayer 方块域采用的地形渲染层
 * @property effectUvMode 方块效果 UV 的生成模式
 * @property nodes 已编译节点列表
 * @property lines 节点输入与输出目标之间的连线
 * @param primaryNode 默认 uniform 写入的主节点名称
 * @param parameterBindings fluent 参数到 uniform 的绑定表
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

    /**
     * 设置模板声明的高斯模糊标准差。
     *
     * 示例：`CooPipelines.MASK_BLOOM.blurSigma(15F)`。
     *
     * @param value 传给 `blurSigma` 参数绑定的浮点值
     * @return 包含新参数值的不可变 Pipeline
     * @throws IllegalArgumentException 当前模板未声明 `blurSigma` 参数时抛出
     */
    fun blurSigma(value: Float): CooRenderPipeline<T> = parameter("blurSigma", value)

    /**
     * 设置模板声明的高斯模糊采样范围。
     *
     * 示例：`CooPipelines.MASK_BLOOM.blurRange(10F)`。
     *
     * @param value 传给 `blurRange` 参数绑定的浮点值
     * @return 包含新参数值的不可变 Pipeline
     * @throws IllegalArgumentException 当前模板未声明 `blurRange` 参数时抛出
     */
    fun blurRange(value: Float): CooRenderPipeline<T> = parameter("blurRange", value)

    /**
     * 设置亮部筛选阈值；值小于等于 0 时保留 mask 中的全部颜色。
     *
     * @param value 传给 `bloomThreshold` 参数绑定的阈值
     * @return 包含新阈值的不可变 Pipeline
     */
    fun bloomThreshold(value: Float): CooRenderPipeline<T> = parameter("bloomThreshold", value)

    /**
     * 设置亮部筛选的软阈值范围。
     *
     * @param value 传给 `bloomSoftKnee` 参数绑定的软阈值范围
     * @return 包含新软阈值的不可变 Pipeline
     */
    fun bloomSoftKnee(value: Float): CooRenderPipeline<T> = parameter("bloomSoftKnee", value)

    /**
     * 为模板声明的 `intensity` 参数绑定固定值。
     *
     * 示例：`CooPipelines.MASK_BLOOM.intensity(2.8F)`。
     *
     * @param value 固定强度
     * @return 包含固定强度的不可变 Pipeline
     * @throws IllegalArgumentException 当前模板未声明 `intensity` 参数时抛出
     */
    fun intensity(value: Float): CooRenderPipeline<T> = parameter("intensity", value)

    /**
     * 为模板声明的 `intensity` 参数绑定当前对象的动态值。
     *
     * 示例：`CooPipelines.MASK_BLOOM.intensity { entity: LaserEntity -> entity.bright }`。
     *
     * @param provider 接收当前 Pipeline 对象并返回强度的函数
     * @return 包含动态强度 provider 的不可变 Pipeline
     * @throws IllegalArgumentException 当前模板未声明 `intensity` 参数时抛出
     */
    fun intensity(provider: (@UnsafeVariance T) -> Float): CooRenderPipeline<T> {
        return parameter("intensity", provider)
    }

    /**
     * 为模板声明的参数绑定固定浮点值。
     *
     * 示例：`template.parameter("strength", 0.8F)`。
     *
     * @param name 构建模板时通过 `parameter(...)` 声明的参数名
     * @param value 绑定到全部目标 uniform 的浮点值
     * @return 包含新参数值的不可变 Pipeline
     * @throws IllegalArgumentException 当前模板未声明指定参数时抛出
     */
    fun parameter(name: String, value: Float): CooRenderPipeline<T> {
        return withParameter(name) { CooUniformValue.FloatValue(value) }
    }

    /**
     * 为模板声明的参数绑定固定 uniform 值。
     *
     * 示例：`template.parameter("tint", CooUniformValue.Vec3Value(1F, 0F, 0F))`。
     *
     * @param name 构建模板时通过 `parameter(...)` 声明的参数名
     * @param value 绑定到全部目标 uniform 的值
     * @return 包含新参数值的不可变 Pipeline
     * @throws IllegalArgumentException 当前模板未声明指定参数时抛出
     */
    fun parameter(name: String, value: CooUniformValue): CooRenderPipeline<T> {
        return withParameter(name) { value }
    }

    /**
     * 为 `entity<Nothing>` 等可复用模板绑定当前对象的动态浮点参数。
     *
     * 参数绑定到 world 节点时按每个实体绘制求值；绑定到 fullscreen 节点时，
     * runtime 会按解析值合批，避免把不同的屏幕级 uniform 静默混用。
     *
     * 示例：`template.parameter("strength") { entity: LaserEntity -> entity.bright }`。
     *
     * @param name 构建模板时通过 `parameter(...)` 声明的参数名
     * @param provider 接收当前 Pipeline 对象并返回浮点值的函数
     * @return 包含动态参数 provider 的不可变 Pipeline
     * @throws IllegalArgumentException 当前模板未声明指定参数时抛出
     */
    fun parameter(name: String, provider: (@UnsafeVariance T) -> Float): CooRenderPipeline<T> {
        return withParameter(name) { subject ->
            @Suppress("UNCHECKED_CAST")
            CooUniformValue.FloatValue(provider(subject as T))
        }
    }

    /**
     * 为模板声明的参数绑定任意类型的动态 uniform provider。
     *
     * 示例：`template.parameterValue("tint") { _: LaserEntity -> CooUniformValue.Vec3Value(1F, 0F, 0F) }`。
     *
     * @param name 构建模板时通过 `parameter(...)` 声明的参数名
     * @param provider 接收当前 Pipeline 对象并返回 uniform 值的 provider
     * @return 包含动态参数 provider 的不可变 Pipeline
     * @throws IllegalArgumentException 当前模板未声明指定参数时抛出
     */
    fun parameterValue(
        name: String,
        provider: CooUniformProvider<@UnsafeVariance T>
    ): CooRenderPipeline<T> {
        return withParameter(name) { subject ->
            @Suppress("UNCHECKED_CAST")
            provider.resolve(subject as T)
        }
    }

    /**
     * 替换主节点上的固定浮点 uniform。
     *
     * 示例：`pipeline.uniform("strength", 0.8F)`。
     *
     * @param name shader uniform 名称
     * @param value 固定浮点值
     * @return 包含新 uniform 的不可变 Pipeline
     * @throws IllegalArgumentException Pipeline 没有主节点时抛出
     */
    fun uniform(name: String, value: Float): CooRenderPipeline<T> {
        return withPrimaryUniform(name) { CooUniformValue.FloatValue(value) }
    }

    /**
     * 为内部参数绑定固定 uniform 值，并返回不可变 Pipeline 副本。
     *
     * @param name 参数绑定目标的 uniform 名称
     * @param value 要上传的固定值
     * @return 更新后的 Pipeline
     */
    internal fun uniformValue(name: String, value: CooUniformValue): CooRenderPipeline<T> {
        return withPrimaryUniform(name) { value }
    }

    /**
     * 批量绑定固定 uniform 值。
     *
     * @param values key 是 uniform 名称，value 是其固定上传值
     * @return 按输入映射依次更新后的 Pipeline
     */
    internal fun uniformValues(values: Map<String, CooUniformValue>): CooRenderPipeline<T> {
        var result: CooRenderPipeline<T> = this
        values.forEach { (name, value) ->
            result = result.uniformValue(name, value)
        }
        return result
    }

    /**
     * 替换主节点上按当前对象求值的浮点 uniform。
     *
     * 示例：`pipeline.uniform("strength") { entity: LaserEntity -> entity.bright }`。
     *
     * @param name shader uniform 名称
     * @param provider 接收当前 Pipeline 对象并返回浮点值的函数
     * @return 包含动态 uniform provider 的不可变 Pipeline
     * @throws IllegalArgumentException Pipeline 没有主节点时抛出
     */
    fun uniform(name: String, provider: (@UnsafeVariance T) -> Float): CooRenderPipeline<T> {
        return withPrimaryUniform(name) { subject ->
            @Suppress("UNCHECKED_CAST")
            CooUniformValue.FloatValue(provider(subject as T))
        }
    }

    /**
     * 替换主节点上按当前对象求值的任意类型 uniform。
     *
     * 示例：`pipeline.uniformValue("tint") { _: LaserEntity -> CooUniformValue.Vec3Value(1F, 0F, 0F) }`。
     *
     * @param name shader uniform 名称
     * @param provider 接收当前 Pipeline 对象并返回 uniform 值的 provider
     * @return 包含动态 uniform provider 的不可变 Pipeline
     * @throws IllegalArgumentException Pipeline 没有主节点时抛出
     */
    fun uniformValue(
        name: String,
        provider: CooUniformProvider<@UnsafeVariance T>
    ): CooRenderPipeline<T> {
        return withPrimaryUniform(name) { subject ->
            @Suppress("UNCHECKED_CAST")
            provider.resolve(subject as T)
        }
    }

    /**
     * 解析主节点上的 uniform，供 runtime 上传当前对象对应的值。
     *
     * @param name uniform 名称
     * @param subject 当前绘制对象
     * @return 解析结果；没有主节点或未声明时返回 null
     */
    internal fun resolveUniform(name: String, subject: Any): CooUniformValue? {
        val node = primaryNode?.let { nodeName -> nodes.firstOrNull { it.name == nodeName } } ?: return null
        return node.resolveUniform(name, subject)
    }

    /**
     * 解析指定节点上的 uniform。
     *
     * @param node 节点名称
     * @param name uniform 名称
     * @param subject 当前绘制对象
     * @return provider 结果；节点或 uniform 不存在时返回 null
     */
    internal fun resolveUniform(node: String, name: String, subject: Any): CooUniformValue? {
        return nodes.firstOrNull { it.name == node }?.resolveUniform(name, subject)
    }

    /**
     * 解析约定的 `intensity` 参数，用于 backend 计算效果强度。
     *
     * @param subject 当前绘制对象
     * @return intensity 浮点值；未绑定或类型不匹配时返回 0
     */
    internal fun resolveIntensity(subject: Any): Float {
        val binding = parameterBindings["intensity"]?.firstOrNull() ?: return 0F
        val value = resolveUniform(binding.node, binding.uniform, subject)
        return (value as? CooUniformValue.FloatValue)?.value ?: 0F
    }

    /** 把 provider 写入同名参数绑定的全部节点。 */
    private fun withParameter(
        name: String,
        provider: CooUniformProvider<Any>
    ): CooRenderPipeline<T> {
        val bindings = requireNotNull(parameterBindings[name]) {
            "Pipeline '$id' does not declare parameter '$name'"
        }
        var updated = nodes
        bindings.forEach { binding ->
            updated = updated.map { node ->
                if (node.name == binding.node) node.withUniform(binding.uniform, provider) else node
            }
        }
        return copy(nodes = updated)
    }

    /** 把 provider 写入 Pipeline 的主节点。 */
    private fun withPrimaryUniform(
        name: String,
        provider: CooUniformProvider<Any>
    ): CooRenderPipeline<T> {
        val target = requireNotNull(primaryNode) { "Pipeline '$id' has no primary shader node" }
        return copy(nodes = nodes.map { if (it.name == target) it.withUniform(name, provider) else it })
    }

    /** 使用新的不可变节点列表复制 Pipeline。 */
    private fun copy(nodes: List<CooPipelineNode>): CooRenderPipeline<T> {
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
