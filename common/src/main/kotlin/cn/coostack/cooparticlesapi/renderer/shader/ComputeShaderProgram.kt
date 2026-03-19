package cn.coostack.cooparticlesapi.renderer.shader

import cn.coostack.cooparticlesapi.renderer.shader.api.CooComputeShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShader
import cn.coostack.cooparticlesapi.renderer.shader.buffer.ShaderBufferCache
import cn.coostack.cooparticlesapi.renderer.shader.buffer.ShaderBufferLayout
import net.minecraft.resources.ResourceLocation
import org.lwjgl.opengl.GL43.*

class ComputeShaderProgram(
    override var computeShader: GlShader,
    private val shaderBufferLayoutsInternal: List<ShaderBufferLayout<*>> = emptyList(),
    private val managedProgramIdInternal: ResourceLocation? = null
) : CooComputeShaderProgram {
    override var program: Int = 0
    private var prevProgram = 0

    override fun shaderBufferLayouts(): List<ShaderBufferLayout<*>> = shaderBufferLayoutsInternal

    override fun managedProgramId(): ResourceLocation? = managedProgramIdInternal

    override fun init() {
        program = glCreateProgram()
        computeShader.compile()
        glAttachShader(program, computeShader.shaderID())
        glLinkProgram(program)
        assertProgram()
        computeShader.deleteShader()
    }

    override fun use() {
        if (program <= 0 || !glIsProgram(program)) {
            prevProgram = 0
            glUseProgram(0)
            return
        }
        prevProgram = glGetInteger(GL_CURRENT_PROGRAM)
        glUseProgram(program)
        ShaderBufferCache.bindAll(shaderBufferLayoutsInternal)
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

    override fun dispatch(x: Int, y: Int, z: Int) {
        if (program <= 0 || !glIsProgram(program)) {
            return
        }
        use()
        try {
            glDispatchCompute(x, y, z)
        } finally {
            reset()
        }
    }

    override fun useOnContext(dispatchMethod: CooComputeShaderProgram.() -> Unit) {
        if (program <= 0 || !glIsProgram(program)) {
            return
        }
        use()
        try {
            dispatchMethod()
        } finally {
            reset()
        }
    }

    private fun assertProgram() {
        require(glGetProgrami(program, GL_LINK_STATUS) != GL_FALSE) {
            "compute program $program link error: ${glGetProgramInfoLog(program)}"
        }
    }
}
