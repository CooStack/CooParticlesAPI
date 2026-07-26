package cn.coostack.cooparticlesapi.renderer.shader

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.shader.api.CooComputeShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShader
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.buffer.ShaderBufferLayout
import cn.coostack.cooparticlesapi.renderer.shader.glsl.IdentifierShader
import net.minecraft.resources.ResourceLocation

class AdvancedShaderProgramBuilder {
    private var vertex: GlShader? = null
    private var fragment: GlShader? = null
    private var geometry: GlShader? = null
    private var tessellationControl: GlShader? = null
    private var tessellationEvaluation: GlShader? = null
    private var compute: GlShader? = null
    private val shaderBufferLayouts = mutableListOf<ShaderBufferLayout<*>>()
    private val attributeLocations = linkedMapOf<String, Int>()
    private var managedProgramId: ResourceLocation? = null

    fun vertex(path: String): AdvancedShaderProgramBuilder {
        vertex = identifier(path, GlShaderType.VERTEX)
        return this
    }

    fun fragment(path: String): AdvancedShaderProgramBuilder {
        fragment = identifier(path, GlShaderType.FRAGMENT)
        return this
    }

    fun geometry(path: String): AdvancedShaderProgramBuilder {
        geometry = identifier(path, GlShaderType.GEOMETRY)
        return this
    }

    fun tessellationControl(path: String): AdvancedShaderProgramBuilder {
        tessellationControl = identifier(path, GlShaderType.TESSELLATION_CONTROL)
        return this
    }

    fun tessellationEvaluation(path: String): AdvancedShaderProgramBuilder {
        tessellationEvaluation = identifier(path, GlShaderType.TESSELLATION_EVALUATION)
        return this
    }

    fun compute(path: String): AdvancedShaderProgramBuilder {
        compute = identifier(path, GlShaderType.COMPUTE)
        return this
    }

    fun vertex(shader: GlShader): AdvancedShaderProgramBuilder {
        vertex = shader
        return this
    }

    fun fragment(shader: GlShader): AdvancedShaderProgramBuilder {
        fragment = shader
        return this
    }

    fun geometry(shader: GlShader): AdvancedShaderProgramBuilder {
        geometry = shader
        return this
    }

    fun tessellationControl(shader: GlShader): AdvancedShaderProgramBuilder {
        tessellationControl = shader
        return this
    }

    fun tessellationEvaluation(shader: GlShader): AdvancedShaderProgramBuilder {
        tessellationEvaluation = shader
        return this
    }

    fun compute(shader: GlShader): AdvancedShaderProgramBuilder {
        compute = shader
        return this
    }

    fun managedId(id: ResourceLocation): AdvancedShaderProgramBuilder {
        managedProgramId = id
        return this
    }

    fun managedId(path: String): AdvancedShaderProgramBuilder {
        managedProgramId = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, path)
        return this
    }

    fun bufferLayout(layout: ShaderBufferLayout<*>): AdvancedShaderProgramBuilder {
        shaderBufferLayouts += layout
        return this
    }

    fun attributeLocation(name: String, location: Int): AdvancedShaderProgramBuilder {
        require(location >= 0) { "attribute location must be non-negative" }
        attributeLocations[name] = location
        return this
    }

    fun build(): CooShaderProgram {
        require(compute == null) { "compute shader must be built with buildCompute()" }
        check(vertex != null && fragment != null) { "vertex and fragment can not be null" }
        return ShaderProgramRegistry.register(
            SimpleShaderProgram(
            vertexShader = vertex!!,
            fragmentShader = fragment!!,
            geometryShaderInternal = geometry,
            tessellationControlShaderInternal = tessellationControl,
            tessellationEvaluationShaderInternal = tessellationEvaluation,
            shaderBufferLayoutsInternal = shaderBufferLayouts.toList(),
            attributeLocationsInternal = attributeLocations.toMap(),
            managedProgramIdInternal = managedProgramId
            )
        )
    }

    fun buildCompute(): CooComputeShaderProgram {
        require(vertex == null && fragment == null && geometry == null && tessellationControl == null && tessellationEvaluation == null) {
            "compute shader program can not be mixed with graphics shader stages"
        }
        check(compute != null) { "compute shader can not be null" }
        return ShaderProgramRegistry.register(
            ComputeShaderProgram(compute!!, shaderBufferLayouts.toList(), managedProgramId)
        )
    }

    private fun identifier(path: String, type: GlShaderType): GlShader {
        return IdentifierShader(
            ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, path),
            type
        )
    }
}
