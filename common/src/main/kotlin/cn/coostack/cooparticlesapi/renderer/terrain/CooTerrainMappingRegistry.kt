package cn.coostack.cooparticlesapi.renderer.terrain

import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import net.minecraft.resources.ResourceLocation
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 客户端程序化 Mapping 状态表。
 *
 * 表中只保存区域和参数快照；地形片元通过 [CooTerrainMappingRegion] 的参数自行计算成员关系。
 */
internal object CooTerrainMappingRegistry {
    private val mappings = ConcurrentHashMap<MappingKey, CooTerrainMappingInstance>()
    private val revisions = ConcurrentHashMap<MappingKey, Long>()
    private val changed = AtomicLong()

    /** 安装或替换完整 Mapping 快照。 */
    @Synchronized
    fun install(instance: CooTerrainMappingInstance) {
        val key = MappingKey(instance.dimension, instance.instanceId)
        if (!acceptRevision(key, instance.revision)) return
        mappings[key] = instance
        changed.incrementAndGet()
    }

    /** 替换一个实例的完整 uniform 集合。 */
    @Synchronized
    fun updateUniforms(
        dimension: ResourceLocation,
        instanceId: ResourceLocation,
        revision: Long,
        uniforms: Map<String, CooUniformValue>
    ) {
        val key = MappingKey(dimension, instanceId)
        if (!acceptRevision(key, revision)) return
        val current = mappings[key] ?: return
        mappings[key] = current.copy(uniforms = uniforms.toMap(), revision = revision)
        changed.incrementAndGet()
    }

    /** 删除 Mapping 实例并保留 revision tombstone。 */
    @Synchronized
    fun remove(dimension: ResourceLocation, instanceId: ResourceLocation, revision: Long) {
        val key = MappingKey(dimension, instanceId)
        if (!acceptRevision(key, revision)) return
        mappings.remove(key)
        changed.incrementAndGet()
    }

    /** 返回当前维度按优先级和序号排序的原始 Mapping 快照。 */
    fun active(dimension: ResourceLocation, gameTime: Long): List<CooTerrainMappingInstance> = mappings.values
        .asSequence()
        .filter { it.dimension == dimension }
        .filter { !it.isPaused() }
        .filter { it.expiresAt?.let { expiresAt -> gameTime < expiresAt } ?: true }
        .sortedWith(mappingComparator())
        .toList()

    /**
     * 按确定性顺序生成实际绘制计划。
     *
     * 第一层 REPLACE 成为基底；后续 REPLACE 被抑制，透明和加色层保持原顺序。
     */
    fun activeRenderPlan(dimension: ResourceLocation, gameTime: Long): List<CooTerrainMappingInstance> {
        var replaceSelected = false
        return active(dimension, gameTime).filter { mapping ->
            if (mapping.composition != CooTerrainEffectComposition.REPLACE) return@filter true
            if (replaceSelected) return@filter false
            replaceSelected = true
            true
        }
    }

    /** 按批次身份读取当前快照，使 uniform/区域替换无需创建新的 RenderType。 */
    fun current(batchKey: CooTerrainMappingBatchKey): CooTerrainMappingInstance? {
        return mappings[MappingKey(batchKey.dimension, batchKey.instanceId)]
    }

    private fun mappingComparator(): Comparator<CooTerrainMappingInstance> =
        compareByDescending<CooTerrainMappingInstance> { it.priority }
            .thenByDescending { it.sequence }
            .thenBy { it.instanceId.toString() }

    /** 返回状态版本，供帧入口在参数变化后重建 Mapping render type。 */
    fun revision(): Long = changed.get()

    /** 清理客户端世界切换或断开连接时的 Mapping 状态。 */
    @Synchronized
    fun clear() {
        mappings.clear()
        revisions.clear()
        changed.incrementAndGet()
    }

    private fun acceptRevision(key: MappingKey, revision: Long): Boolean {
        val previous = revisions[key]
        if (previous != null && revision <= previous) return false
        revisions[key] = revision
        return true
    }

    private data class MappingKey(val dimension: ResourceLocation, val id: ResourceLocation)
}
