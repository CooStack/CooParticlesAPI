package cn.coostack.cooparticlesapi.renderer.shader

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShader
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.glsl.FileShader
import cn.coostack.cooparticlesapi.renderer.shader.glsl.IdentifierShader
import net.minecraft.resources.ResourceLocation

class ShaderProgramBuilder {
    private var vertex: GlShader? = null
    private var fragment: GlShader? = null

    fun vertex(path: String): ShaderProgramBuilder {
        vertex = IdentifierShader(
            ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, path),
            GlShaderType.VERTEX
        )
        return this
    }


    fun fragment(path: String): ShaderProgramBuilder {
        fragment = IdentifierShader(
            ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, path),
            GlShaderType.FRAGMENT
        )
        return this
    }

    fun vertex(shader: GlShader): ShaderProgramBuilder {
        vertex = shader
        return this
    }

    fun fragment(shader: GlShader): ShaderProgramBuilder {
        fragment = shader
        return this
    }

    fun build(): CooShaderProgram {
        assert(vertex != null && fragment != null) { "vertex and fragment can not be null" }
        return SimpleShaderProgram(vertex!!, fragment!!)
    }
}