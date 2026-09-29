package cn.coostack.cooparticlesapi

import cn.coostack.cooparticlesapi.renderer.shader.CooShaderSourceLoader
import cn.coostack.cooparticlesapi.renderer.shader.ShaderCompileUpdate
import cn.coostack.cooparticlesapi.renderer.shader.ShaderReloadBus
import cn.coostack.cooparticlesapi.renderer.shader.ShaderReloadDispatchResult
import cn.coostack.cooparticlesapi.renderer.shader.ShaderReloadListener
import cn.coostack.cooparticlesapi.renderer.shader.ShaderReloadSignal
import cn.coostack.cooparticlesapi.renderer.shader.ShaderRefreshResult
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramRegistry
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager

object CooShaderReloadSupport {
    @JvmStatic
    fun reload(resourceManager: ResourceManager) {
        ShaderReloadBus.dispatch(ShaderReloadSignal.FullReload(resourceManager))
    }

    /**
     * 读取一个 Coo shader 源码，并展开其中的 `#coo_import`。
     *
     * 与 GPU 编译路径使用同一个加载器，因此调用方拿到的是**驱动真正会编译的文本**。
     * 需要在不启动客户端的情况下检查 shader 语法（例如编译回归测试）时使用本入口，
     * 不要直接按原版规则读取源码文件——那样会拿到未展开的 `#coo_import`，导致驱动报未知指令。
     *
     * @param source 程序自身的资源位置；path 不含 `shaders/` 前缀
     * @return 展开后的源码
     * @throws IllegalArgumentException 程序自身或任一 include 在 classpath 上不存在时抛出
     */
    @JvmStatic
    fun loadShaderSource(source: ResourceLocation): String =
        CooShaderSourceLoader.loadFromClasspath(source)

    @JvmStatic
    fun refreshProgramsById(ids: Set<ResourceLocation>): Int {
        return ShaderProgramRegistry.refreshProgramsById(ids)
    }

    @JvmStatic
    fun refreshProgramsBySource(sources: Set<ResourceLocation>): Int {
        return ShaderProgramRegistry.refreshProgramsBySource(sources)
    }

    @JvmStatic
    fun refreshProgramsAndBuffersById(ids: Set<ResourceLocation>): ShaderRefreshResult {
        return handleCompileUpdate(
            ShaderCompileUpdate(updatedProgramIds = ids)
        )
    }

    @JvmStatic
    fun refreshProgramsAndBuffersBySource(sources: Set<ResourceLocation>): ShaderRefreshResult {
        return handleCompileUpdate(
            ShaderCompileUpdate(updatedShaderSources = sources)
        )
    }

    @JvmStatic
    fun handleCompileUpdate(update: ShaderCompileUpdate): ShaderRefreshResult {
        return ShaderReloadBus.dispatch(
            ShaderReloadSignal.CompileUpdate(update)
        ).refreshResult
    }

    @JvmStatic
    fun dispatch(signal: ShaderReloadSignal): ShaderReloadDispatchResult {
        return ShaderReloadBus.dispatch(signal)
    }

    @JvmStatic
    fun registerReloadListener(listener: ShaderReloadListener): ShaderReloadListener {
        return ShaderReloadBus.register(listener)
    }

    @JvmStatic
    fun unregisterReloadListener(listener: ShaderReloadListener) {
        ShaderReloadBus.unregister(listener)
    }
}
