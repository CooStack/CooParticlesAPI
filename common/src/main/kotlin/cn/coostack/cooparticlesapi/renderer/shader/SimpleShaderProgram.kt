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
        if (program <= 0 || !glIsProgram(program)) {
            prevProgram = 0
            glUseProgram(0)
            return
        }
        prevProgram = glGetInteger(GL_CURRENT_PROGRAM)
        glUseProgram(program)
    }

    override fun reset() {
        if (prevProgram > 0 && glIsProgram(prevProgram)) {
            glUseProgram(prevProgram)
        } else {
            glUseProgram(0)
        }
    }

    override fun release() {
        if (program > 0) {
            if (glGetInteger(GL_CURRENT_PROGRAM) == program) {
                glUseProgram(0)
            }
            glDeleteProgram(program)
            program = 0
            prevProgram = 0
        }
    }

    override fun useOnContext(drawMethod: CooShaderProgram.() -> Unit) {
        if (program <= 0 || !glIsProgram(program)) {
            return
        }
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
