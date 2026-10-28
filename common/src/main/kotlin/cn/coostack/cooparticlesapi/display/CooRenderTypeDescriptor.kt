package cn.coostack.cooparticlesapi.display

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.renderpearl.api.vertex.VertexFormat
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import com.mojang.renderpearl.api.pipeline.RenderPipeline

/**
 * 描述符选择的着色程序来源。
 * [POSITION_COLOR] 使用原版位置颜色程序，[COO_GLOW] 使用保留高亮输出的库内程序，
 * [CUSTOM] 使用调用方注册的 RenderPearl 管线；自定义资源必须支持 26.3 的绑定布局。
 */
enum class CooRenderTypeShaderPreset {
    /** 不附加纹理的基础顶点颜色程序。 */
    POSITION_COLOR,

    /**
     * 使用 [cn.coostack.cooparticlesapi.test.options.display.MCShaders.GLOW] 预设。
     * 对应 `assets/cooparticlesapi/shaders/core/coo_glow.{vsh,fsh}`，
     * 输出未 clamp 的 HDR 颜色 (`vColor.rgb * 8.0`) 用于 IRIS bloom。
     */
    COO_GLOW,

    /**
     * 走 [CooRenderTypeDescriptor.customShader] 提供的 RenderPipeline。
     * 程序编译与释放由原版资源生命周期管理，调用方负责注册管线引用的资源与额外绑定。
     */
    CUSTOM,
}

/** 颜色混合方式：[NONE] 禁用混合，[ADDITIVE] 将源色与目标色相加，不改变深度写入。 */
enum class CooRenderTransparencyMode {
    NONE,
    ADDITIVE
}

/** 背面裁剪开关：[ENABLED] 仅保留正面，[DISABLED] 允许双面绘制。 */
enum class CooRenderCullMode {
    ENABLED,
    DISABLED
}

/** 光照图绑定开关：[ENABLED] 提供原版光照图，[DISABLED] 不要求该纹理；程序自行决定是否采样。 */
enum class CooRenderLightmapMode {
    ENABLED,
    DISABLED
}

/** 深度比较模式；[LEQUAL] 保留旧版不晚于场景表面的语义，26.3 反向深度下使用大于等于比较。 */
enum class CooRenderDepthTestMode {
    LEQUAL
}


/**
 * RenderType 描述符。
 *
 * - [shaderPreset] = [CooRenderTypeShaderPreset.CUSTOM] 时必须提供 [customShader]。
 * - [customShader] 在 26.3 返回 RenderPipeline，不再返回已经移除的 ShaderInstance。
 * - 平台保留 Iris 实体通道包装；是否支持自定义管线仍须按实际 Iris 版本验证。
 *
 * `customShader` 不参与 [equals] / [hashCode]，避免不同 lambda 实例破坏 RenderType 缓存。
 * 示例：`CooRenderTypeDescriptor.builder("marker").shaderPreset(CooRenderTypeShaderPreset.COO_GLOW).build()`。
 * @property name 诊断与 Iris 类型映射共用的名称
 * @property vertexFormat 单个几何流的顶点布局
 * @property mode 原版提交器接受的图元拓扑
 * @property bufferSize 保留旧描述格式的容量提示；26.3 暂存顶点缓冲由原版分配，不再接受固定初始容量
 * @property affectsCrumbling 是否参与破坏覆盖
 * @property sortOnUpload 是否对上传的透明几何排序
 * @property shaderPreset 程序来源
 * @property transparencyMode 颜色混合方式
 * @property cullMode 背面裁剪方式
 * @property lightmapMode 是否绑定原版光照图
 * @property depthTestMode 深度比较方式
 * @property customShader 自定义管线供应器；额外 uniform 与顶点流仍需对应执行器提供绑定
 */
