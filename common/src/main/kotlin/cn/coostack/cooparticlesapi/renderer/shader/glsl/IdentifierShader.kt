package cn.coostack.cooparticlesapi.renderer.shader.glsl

import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShader
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.utils.GlslUtil
import net.minecraft.resources.ResourceLocation
import org.lwjgl.opengl.GL33.*

class IdentifierShader(val id: ResourceLocation, override val type: GlShaderType) : GlShader {
    private var shaderID = 0
    override fun shaderID(): Int {
        return shaderID
    }

    override fun compile() {
        shaderID = glCreateShader(type.gl)
        glShaderSource(shaderID, readFromJar())
        glCompileShader(shaderID)
        assertCompiled()
    }

    override fun assertCompiled() {
        require(glGetShaderi(shaderID, GL_COMPILE_STATUS) != GL_FALSE) {
            "compile code failed info:${glGetShaderInfoLog(shaderID)} shader: $id"
        }
    }

    override fun deleteShader() {
        glDeleteShader(shaderID)
    }

    private fun readFromJar(): String {
        return GlslUtil.readGlslCodeFromJar(id)
    }

}