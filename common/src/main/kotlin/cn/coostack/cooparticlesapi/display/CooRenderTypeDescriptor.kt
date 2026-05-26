package cn.coostack.cooparticlesapi.display

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.ShaderInstance

enum class CooRenderTypeShaderPreset {
    POSITION_COLOR,

    /**
     * 使用 [cn.coostack.cooparticlesapi.test.options.display.MCShaders.GLOW] 预设。
     * 对应 `assets/minecraft/shaders/core/coo_glow.{json,vsh,fsh}`，
     * 输出未 clamp 的 HDR 颜色 (`vColor.rgb * 8.0`) 用于 IRIS bloom。
     */
    COO_GLOW,

    /**
     * 走 [CooRenderTypeDescriptor.customShader] 提供的 ShaderInstance。
     * 这条路径绕过所有内建 preset，由用户自行管理生命周期 (通常通过 ShaderReloadBus reload)。
     */
    CUSTOM,
}

enum class CooRenderTransparencyMode {
    NONE,
    ADDITIVE
}

enum class CooRenderCullMode {
    ENABLED,
    DISABLED
}

enum class CooRenderLightmapMode {
    ENABLED,
    DISABLED
}

enum class CooRenderDepthTestMode {
    LEQUAL
}


/**
 * RenderType 描述符。
 *
 * - [shaderPreset] = [CooRenderTypeShaderPreset.CUSTOM] 时必须提供 [customShader]。
 * - [customShader] 在 IRIS 光影下需要通过 [cn.coostack.cooparticlesapi.compat.IrisCompat.markUnskippable]
 *   标记为不可跳过，否则 IRIS 会丢弃绘制 (我们的 provider 已自动处理)。
 *
 * `customShader` 不参与 [equals] / [hashCode]，避免不同 lambda 实例破坏 RenderType 缓存。
 */
class CooRenderTypeDescriptor(
    val name: String,
    val vertexFormat: VertexFormat = DefaultVertexFormat.POSITION_COLOR,
    val mode: VertexFormat.Mode = VertexFormat.Mode.QUADS,
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
     * 必须返回当前 reload 周期内有效的 ShaderInstance。
     */
    val customShader: (() -> ShaderInstance)? = null,
) {
    init {
        if (shaderPreset == CooRenderTypeShaderPreset.CUSTOM) {
            requireNotNull(customShader) {
                "CooRenderTypeDescriptor(name=$name) shaderPreset=CUSTOM requires customShader"
            }
        }
    }

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
        fun builder(
            name: String,
            vertexFormat: VertexFormat = DefaultVertexFormat.POSITION_COLOR,
            mode: VertexFormat.Mode = VertexFormat.Mode.QUADS
        ): CooRenderTypeDescriptorBuilder {
            return CooRenderTypeDescriptorBuilder(name, vertexFormat, mode)
        }
    }
}

class CooRenderTypeDescriptorBuilder(
    private val name: String,
    private val vertexFormat: VertexFormat,
    private val mode: VertexFormat.Mode
) {
    private var bufferSize: Int = 256
    private var affectsCrumbling: Boolean = false
    private var sortOnUpload: Boolean = false
    private var shaderPreset: CooRenderTypeShaderPreset = CooRenderTypeShaderPreset.POSITION_COLOR
    private var transparencyMode: CooRenderTransparencyMode = CooRenderTransparencyMode.NONE
    private var cullMode: CooRenderCullMode = CooRenderCullMode.DISABLED
    private var lightmapMode: CooRenderLightmapMode = CooRenderLightmapMode.DISABLED
    private var depthTestMode: CooRenderDepthTestMode = CooRenderDepthTestMode.LEQUAL
    private var customShader: (() -> ShaderInstance)? = null

    fun bufferSize(bufferSize: Int): CooRenderTypeDescriptorBuilder {
        this.bufferSize = bufferSize
        return this
    }

    fun affectsCrumbling(affectsCrumbling: Boolean): CooRenderTypeDescriptorBuilder {
        this.affectsCrumbling = affectsCrumbling
        return this
    }

    fun sortOnUpload(sortOnUpload: Boolean): CooRenderTypeDescriptorBuilder {
        this.sortOnUpload = sortOnUpload
        return this
    }

    fun shaderPreset(preset: CooRenderTypeShaderPreset): CooRenderTypeDescriptorBuilder {
        this.shaderPreset = preset
        return this
    }

    /**
     * 直接指定一个自定义 ShaderInstance，等价于 `.shaderPreset(CUSTOM)` + `.customShader{ ... }`。
     */
    fun customShader(supplier: () -> ShaderInstance): CooRenderTypeDescriptorBuilder {
        this.customShader = supplier
        this.shaderPreset = CooRenderTypeShaderPreset.CUSTOM
        return this
    }

    fun transparencyMode(mode: CooRenderTransparencyMode): CooRenderTypeDescriptorBuilder {
        this.transparencyMode = mode
        return this
    }

    fun cullMode(mode: CooRenderCullMode): CooRenderTypeDescriptorBuilder {
        this.cullMode = mode
        return this
    }

    fun lightmapMode(mode: CooRenderLightmapMode): CooRenderTypeDescriptorBuilder {
        this.lightmapMode = mode
        return this
    }

    fun depthTestMode(mode: CooRenderDepthTestMode): CooRenderTypeDescriptorBuilder {
        this.depthTestMode = mode
        return this
    }

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
