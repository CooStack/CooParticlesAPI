package cn.coostack.cooparticlesapi.renderer.shader.api

import cn.coostack.cooparticlesapi.renderer.shader.buffer.ShaderBufferLayout
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShader
import net.minecraft.resources.ResourceLocation

/**
 * 标准图形 shader program 抽象。
 *
 * 它统一封装了 vertex/fragment 以及可选 geometry/tessellation 阶段。
 */
interface CooShaderProgram : CooProgramUniformAccess {
    /**
     * 当前 program 的顶点着色器。
     */
    var vertexShader: GlShader
    /**
     * 当前 program 的片元着色器。
     */
    var fragmentShader: GlShader

    /**
     * 可选的几何着色器。
     */
    fun geometryShader(): GlShader? = null

    /**
     * 可选的曲面细分控制着色器。
     */
    fun tessellationControlShader(): GlShader? = null

    /**
     * 可选的曲面细分求值着色器。
     */
    fun tessellationEvaluationShader(): GlShader? = null

    /**
     * 返回当前 program 实际附着的全部 shader。
     */
    fun attachedShaders(): List<GlShader> {
        return listOfNotNull(
            vertexShader,
            tessellationControlShader(),
            tessellationEvaluationShader(),
            geometryShader(),
            fragmentShader
        )
    }

    /**
     * 返回该程序依赖的 shader buffer layout 列表。
     */
    fun shaderBufferLayouts(): List<ShaderBufferLayout<*>> = emptyList()

    /**
     * 返回受统一注册表管理的 program id。
     */
    fun managedProgramId(): ResourceLocation? = null

    /**
     * 返回当前 program 依赖的 shader 源文件集合。
     */
    fun shaderSources(): Set<ResourceLocation> {
        return attachedShaders().mapNotNull { it.sourceLocation() }.toSet()
    }

    /**
     * 初始化 program 与相关 GPU 资源。
     */
    fun init()

    /**
     * 绑定当前 program。
     */
    fun use()

    /**
     * 解除当前 program 的使用状态。
     */
    fun reset()

    /**
     * 释放当前 program 与相关 GPU 资源。
     */
    fun release()

    /**
     * 在绑定当前 program 的作用域内执行一段绘制逻辑。
     *
     * 一般会在内部自动 `use()`，执行完成后再 `reset()`。
     */
    fun useOnContext(drawMethod: CooShaderProgram.() -> Unit)
}
