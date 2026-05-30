package cn.coostack.cooparticlesapi.renderer.effects

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionCollector

/**
 * 当前帧的 descriptor graph 收集与执行器。
 *
 * 它负责：
 * - 收集所有实体提交的 `RenderEffectDescriptor`
 * - 依据 backend capability 过滤
 * - 依据优先级和提交顺序稳定排序
 * - 按连续的 executor 批次分发给 `RenderEffectRegistry`
 */
class RenderEffectGraph(
    private val backendCapabilities: Set<RenderBackendCapability>,
    private val frameContext: RenderFrameContext
) : RenderEffectCollector, RenderContributionCollector {
    private val descriptors = mutableListOf<IndexedDescriptor>()
    private var nextSequence = 0L

    /**
     * 向当前图中提交一个 descriptor。
     */
    override fun submit(effect: RenderEffectDescriptor) {
        CooParticlesConstants.logger.debug(
            "Render effect submitted type={} id={} source={} priority={} required={}",
            effect.effectType,
            effect.effectId,
            effect.sourceInstanceId,
            effect.priority,
            effect.requiredCapabilities
        )
        descriptors += IndexedDescriptor(sequence = nextSequence++, descriptor = effect)
    }

    /**
     * 执行当前图中的全部 descriptor。
     *
     * 执行前会先按能力过滤与排序，再把连续使用同一 executor 的 descriptor 合批执行。
     */
    fun execute() {
        var currentExecutor: RenderEffectExecutor? = null
        val currentBatch = mutableListOf<RenderEffectDescriptor>()

        fun flushBatch() {
            val executor = currentExecutor ?: return
            if (currentBatch.isEmpty()) {
                return
            }
            executor.render(frameContext, currentBatch.toList())
            currentBatch.clear()
        }

        orderedDescriptors().forEach { indexed ->
            val descriptor = indexed.descriptor
            val executor = RenderEffectRegistry.get(descriptor.effectType)
            if (executor == null) {
                flushBatch()
                currentExecutor = null
                CooParticlesConstants.logger.warn(
                    "Skipping render effect type={} because no executor is registered",
                    descriptor.effectType
                )
                return@forEach
            }
            if (currentExecutor !== executor) {
                flushBatch()
                currentExecutor = executor
            }
            currentBatch += descriptor
        }
        flushBatch()
    }

    /**
     * 返回经过 capability 过滤和稳定排序后的 descriptor 列表。
     */
    private fun orderedDescriptors(): List<IndexedDescriptor> {
        return descriptors
            .asSequence()
            .filter { indexed ->
                backendCapabilities.containsAll(indexed.descriptor.requiredCapabilities)
            }
            .sortedWith(
                compareBy<IndexedDescriptor> { it.descriptor.priority }
                    .thenBy { it.sequence }
            )
            .toList()
    }

    /**
     * 内部排序辅助结构。
     *
     * `sequence` 用于在完全同优先级/同键值时保持提交顺序稳定。
     */
    private data class IndexedDescriptor(
        val sequence: Long,
        val descriptor: RenderEffectDescriptor
    )
}
