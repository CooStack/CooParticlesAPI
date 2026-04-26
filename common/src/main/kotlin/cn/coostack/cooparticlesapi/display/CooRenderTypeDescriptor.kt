package cn.coostack.cooparticlesapi.display

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat

enum class CooRenderTypeShaderPreset {
    POSITION_COLOR
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


data class CooRenderTypeDescriptor(
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
    val depthTestMode: CooRenderDepthTestMode = CooRenderDepthTestMode.LEQUAL
) {
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
            depthTestMode = depthTestMode
        )
    }
}
