package cn.coostack.cooparticlesapi.renderer.shader

import net.minecraft.resources.ResourceLocation

data class ShaderCompileUpdate(
    val updatedProgramIds: Set<ResourceLocation> = emptySet(),
    val updatedShaderSources: Set<ResourceLocation> = emptySet()
) {
    /**
     * 执行 `ShaderCompileUpdate` 定义的 `isEmpty` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`isEmpty()`。
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun isEmpty(): Boolean {
        return updatedProgramIds.isEmpty() && updatedShaderSources.isEmpty()
    }
}

object ShaderCompileUpdateCoordinator {
    @JvmStatic
    /**
     * 执行 `ShaderCompileUpdateCoordinator` 定义的 `handleUpdate` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`handleUpdate(update = update)`。
     *
     * @param update 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
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
    /**
     * 执行 `ShaderCompileUpdateCoordinator` 定义的 `handleUpdatedProgramIds` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`handleUpdatedProgramIds(ids = ids)`。
     *
     * @param ids 要批量处理的元素集合；集合内容会直接影响本次构建、绑定或渲染结果
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun handleUpdatedProgramIds(ids: Set<ResourceLocation>): ShaderRefreshResult {
        return handleUpdate(ShaderCompileUpdate(updatedProgramIds = ids))
    }

    @JvmStatic
    /**
     * 执行 `ShaderCompileUpdateCoordinator` 定义的 `handleUpdatedShaderSources` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`handleUpdatedShaderSources(sources = sources)`。
     *
     * @param sources 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun handleUpdatedShaderSources(sources: Set<ResourceLocation>): ShaderRefreshResult {
        return handleUpdate(ShaderCompileUpdate(updatedShaderSources = sources))
    }
}