class CooRenderTypeDescriptor(
    val name: String,
    val vertexFormat: VertexFormat = DefaultVertexFormat.POSITION_COLOR,
    val mode: PrimitiveTopology = PrimitiveTopology.QUADS,
    val bufferSize: Int = 256,
    val affectsCrumbling: Boolean = false,
    val sortOnUpload: Boolean = false,
    val shaderPreset: CooRenderTypeShaderPreset = CooRenderTypeShaderPreset.POSITION_COLOR,
    val transparencyMode: CooRenderTransparencyMode = CooRenderTransparencyMode.NONE,
    val cullMode: CooRenderCullMode = CooRenderCullMode.DISABLED,
    val lightmapMode: CooRenderLightmapMode = CooRenderLightmapMode.DISABLED,
    val depthTestMode: CooRenderDepthTestMode = CooRenderDepthTestMode.LEQUAL,
    /**
     * 当 [shaderPreset] = [CooRenderTypeShaderPreset.CUSTOM] 时使用。
     * 每次 build RenderType 时取一次，结果会被 RenderType 缓存。
     * 必须返回引用当前资源包程序与绑定布局的 RenderPipeline。
     */
    val customShader: (() -> RenderPipeline)? = null,
) {
    init {
        if (shaderPreset == CooRenderTypeShaderPreset.CUSTOM) {
            requireNotNull(customShader) {
                "CooRenderTypeDescriptor(name=$name) shaderPreset=CUSTOM requires customShader"
            }
        }
    }

    /** 比较描述值，不将程序供应器的函数对象身份作为缓存键。 */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CooRenderTypeDescriptor) return false
        return name == other.name &&
            vertexFormat == other.vertexFormat &&
            mode == other.mode &&
            bufferSize == other.bufferSize &&
            affectsCrumbling == other.affectsCrumbling &&
            sortOnUpload == other.sortOnUpload &&
            shaderPreset == other.shaderPreset &&
            transparencyMode == other.transparencyMode &&
            cullMode == other.cullMode &&
            lightmapMode == other.lightmapMode &&
            depthTestMode == other.depthTestMode
    }

    /** 为同值描述生成与 [equals] 一致的缓存散列。 */
    override fun hashCode(): Int {
        var r = name.hashCode()
        r = 31 * r + vertexFormat.hashCode()
        r = 31 * r + mode.hashCode()
        r = 31 * r + bufferSize
        r = 31 * r + affectsCrumbling.hashCode()
        r = 31 * r + sortOnUpload.hashCode()
        r = 31 * r + shaderPreset.hashCode()
        r = 31 * r + transparencyMode.hashCode()
        r = 31 * r + cullMode.hashCode()
        r = 31 * r + lightmapMode.hashCode()
        r = 31 * r + depthTestMode.hashCode()
        return r
    }

    override fun toString(): String =
        "CooRenderTypeDescriptor(name=$name, preset=$shaderPreset, transparency=$transparencyMode, " +
            "cull=$cullMode, lightmap=$lightmapMode, depth=$depthTestMode)"

    companion object {
        /**
         * 创建独立构建器，不修改已发布的描述符。
         * 示例：`builder("marker").sortOnUpload(true).build()`。
         * @param name 绘制类型的诊断名称
         * @param vertexFormat 几何顶点布局
         * @param mode 图元拓扑
         * @return 尚未发布的构建器
         */
        fun builder(
            name: String,
            vertexFormat: VertexFormat = DefaultVertexFormat.POSITION_COLOR,
            mode: PrimitiveTopology = PrimitiveTopology.QUADS
        ): CooRenderTypeDescriptorBuilder {
            return CooRenderTypeDescriptorBuilder(name, vertexFormat, mode)
        }
    }
}

/**
 * 收集一个不可变描述符的设置，构建后可交给任一平台的公共工厂。
 * 示例：`CooRenderTypeDescriptor.builder("marker").customShader { pipeline }.build()`。
 * @property name 绘制类型的诊断名称
 * @property vertexFormat 几何顶点布局
 * @property mode 图元拓扑
 */
