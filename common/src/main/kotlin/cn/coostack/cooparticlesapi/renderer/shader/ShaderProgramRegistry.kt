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

    /**
     * 把输入对象加入 `ShaderProgramRegistry` 的 `register` 管理范围，后续查询、构建或绘制会使用该绑定。
     *
     * 示例：`register(program = program)`。
     *
     * @param program 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun register(program: CooShaderProgram): CooShaderProgram {
        graphicsPrograms += program
        return program
    }

    /**
     * 把输入对象加入 `ShaderProgramRegistry` 的 `register` 管理范围，后续查询、构建或绘制会使用该绑定。
     *
     * 示例：`register(program = program)`。
     *
     * @param program 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
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

    /**
     * 从 `ShaderProgramRegistry` 的 `unregister` 管理范围移除目标，后续调用不再使用对应绑定或资源。
     *
     * 示例：`unregister(program = program)`。
     *
     * @param program 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun unregister(program: CooComputeShaderProgram): Boolean {
        program.release()
        return computePrograms.remove(program)
    }

    /**
     * 从 `ShaderProgramRegistry` 当前维护的状态中读取 `graphicsCount` 结果，不创建新的渲染资源。
     *
     * 示例：`graphicsCount()`。
     *
     * @return 当前集合、资源或实例的数量
     */
    fun graphicsCount(): Int = graphicsPrograms.size

    /**
     * 从 `ShaderProgramRegistry` 当前维护的状态中读取 `computeCount` 结果，不创建新的渲染资源。
     *
     * 示例：`computeCount()`。
     *
     * @return 当前集合、资源或实例的数量
     */
    fun computeCount(): Int = computePrograms.size

    /**
     * 清理 `ShaderProgramRegistry` 的 `invalidateProgramsById` 状态，使缓存、绑定或 OpenGL 状态可以重新初始化。
     *
     * 示例：`invalidateProgramsById(ids = ids)`。
     *
     * @param ids 要批量处理的元素集合；集合内容会直接影响本次构建、绑定或渲染结果
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
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

    /**
     * 清理 `ShaderProgramRegistry` 的 `invalidateProgramsBySource` 状态，使缓存、绑定或 OpenGL 状态可以重新初始化。
     *
     * 示例：`invalidateProgramsBySource(sources = sources)`。
     *
     * @param sources 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
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

    /**
     * 执行 `ShaderProgramRegistry` 定义的 `reinitializeProgramsById` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`reinitializeProgramsById(ids = ids)`。
     *
     * @param ids 要批量处理的元素集合；集合内容会直接影响本次构建、绑定或渲染结果
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
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

    /**
     * 执行 `ShaderProgramRegistry` 定义的 `reinitializeProgramsBySource` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`reinitializeProgramsBySource(sources = sources)`。
     *
     * @param sources 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
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

    /**
     * 执行 `ShaderProgramRegistry` 定义的 `refreshProgramsById` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`refreshProgramsById(ids = ids)`。
     *
     * @param ids 要批量处理的元素集合；集合内容会直接影响本次构建、绑定或渲染结果
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun refreshProgramsById(ids: Set<ResourceLocation>): Int {
        invalidateProgramsById(ids)
        return reinitializeProgramsById(ids)
    }

    /**
     * 执行 `ShaderProgramRegistry` 定义的 `refreshProgramsBySource` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`refreshProgramsBySource(sources = sources)`。
     *
     * @param sources 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun refreshProgramsBySource(sources: Set<ResourceLocation>): Int {
        invalidateProgramsBySource(sources)
        return reinitializeProgramsBySource(sources)
    }

    /**
     * 执行 `ShaderProgramRegistry` 定义的 `refreshProgramsAndBuffersById` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`refreshProgramsAndBuffersById(ids = ids)`。
     *
     * @param ids 要批量处理的元素集合；集合内容会直接影响本次构建、绑定或渲染结果
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
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

    /**
     * 执行 `ShaderProgramRegistry` 定义的 `refreshProgramsAndBuffersBySource` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`refreshProgramsAndBuffersBySource(sources = sources)`。
     *
     * @param sources 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
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

    /**
     * 清理 `ShaderProgramRegistry` 的 `invalidateAll` 状态，使缓存、绑定或 OpenGL 状态可以重新初始化。
     *
     * 示例：`invalidateAll()`。
     */
    fun invalidateAll() {
        graphicsPrograms.forEach { it.release() }
        computePrograms.forEach { it.release() }
    }

    /**
     * 执行 `ShaderProgramRegistry` 定义的 `reinitializeAll` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`reinitializeAll()`。
     */
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

    /**
     * 释放 `ShaderProgramRegistry` 在 `releaseAll` 中管理的资源；再次使用前必须重新初始化。
     *
     * 示例：`releaseAll()`。
     */
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
