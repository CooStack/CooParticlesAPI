package cn.coostack.cooparticlesapi.coofx.render.compiled

import net.minecraft.resources.ResourceLocation

/**
 * CooFX 编译网格支持的索引元素类型。
 *
 * [UNSIGNED_SHORT] 表示无符号 16 位索引，单个元素占 2 字节；[UNSIGNED_INT] 表示无符号
 * 32 位索引，单个元素占 4 字节。该值进入批次键，上传后不得在同一批次内混用。
 */
enum class CooFxIndexType(val byteSize: Int) {
    /** 使用无符号 16 位索引，适合顶点数量不超过其表达范围的 primitive。 */
    UNSIGNED_SHORT(2),

    /** 使用无符号 32 位索引，适合需要更大顶点索引范围的 primitive。 */
    UNSIGNED_INT(4)
}

/**
 * CooFX 网格材质的透明度模式。
 *
 * [OPAQUE] 是默认模式，不执行 alpha 丢弃；[MASK] 根据 [CooFxCompiledMaterial.alphaCutoff]
 * 丢弃片元。首版不包含透明混合模式，导入到该阶段的 BLEND 材质必须被 compiler 拒绝。
 */
enum class CooFxAlphaMode {
    /** 完全不透明材质，不使用 alpha cutoff。 */
    OPAQUE,

    /** Alpha 裁剪材质，片元阶段使用材质记录中的 cutoff。 */
    MASK
}

/**
 * CooFX primitive 的面剔除策略。
 *
 * [BACK] 是单面材质的默认策略，只绘制正面；[NONE] 用于 glTF doubleSided 材质，关闭面剔除。
 */
enum class CooFxCullMode {
    /** 剔除背面，仅绘制正面。 */
    BACK,

    /** 不执行面剔除，正反面都参与绘制。 */
    NONE
}

/**
 * CooFX world pass 的深度比较策略。
 *
 * [LESS_OR_EQUAL] 是普通世界网格的默认策略；[ALWAYS] 仅用于明确声明忽略遮挡的变体。
 * 策略必须由现有 Coo Pipeline/backend 映射，描述对象本身不修改 GL 状态。
 */
enum class CooFxDepthTest {
    /** 当前片元深度小于或等于已有深度时通过。 */
    LESS_OR_EQUAL,

    /** 所有片元都通过深度测试。 */
    ALWAYS
}

/**
 * CooFX 首版可声明的混合策略。
 *
 * [DISABLED] 是首版唯一合法成员，表示不进行颜色混合。该枚举保留稳定批次字段，未来新增
 * 混合模式前必须先定义透明实例的跨批次排序规则。
 */
enum class CooFxBlendMode {
    /** 禁用颜色混合，供 OPAQUE 与 MASK 材质使用。 */
    DISABLED
}

/**
 * CooFX 实例的光照来源。
 *
 * [WORLD] 使用实例数据中的 packed light；[FULL_BRIGHT] 使用 backend 提供的全亮语义。
 * 该选择进入批次键，不能按单个实例临时切换。
 */
enum class CooFxLightMode {
    /** 使用世界光照与实例携带的 packed light。 */
    WORLD,

    /** 忽略世界亮度并按全亮方式着色。 */
    FULL_BRIGHT
}

/**
 * CooFX 顶点属性的领域语义。
 *
 * [POSITION] 是必需的三维位置；[NORMAL] 是可选三维法线；[TEXCOORD_0] 是可选第一套二维 UV；
 * [COLOR_0] 是可选线性顶点颜色。compiler 必须保证同一 layout 中语义不重复。
 */
enum class CooFxVertexSemantic {
    /** 三维顶点位置，是每个 primitive 的必需属性。 */
    POSITION,

    /** 三维顶点法线，用于世界光照。 */
    NORMAL,

    /** 第一套二维纹理坐标，数值保持 glTF 原始方向。 */
    TEXCOORD_0,

    /** 线性空间顶点颜色，可包含 RGB 或 RGBA 分量。 */
    COLOR_0
}

/**
 * 编译后顶点属性的标量存储类型。
 *
 * [FLOAT] 保存 IEEE 754 单精度值；[UNSIGNED_BYTE_NORMALIZED] 保存无符号字节并在读取时归一化；
 * [UNSIGNED_SHORT_NORMALIZED] 保存无符号短整数并在读取时归一化。
 */
enum class CooFxVertexComponentType(val byteSize: Int) {
    /** 32 位单精度浮点标量。 */
    FLOAT(4),

    /** 8 位无符号归一化标量。 */
    UNSIGNED_BYTE_NORMALIZED(1),

    /** 16 位无符号归一化标量。 */
    UNSIGNED_SHORT_NORMALIZED(2)
}

/**
 * CooFX 编译包声明的变形执行模式。
 *
 * [RIGID] 是首版唯一可执行模式，只使用节点或实例 TRS；[MORPH] 表示资源含 morph target 计划；
 * [SKIN] 表示资源需要 joint palette；[VAT] 表示资源计划使用顶点动画纹理。后三者仅用于显式诊断
 * 和未来能力协商，首版上传器必须拒绝执行，不能静默退化为静态网格。
 */