class CooRenderTypeDescriptorBuilder(
    private val name: String,
    private val vertexFormat: VertexFormat,
    private val mode: PrimitiveTopology
) {
    private var bufferSize: Int = 256
    private var affectsCrumbling: Boolean = false
    private var sortOnUpload: Boolean = false
    private var shaderPreset: CooRenderTypeShaderPreset = CooRenderTypeShaderPreset.POSITION_COLOR
    private var transparencyMode: CooRenderTransparencyMode = CooRenderTransparencyMode.NONE
    private var cullMode: CooRenderCullMode = CooRenderCullMode.DISABLED
    private var lightmapMode: CooRenderLightmapMode = CooRenderLightmapMode.DISABLED
    private var depthTestMode: CooRenderDepthTestMode = CooRenderDepthTestMode.LEQUAL
    /** 仅在自定义预设下读取的管线供应器。 */
    private var customShader: (() -> RenderPipeline)? = null

    /**
     * 保留旧资源描述中的容量提示，26.3 的实际暂存容量由原版管理。
     * 示例：`builder.bufferSize(512)`。
     * @param bufferSize 旧描述的正字节容量提示
     * @return 当前构建器
     */
    fun bufferSize(bufferSize: Int): CooRenderTypeDescriptorBuilder {
        this.bufferSize = bufferSize
        return this
    }

    /**
     * 设置是否参与破坏覆盖。
     * 示例：`builder.affectsCrumbling(true)`。
     * @param affectsCrumbling 是否接收覆盖效果
     * @return 当前构建器
     */
    fun affectsCrumbling(affectsCrumbling: Boolean): CooRenderTypeDescriptorBuilder {
        this.affectsCrumbling = affectsCrumbling
        return this
    }

    /**
     * 设置上传时的透明几何排序。
     * 示例：`builder.sortOnUpload(true)`。
     * @param sortOnUpload 是否按原版规则排序
     * @return 当前构建器
     */
    fun sortOnUpload(sortOnUpload: Boolean): CooRenderTypeDescriptorBuilder {
        this.sortOnUpload = sortOnUpload
        return this
    }

    /**
     * 选择内置程序或已经设置供应器的自定义程序。
     * 示例：`builder.shaderPreset(CooRenderTypeShaderPreset.COO_GLOW)`。
     * @param preset 程序来源
     * @return 当前构建器
     */
    fun shaderPreset(preset: CooRenderTypeShaderPreset): CooRenderTypeDescriptorBuilder {
        this.shaderPreset = preset
        return this
    }

    /**
     * 直接指定自定义管线，等价于 `.shaderPreset(CUSTOM)` 与 `.customShader { pipeline }`。
     * 示例：`builder.customShader { pipeline }`。
     * @param supplier 当前资源生命周期使用的 RenderPearl 管线供应器
     * @return 当前构建器
     */
    fun customShader(supplier: () -> RenderPipeline): CooRenderTypeDescriptorBuilder {
        this.customShader = supplier
        this.shaderPreset = CooRenderTypeShaderPreset.CUSTOM
        return this
    }

    /**
     * 设置颜色混合方式。
     * 示例：`builder.transparencyMode(CooRenderTransparencyMode.ADDITIVE)`。
     * @param mode 关闭混合或加色混合
     * @return 当前构建器
     */
    fun transparencyMode(mode: CooRenderTransparencyMode): CooRenderTypeDescriptorBuilder {
        this.transparencyMode = mode
        return this
    }

    /**
     * 设置背面裁剪。
     * 示例：`builder.cullMode(CooRenderCullMode.DISABLED)`。
     * @param mode 背面裁剪开关
     * @return 当前构建器
     */
    fun cullMode(mode: CooRenderCullMode): CooRenderTypeDescriptorBuilder {
        this.cullMode = mode
        return this
    }

    /**
     * 设置原版光照图绑定。
     * 示例：`builder.lightmapMode(CooRenderLightmapMode.ENABLED)`。
     * @param mode 光照图绑定开关
     * @return 当前构建器
     */
    fun lightmapMode(mode: CooRenderLightmapMode): CooRenderTypeDescriptorBuilder {
        this.lightmapMode = mode
        return this
    }

    /**
     * 设置旧版表面深度比较语义，工厂负责转换为反向深度。
     * 示例：`builder.depthTestMode(CooRenderDepthTestMode.LEQUAL)`。
     * @param mode 描述中的深度比较方式
     * @return 当前构建器
     */
    fun depthTestMode(mode: CooRenderDepthTestMode): CooRenderTypeDescriptorBuilder {
        this.depthTestMode = mode
        return this
    }

    /**
     * 发布独立描述符，不创建 GPU 资源。
     * 示例：`builder.build()`。
     * @return 可以作为平台缓存键的渲染描述
     * @throws IllegalArgumentException 自定义预设没有提供管线供应器
     */
    fun build(): CooRenderTypeDescriptor {
        return CooRenderTypeDescriptor(
            name = name,
            vertexFormat = vertexFormat,
            mode = mode,
            bufferSize = bufferSize,
            affectsCrumbling = affectsCrumbling,
            sortOnUpload = sortOnUpload,
            shaderPreset = shaderPreset,
            transparencyMode = transparencyMode,
            cullMode = cullMode,
            lightmapMode = lightmapMode,
            depthTestMode = depthTestMode,
            customShader = customShader,
        )
    }
}
