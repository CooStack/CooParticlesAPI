package cn.coostack.cooparticlesapi

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
