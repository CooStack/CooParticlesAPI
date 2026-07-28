package cn.coostack.cooparticlesapi.renderer.shader

import cn.coostack.cooparticlesapi.renderer.shader.api.CooComputeShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.buffer.ShaderBufferCache
import cn.coostack.cooparticlesapi.renderer.shader.buffer.ShaderBufferLayout
import net.minecraft.resources.ResourceLocation

data class ShaderRefreshResult(
    val refreshedPrograms: Int,
    val releasedBuffers: Int
)

object ShaderProgramRegistry {
    private val graphicsPrograms = LinkedHashSet<CooShaderProgram>()
    private val computePrograms = LinkedHashSet<CooComputeShaderProgram>()

    fun register(program: CooShaderProgram): CooShaderProgram {
        graphicsPrograms += program
        return program
    }

    fun register(program: CooComputeShaderProgram): CooComputeShaderProgram {
        computePrograms += program
        return program
    }

    /**
     * 释放并注销一个图形 program。
     *
     * Example: CParticle 完全释放时用它同步清理单例缓存和统一注册表。
     * Forbidden: 临时失效后还需要参加资源重载的 program 不能注销。
     *
     * @param program 不再由注册表管理的图形 program
     * @return 注销前 program 是否在注册表中
     */
    fun unregister(program: CooShaderProgram): Boolean {
        program.release()
        return graphicsPrograms.remove(program)
    }

    fun unregister(program: CooComputeShaderProgram): Boolean {
        program.release()
        return computePrograms.remove(program)
    }

    fun graphicsCount(): Int = graphicsPrograms.size

    fun computeCount(): Int = computePrograms.size

    fun invalidateProgramsById(ids: Set<ResourceLocation>): Int {
        var invalidated = 0
        graphicsPrograms.filter { it.managedProgramId() in ids }.forEach {
            it.release()
            invalidated++
        }
        computePrograms.filter { it.managedProgramId() in ids }.forEach {
            it.release()
            invalidated++
        }
        return invalidated
    }

    fun invalidateProgramsBySource(sources: Set<ResourceLocation>): Int {
        var invalidated = 0
        graphicsPrograms.filter { it.shaderSources().any(sources::contains) }.forEach {
            it.release()
            invalidated++
        }
        computePrograms.filter { it.shaderSources().any(sources::contains) }.forEach {
            it.release()
            invalidated++
        }
        return invalidated
    }

    fun reinitializeProgramsById(ids: Set<ResourceLocation>): Int {
        var reinitialized = 0
        graphicsPrograms.filter { it.managedProgramId() in ids }.forEach { program ->
            if (program.program == 0) {
                program.init()
                reinitialized++
            }
        }
        computePrograms.filter { it.managedProgramId() in ids }.forEach { program ->
            if (program.program == 0) {
                program.init()
                reinitialized++
            }
        }
        return reinitialized
    }

    fun reinitializeProgramsBySource(sources: Set<ResourceLocation>): Int {
        var reinitialized = 0
        graphicsPrograms.filter { it.shaderSources().any(sources::contains) }.forEach { program ->
            if (program.program == 0) {
                program.init()
                reinitialized++
            }
        }
        computePrograms.filter { it.shaderSources().any(sources::contains) }.forEach { program ->
            if (program.program == 0) {
                program.init()
                reinitialized++
            }
        }
        return reinitialized
    }

    fun refreshProgramsById(ids: Set<ResourceLocation>): Int {
        invalidateProgramsById(ids)
        return reinitializeProgramsById(ids)
    }

    fun refreshProgramsBySource(sources: Set<ResourceLocation>): Int {
        invalidateProgramsBySource(sources)
        return reinitializeProgramsBySource(sources)
    }

    fun refreshProgramsAndBuffersById(ids: Set<ResourceLocation>): ShaderRefreshResult {
        if (ids.isEmpty()) {
            return ShaderRefreshResult(refreshedPrograms = 0, releasedBuffers = 0)
        }
        val releasedBuffers = ShaderBufferCache.releaseLayouts(collectBufferLayoutsById(ids))
        val refreshedPrograms = refreshProgramsById(ids)
        return ShaderRefreshResult(
            refreshedPrograms = refreshedPrograms,
            releasedBuffers = releasedBuffers
        )
    }

    fun refreshProgramsAndBuffersBySource(sources: Set<ResourceLocation>): ShaderRefreshResult {
        if (sources.isEmpty()) {
            return ShaderRefreshResult(refreshedPrograms = 0, releasedBuffers = 0)
        }
        val releasedBuffers = ShaderBufferCache.releaseLayouts(collectBufferLayoutsBySource(sources))
        val refreshedPrograms = refreshProgramsBySource(sources)
        return ShaderRefreshResult(
            refreshedPrograms = refreshedPrograms,
            releasedBuffers = releasedBuffers
        )
    }

    fun invalidateAll() {
        graphicsPrograms.forEach { it.release() }
        computePrograms.forEach { it.release() }
    }

    fun reinitializeAll() {
        graphicsPrograms.forEach { program ->
            if (program.program == 0) {
                program.init()
            }
        }
        computePrograms.forEach { program ->
            if (program.program == 0) {
                program.init()
            }
        }
    }

    fun releaseAll() {
        invalidateAll()
        graphicsPrograms.clear()
        computePrograms.clear()
    }

    private fun collectBufferLayoutsById(ids: Set<ResourceLocation>): List<ShaderBufferLayout<*>> {
        return buildList {
            graphicsPrograms
                .filter { it.managedProgramId() in ids }
                .forEach { addAll(it.shaderBufferLayouts()) }
            computePrograms
                .filter { it.managedProgramId() in ids }
                .forEach { addAll(it.shaderBufferLayouts()) }
        }
    }

    private fun collectBufferLayoutsBySource(sources: Set<ResourceLocation>): List<ShaderBufferLayout<*>> {
        return buildList {
            graphicsPrograms
                .filter { it.shaderSources().any(sources::contains) }
                .forEach { addAll(it.shaderBufferLayouts()) }
            computePrograms
                .filter { it.shaderSources().any(sources::contains) }
                .forEach { addAll(it.shaderBufferLayouts()) }
        }
    }
}
