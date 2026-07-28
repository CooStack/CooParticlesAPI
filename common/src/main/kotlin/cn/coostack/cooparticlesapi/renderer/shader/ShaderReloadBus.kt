package cn.coostack.cooparticlesapi.renderer.shader

import cn.coostack.cooparticlesapi.CooParticlesAPIClient
import cn.coostack.cooparticlesapi.display.CooRenderTypeResourceRegistry
import cn.coostack.cooparticlesapi.particles.CooParticleTextureSheet
import cn.coostack.cooparticlesapi.renderer.shader.buffer.ShaderBufferCache
import cn.coostack.cooparticlesapi.test.options.display.MCShaders
import net.minecraft.server.packs.resources.ResourceManager
import java.util.concurrent.CopyOnWriteArrayList

/**
 * shader 重载总线中使用的信号类型。
 */
sealed interface ShaderReloadSignal {
    /**
     * 完整资源重载信号。
     *
     * 通常意味着资源管理器整体刷新，需要重新初始化 shader、render type 和缓冲缓存。
     */
    data class FullReload(
        val resourceManager: ResourceManager
    ) : ShaderReloadSignal

    /**
     * 增量编译更新信号。
     *
     * 只刷新被判定为需要重新编译或重新绑定的 shader/program。
     */
    data class CompileUpdate(
        val update: ShaderCompileUpdate
    ) : ShaderReloadSignal
}

/**
 * 一次 shader 重载分发的结果摘要。
 */
data class ShaderReloadDispatchResult(
    val fullReloadTriggered: Boolean = false,
    val refreshResult: ShaderRefreshResult = ShaderRefreshResult(
        refreshedPrograms = 0,
        releasedBuffers = 0
    )
) {
    /**
     * 合并两个分发结果。
     */
    fun merge(other: ShaderReloadDispatchResult): ShaderReloadDispatchResult {
        return ShaderReloadDispatchResult(
            fullReloadTriggered = fullReloadTriggered || other.fullReloadTriggered,
            refreshResult = ShaderRefreshResult(
                refreshedPrograms = refreshResult.refreshedPrograms + other.refreshResult.refreshedPrograms,
                releasedBuffers = refreshResult.releasedBuffers + other.refreshResult.releasedBuffers
            )
        )
    }
}

/**
 * shader 重载事件监听器。
 */
fun interface ShaderReloadListener {
    /**
     * 处理一次 shader 重载信号。
     *
     * 返回 `null` 表示该监听器本次没有产生结果。
     */
    fun onShaderReload(signal: ShaderReloadSignal): ShaderReloadDispatchResult?
}

/**
 * shader 重载总线。
 *
 * Fabric/NeoForge 的 reload listener 最终都会把事件汇聚到这里，
 * 再统一分发给已注册监听器。
 */
object ShaderReloadBus {
    private val listeners = CopyOnWriteArrayList<ShaderReloadListener>()

    init {
        register(DefaultShaderReloadStrategy)
    }

    /**
     * 注册一个 shader 重载监听器。
     *
     * 返回值就是原监听器本身，便于调用方保存引用后再注销。
     */
    @JvmStatic
    fun register(listener: ShaderReloadListener): ShaderReloadListener {
        listeners += listener
        return listener
    }

    /**
     * 注销一个监听器。
     */
    @JvmStatic
    fun unregister(listener: ShaderReloadListener) {
        listeners -= listener
    }

    /**
     * 分发一个重载信号给所有监听器，并把结果合并返回。
     */
    @JvmStatic
    fun dispatch(signal: ShaderReloadSignal): ShaderReloadDispatchResult {
        var result = ShaderReloadDispatchResult()
        listeners.forEach { listener ->
            val dispatchResult = listener.onShaderReload(signal) ?: return@forEach
            result = result.merge(dispatchResult)
        }
        return result
    }

    /**
     * 默认重载策略。
     *
     * 仓库内没有额外自定义监听器时，也至少会由它处理完整重载和编译更新。
     */
    private object DefaultShaderReloadStrategy : ShaderReloadListener {
        override fun onShaderReload(signal: ShaderReloadSignal): ShaderReloadDispatchResult {
            return when (signal) {
                is ShaderReloadSignal.FullReload -> handleFullReload(signal.resourceManager)
                is ShaderReloadSignal.CompileUpdate -> ShaderReloadDispatchResult(
                    refreshResult = ShaderCompileUpdateCoordinator.handleUpdate(signal.update)
                )
            }
        }

        /**
         * 处理一次完整 shader 资源重载。
         */
        private fun handleFullReload(resourceManager: ResourceManager): ShaderReloadDispatchResult {
            MCShaders.init(resourceManager)
            CooParticleTextureSheet.reloadShader(resourceManager)
            CooRenderTypeResourceRegistry.reload(resourceManager)
            ShaderProgramRegistry.invalidateAll()
            ShaderBufferCache.releaseAll()
            CooParticlesAPIClient.reloadShaderPrograms()
            return ShaderReloadDispatchResult(fullReloadTriggered = true)
        }
    }
}
