package cn.coostack.cooparticlesapi.renderer.shader

import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShader
import org.joml.Matrix4f
import org.lwjgl.opengl.GL33.*

class SimpleShaderProgram(override var vertexShader: GlShader, override var fragmentShader: GlShader) :
    CooShaderProgram {
    override var program: Int = 0
    private var prevProgram = 0
    override fun init() {
        program = glCreateProgram()
        vertexShader.compile()
        fragmentShader.compile()
        glAttachShader(program, vertexShader.shaderID())
        glAttachShader(program, fragmentShader.shaderID())
        glLinkProgram(program)
        assertProgram()
        vertexShader.deleteShader()
        fragmentShader.deleteShader()
    }

    override fun use() {
        prevProgram = glGetInteger(GL_CURRENT_PROGRAM)
        glUseProgram(program)
    }

    override fun reset() {
        glUseProgram(prevProgram)
    }

    override fun release() {
        if (program > 0) {
            glDeleteProgram(program)
        }
    }

    override fun useOnContext(drawMethod: CooShaderProgram.() -> Unit) {
        use()
        drawMethod()
        reset()
    }


    private fun assertProgram() {
        require(glGetProgrami(program, GL_LINK_STATUS) != GL_FALSE) {
            "program $program link error: ${glGetProgramInfoLog(program)}"
        }
    }
}