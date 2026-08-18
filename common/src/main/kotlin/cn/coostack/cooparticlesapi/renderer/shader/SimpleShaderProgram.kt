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
    private val previousPrograms = ArrayDeque<Int>()

    /**
     * 执行 `SimpleShaderProgram` 定义的 `geometryShader` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`geometryShader()`。
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    override fun geometryShader(): GlShader? = geometryShaderInternal

    /**
     * 执行 `SimpleShaderProgram` 定义的 `tessellationControlShader` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`tessellationControlShader()`。
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    override fun tessellationControlShader(): GlShader? = tessellationControlShaderInternal

    /**
     * 执行 `SimpleShaderProgram` 定义的 `tessellationEvaluationShader` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`tessellationEvaluationShader()`。
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    override fun tessellationEvaluationShader(): GlShader? = tessellationEvaluationShaderInternal

    /**
     * 执行 `SimpleShaderProgram` 定义的 `shaderBufferLayouts` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`shaderBufferLayouts()`。
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    override fun shaderBufferLayouts(): List<ShaderBufferLayout<*>> = shaderBufferLayoutsInternal

    /**
     * 执行 `SimpleShaderProgram` 定义的 `managedProgramId` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`managedProgramId()`。
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    override fun managedProgramId(): ResourceLocation? = managedProgramIdInternal

    /**
     * 初始化或准备 `SimpleShaderProgram` 的 `init` 阶段，使后续渲染调用可以使用相关资源。
     *
     * 示例：`init()`。
     */
    override fun init() {
        program = glCreateProgram()
        try {
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
        } catch (error: Throwable) {
            glDeleteProgram(program)
            program = 0
            throw error
        } finally {
            attachedShaders().forEach { shader ->
                shader.deleteShader()
            }
        }
    }

    /**
     * 执行 `SimpleShaderProgram` 定义的 `use` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`use()`。
     */
    override fun use() {
        if (program <= 0 || !glIsProgram(program)) {
            glUseProgram(0)
            return
        }
        val previousProgram = glGetInteger(GL_CURRENT_PROGRAM)
        previousPrograms.addLast(previousProgram)
        try {
            glUseProgram(program)
            ShaderBufferCache.bindAll(shaderBufferLayoutsInternal)
        } catch (error: Throwable) {
            previousPrograms.removeLast()
            try {
                restoreProgram(previousProgram)
            } catch (restoreError: Throwable) {
                error.addSuppressed(restoreError)
            }
            throw error
        }
    }

    /**
     * 清理 `SimpleShaderProgram` 的 `reset` 状态，使缓存、绑定或 OpenGL 状态可以重新初始化。
     *
     * 示例：`reset()`。
     */
    override fun reset() {
        if (previousPrograms.isEmpty()) return
        restoreProgram(previousPrograms.removeLast())
    }

    /**
     * 释放 `SimpleShaderProgram` 在 `release` 中管理的资源；再次使用前必须重新初始化。
     *
     * 示例：`release()`。
     */
    override fun release() {
        if (program > 0) {
            if (glGetInteger(GL_CURRENT_PROGRAM) == program) {
                glUseProgram(0)
            }
            glDeleteProgram(program)
            program = 0
            previousPrograms.clear()
        }
    }

    /**
     * 执行 `SimpleShaderProgram` 定义的 `useOnContext` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`useOnContext(drawMethod = drawMethod)`。
     *
     * @param drawMethod 在当前生命周期或数据上下文中执行的回调
     */
    override fun useOnContext(drawMethod: CooShaderProgram.() -> Unit) {
        if (program <= 0 || !glIsProgram(program)) {
            return
        }
        var stateCreated = false
        try {
            use()
            stateCreated = true
            drawMethod()
        } finally {
            if (stateCreated) reset()
        }
    }

    private fun restoreProgram(previousProgram: Int) {
        if (previousProgram > 0 && glIsProgram(previousProgram)) {
            glUseProgram(previousProgram)
        } else {
            glUseProgram(0)
        }
    }

    private fun assertProgram() {
        require(glGetProgrami(program, GL_LINK_STATUS) != GL_FALSE) {
            "program $program link error: ${glGetProgramInfoLog(program)} ${vertexShader.sourceLocation()} : ${fragmentShader.sourceLocation()}"
        }
    }
}
