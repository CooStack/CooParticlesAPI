package cn.coostack.cooparticlesapi.renderer.shader

import net.minecraft.resources.ResourceLocation

data class ShaderCompileUpdate(
    val updatedProgramIds: Set<ResourceLocation> = emptySet(),
    val updatedShaderSources: Set<ResourceLocation> = emptySet()
) {
    fun isEmpty(): Boolean {
        return updatedProgramIds.isEmpty() && updatedShaderSources.isEmpty()
    }
}

object ShaderCompileUpdateCoordinator {
    @JvmStatic
    fun handleUpdate(update: ShaderCompileUpdate): ShaderRefreshResult {
        if (update.isEmpty()) {
            return ShaderRefreshResult(refreshedPrograms = 0, releasedBuffers = 0)
        }

        if (update.updatedProgramIds.isNotEmpty()) {
            val byId = ShaderProgramRegistry.refreshProgramsAndBuffersById(update.updatedProgramIds)
            if (byId.refreshedPrograms > 0 || byId.releasedBuffers > 0 || update.updatedShaderSources.isEmpty()) {
                return byId
            }
        }

        if (update.updatedShaderSources.isNotEmpty()) {
            return ShaderProgramRegistry.refreshProgramsAndBuffersBySource(update.updatedShaderSources)
        }

        return ShaderRefreshResult(refreshedPrograms = 0, releasedBuffers = 0)
    }

    @JvmStatic
    fun handleUpdatedProgramIds(ids: Set<ResourceLocation>): ShaderRefreshResult {
        return handleUpdate(ShaderCompileUpdate(updatedProgramIds = ids))
    }

    @JvmStatic
    fun handleUpdatedShaderSources(sources: Set<ResourceLocation>): ShaderRefreshResult {
        return handleUpdate(ShaderCompileUpdate(updatedShaderSources = sources))
    }
}