enum class CooFxDeformationMode(val executableInV1: Boolean) {
    /** 首版可执行的刚性变形模式。 */
    RIGID(true),

    /** 尚未执行的 morph target 变形计划。 */
    MORPH(false),

    /** 尚未执行的骨骼蒙皮变形计划。 */
    SKIN(false),

    /** 尚未执行的顶点动画纹理变形计划。 */
    VAT(false)
}

data class CooFxVertexAttribute(
    val semantic: CooFxVertexSemantic,
    val componentType: CooFxVertexComponentType,
    val componentCount: Int,
    val byteOffset: Int
) {
    init {
        require(componentCount in 1..4) { "Vertex attribute component count must be between 1 and 4" }
        require(byteOffset >= 0) { "Vertex attribute byte offset must not be negative" }
    }
}

data class CooFxVertexLayout(
    val version: Int,
    val strideBytes: Int,
    val attributes: List<CooFxVertexAttribute>
) {
    init {
        require(version > 0) { "Vertex layout version must be positive" }
        require(strideBytes > 0) { "Vertex stride must be positive" }
        require(attributes.isNotEmpty()) { "Vertex layout must contain attributes" }
        require(attributes.map { it.semantic }.distinct().size == attributes.size) {
            "Vertex layout must not contain duplicate semantics"
        }
        require(attributes.any { it.semantic == CooFxVertexSemantic.POSITION }) {
            "Vertex layout must contain POSITION"
        }
        require(attributes.all { attribute ->
            attribute.byteOffset + attribute.componentType.byteSize * attribute.componentCount <= strideBytes
        }) { "Vertex attribute exceeds vertex stride" }
    }
}

class CooFxImmutableBytes(bytes: ByteArray) {
    private val content = bytes.copyOf()

    val size: Int
        get() = content.size

    fun copyBytes(): ByteArray = content.copyOf()

    override fun equals(other: Any?): Boolean = other is CooFxImmutableBytes && content.contentEquals(other.content)

    override fun hashCode(): Int = content.contentHashCode()

    override fun toString(): String = "CooFxImmutableBytes(size=$size)"
}

data class CooFxDrawRange(
    val firstIndex: Int,
    val indexCount: Int,
    val baseVertex: Int = 0
) {
    init {
        require(firstIndex >= 0) { "First index must not be negative" }
        require(indexCount > 0) { "Index count must be positive" }
        require(indexCount % 3 == 0) { "Triangle draw index count must be divisible by 3" }
        require(baseVertex >= 0) { "Base vertex must not be negative" }
    }
}

data class CooFxColor4(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float,
) {
    init {
        require(red.isFinite() && green.isFinite() && blue.isFinite() && alpha.isFinite()) {
            "Color components must be finite"
        }
    }
}

data class CooFxColor3(
    val red: Float,
    val green: Float,
    val blue: Float,
) {
    init {
        require(red.isFinite() && green.isFinite() && blue.isFinite()) {
            "Color components must be finite"
        }
    }
}

data class CooFxCompiledMaterial(
    val id: String,
    val baseColorTexture: ResourceLocation?,
    val baseColorFactor: CooFxColor4 = CooFxColor4(1F, 1F, 1F, 1F),
    val alphaMode: CooFxAlphaMode,
    val alphaCutoff: Float,
    val cullMode: CooFxCullMode,
    val depthTest: CooFxDepthTest,
    val depthWrite: Boolean,
    val blendMode: CooFxBlendMode,
    val lightMode: CooFxLightMode,
    val emissiveTexture: ResourceLocation? = null,
    val emissiveFactor: CooFxColor3 = CooFxColor3(0F, 0F, 0F),
    val emissiveStrength: Float = 1F,
) {
    init {
        require(id.isNotBlank()) { "Material id must not be blank" }
        require(alphaCutoff.isFinite() && alphaCutoff in 0.0F..1.0F) {
            "Alpha cutoff must be finite and between 0 and 1"
        }
        require(alphaMode == CooFxAlphaMode.MASK || alphaCutoff == 0.0F) {
            "Opaque material must use zero alpha cutoff"
        }
        require(emissiveFactor.red >= 0F && emissiveFactor.green >= 0F && emissiveFactor.blue >= 0F) {
            "Emissive factor components must not be negative"
        }
        require(emissiveStrength.isFinite() && emissiveStrength >= 0F) {
            "Emissive strength must be a non-negative finite value"
        }
    }
}

data class CooFxDeformationPlan(
    val mode: CooFxDeformationMode,
    val primitiveNodeIndex: Int,
    val diagnostic: String? = null
) {
    init {
        require(primitiveNodeIndex >= 0) { "Primitive node index must not be negative" }
        require(mode.executableInV1 || !diagnostic.isNullOrBlank()) {
            "Unsupported deformation plan must contain a diagnostic"
        }
    }
}
