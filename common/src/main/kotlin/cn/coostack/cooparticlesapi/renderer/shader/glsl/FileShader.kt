package cn.coostack.cooparticlesapi.renderer.shader.glsl

import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShader
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.utils.GlslUtil
import org.lwjgl.opengl.GL33.*

class FileShader(val path: String, override val type: GlShaderType) : GlShader {
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
        assert(glGetShaderi(shaderID, GL_COMPILE_STATUS) != GL_FALSE) {
            "compile code failed info:${glGetShaderInfoLog(shaderID)}"
        }
    }

    override fun deleteShader() {
        glDeleteShader(shaderID)
    }

    private fun readFromJar(): String {
        return GlslUtil.readGlslCodeFromJar(path)
    }

}