package cn.coostack.cooparticlesapi.renderer.shader

import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShader
import cn.coostack.cooparticlesapi.renderer.shader.buffer.ShaderBufferCache
import cn.coostack.cooparticlesapi.renderer.shader.buffer.ShaderBufferLayout
import net.minecraft.resources.ResourceLocation
import org.lwjgl.opengl.GL33.*

class SimpleShaderProgram(
    override var vertexShader: GlShader,
    override var fragmentShader: GlShader,
    private val geometryShaderInternal: GlShader? = null,
    private val tessellationControlShaderInternal: GlShader? = null,
    private val tessellationEvaluationShaderInternal: GlShader? = null,
    private val shaderBufferLayoutsInternal: List<ShaderBufferLayout<*>> = emptyList(),
    private val attributeLocationsInternal: Map<String, Int> = emptyMap(),
    private val transformFeedbackVaryingsInternal: List<String> = emptyList(),
    private val managedProgramIdInternal: ResourceLocation? = null
) : CooShaderProgram {
    override var program: Int = 0
    private var prevProgram = 0

    override fun geometryShader(): GlShader? = geometryShaderInternal

    override fun tessellationControlShader(): GlShader? = tessellationControlShaderInternal

    override fun tessellationEvaluationShader(): GlShader? = tessellationEvaluationShaderInternal

    override fun shaderBufferLayouts(): List<ShaderBufferLayout<*>> = shaderBufferLayoutsInternal

    override fun managedProgramId(): ResourceLocation? = managedProgramIdInternal

    override fun init() {
        program = glCreateProgram()
        attachedShaders().forEach { shader ->
            shader.compile()
            glAttachShader(program, shader.shaderID())
        }
        attributeLocationsInternal.forEach { (name, location) ->
            glBindAttribLocation(program, location, name)
        }
        if (transformFeedbackVaryingsInternal.isNotEmpty()) {
            glTransformFeedbackVaryings(
                program,
                transformFeedbackVaryingsInternal.toTypedArray(),
                GL_INTERLEAVED_ATTRIBS,
            )
        }
        glLinkProgram(program)
        assertProgram()
        attachedShaders().forEach { shader ->
            shader.deleteShader()
        }
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
