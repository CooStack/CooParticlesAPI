package cn.coostack.cooparticlesapi.renderer.shader.shader

import net.minecraft.client.MinecraftClient
import net.minecraft.util.Identifier
import org.lwjgl.opengl.GL33.*

class IdentifierGlShader(val targetID: Identifier, override val type: ShaderType) : GlShader {
    var shaderID = -1
    override fun shaderCode(): String {
        return loadFromRelease()
    }

    override fun shaderID(): Int {
        return shaderID
    }

    override fun init() {
        shaderID = glCreateShader(type.glID)
        glShaderSource(shaderID, shaderCode())
        compile()
        assertShaderCompiled()
    }


    override fun assertShaderCompiled() {
        if (shaderID == -1) {
            throw RuntimeException("current shader is not invoke compile method")
        }
        if (glGetShaderi(shaderID, GL_COMPILE_STATUS) == GL_FALSE) {
            throw RuntimeException("Shader $shaderID not compiled info :${glGetShaderInfoLog(shaderID)}")
        }
    }

    private fun compile() {
        glCompileShader(shaderID)
    }

    private fun loadFromRelease(): String {
        val manager = MinecraftClient.getInstance().resourceManager
        val open = manager.open(targetID)
        val res = open.readAllBytes().decodeToString()
        return res
    }

}