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

    /**
     * 执行 `ComputeShaderProgram` 定义的 `shaderBufferLayouts` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`shaderBufferLayouts()`。
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    override fun shaderBufferLayouts(): List<ShaderBufferLayout<*>> = shaderBufferLayoutsInternal

    /**
     * 执行 `ComputeShaderProgram` 定义的 `managedProgramId` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`managedProgramId()`。
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    override fun managedProgramId(): ResourceLocation? = managedProgramIdInternal

    /**
     * 初始化或准备 `ComputeShaderProgram` 的 `init` 阶段，使后续渲染调用可以使用相关资源。
     *
     * 示例：`init()`。
     */
    override fun init() {
        program = glCreateProgram()
        computeShader.compile()
        glAttachShader(program, computeShader.shaderID())
        glLinkProgram(program)
        assertProgram()
        computeShader.deleteShader()
    }

    /**
     * 执行 `ComputeShaderProgram` 定义的 `use` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`use()`。
     */
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

    /**
     * 清理 `ComputeShaderProgram` 的 `reset` 状态，使缓存、绑定或 OpenGL 状态可以重新初始化。
     *
     * 示例：`reset()`。
     */
    override fun reset() {
        if (prevProgram > 0 && glIsProgram(prevProgram)) {
            glUseProgram(prevProgram)
        } else {
            glUseProgram(0)
        }
    }

    /**
     * 释放 `ComputeShaderProgram` 在 `release` 中管理的资源；再次使用前必须重新初始化。
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
            prevProgram = 0
        }
    }

    /**
     * 执行 `ComputeShaderProgram` 的 `dispatch` 渲染操作，处理传入数据并更新当前帧或 GPU 状态。
     *
     * 示例：`dispatch(x = x, y = y, z = z)`。
     *
     * @param x 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param y 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param z 本次计算使用的坐标、颜色、比例、范围或强度分量
     */
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

    /**
     * 执行 `ComputeShaderProgram` 定义的 `useOnContext` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`useOnContext(dispatchMethod = dispatchMethod)`。
     *
     * @param dispatchMethod 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
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
