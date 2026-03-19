package cn.coostack.cooparticlesapi.renderer.shader.api

import cn.coostack.cooparticlesapi.renderer.shader.buffer.ShaderBufferLayout
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShader
import net.minecraft.resources.ResourceLocation

/**
 * compute shader 程序抽象。
 *
 * 这是仓库里对 OpenGL compute program 的统一封装接口。
 */
interface CooComputeShaderProgram : CooProgramUniformAccess {
    /**
     * 当前 program 绑定的 compute shader。
     */
    var computeShader: GlShader

    /**
     * 返回该程序使用的 shader buffer layout 列表。
     */
    fun shaderBufferLayouts(): List<ShaderBufferLayout<*>> = emptyList()

    /**
     * 返回受统一注册表管理的 program id。
     *
     * 返回 `null` 表示该程序不参与托管式刷新。
     */
    fun managedProgramId(): ResourceLocation? = null

    /**
     * 返回该程序依赖的 shader 源文件集合。
     */
    fun shaderSources(): Set<ResourceLocation> = listOfNotNull(computeShader.sourceLocation()).toSet()

    /**
     * 初始化 program 与相关 GPU 资源。
     */
    fun init()

    /**
     * 绑定当前 compute program。
     */
    fun use()

    /**
     * 解除当前 program 的使用状态。
     */
    fun reset()

    /**
     * 释放 program 与相关 GPU 资源。
     */
    fun release()

    /**
     * 发起一次 compute dispatch。
     */
    fun dispatch(x: Int, y: Int = 1, z: Int = 1)

    /**
     * 在绑定当前 program 的作用域内执行一段 compute 逻辑。
     *
     * 一般会在内部自动 `use()`，执行完成后再 `reset()`。
     */
    fun useOnContext(dispatchMethod: CooComputeShaderProgram.() -> Unit)
}
