package cn.coostack.cooparticlesapi.renderer.terrain

import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineNodeKind
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
    private val topologyChanged = AtomicLong()
    private val topologyRegions = ArrayList<TopologyRegion>()

    /** 安装或替换完整 Mapping 快照。 */
    @Synchronized
    fun install(instance: CooTerrainMappingInstance) {
        val key = MappingKey(instance.dimension, instance.instanceId)
        if (!acceptRevision(key, instance.revision)) return
        val previous = mappings.put(key, instance)
        changed.incrementAndGet()
        if (previous == null || topologySignature(previous) != topologySignature(instance)) {
            previous?.let { previousInstance ->
                if (requiresTerrainGeometry(previousInstance)) {
                    topologyRegions += TopologyRegion(previousInstance.dimension, previousInstance.region)
                }
            }
            if (requiresTerrainGeometry(instance)) {
                topologyRegions += TopologyRegion(instance.dimension, instance.region)
            }
            topologyChanged.incrementAndGet()
        }
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
        val removed = mappings.remove(key)
        if (removed != null) {
            if (requiresTerrainGeometry(removed)) {
                topologyRegions += TopologyRegion(removed.dimension, removed.region)
            }
            topologyChanged.incrementAndGet()
        }
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

    /** 返回客户端当前持有的程序化 Terrain mapping 实例数。 */
    fun activeMappingCount(): Int = mappings.size

    /**
     * 返回指定维度当前持有的全部 mapping 实例，包含暂停和已过期的实例。
     *
     * 仅供调试渲染读取，与 [active] 不同的是不做生命周期过滤，便于观察“为什么没有生效”。
     *
     * @param dimension 查询维度 ID
     * @return 按优先级和序号排序的 mapping 实例
     */
    fun debugMappings(dimension: ResourceLocation): List<CooTerrainMappingInstance> = mappings.values
        .asSequence()
        .filter { it.dimension == dimension }
        .sortedWith(mappingComparator())
        .toList()

    /** 返回包含区域和 uniform 更新的状态版本，供运行时快照观察。 */
    fun revision(): Long = changed.get()

    /** 返回只在绘制拓扑变化时递增的版本，供 section 几何重建使用。 */
    fun topologyRevision(): Long = topologyChanged.get()

    /** 取出最近 topology 变化涉及的 region，用于定向标记 terrain section。 */
    @Synchronized
    fun drainTopologyRegions(dimension: ResourceLocation): List<CooTerrainMappingRegion> {
        val selected = topologyRegions.filter { it.dimension == dimension }.map(TopologyRegion::region)
        topologyRegions.removeIf { it.dimension == dimension }
        return selected
    }

    @Synchronized
    fun clear() {
        mappings.clear()
        revisions.clear()
        topologyRegions.clear()
        changed.incrementAndGet()
        topologyChanged.incrementAndGet()
    }

    private fun topologySignature(instance: CooTerrainMappingInstance): TopologySignature = TopologySignature(
        instance.mappingId,
        instance.priority,
        instance.composition,
        instance.sequence,
        instance.isPaused(),
        instance.region,
    )

    private fun requiresTerrainGeometry(instance: CooTerrainMappingInstance): Boolean {
        return CooTerrainMappingManager.pipeline(instance.mappingId)?.nodes?.any {
            it.kind == CooPipelineNodeKind.WORLD
        } == true
    }

    private data class TopologySignature(
        val mappingId: ResourceLocation,
        val priority: Int,
        val composition: CooTerrainEffectComposition,
        val sequence: Long,
        val paused: Boolean,
        val region: CooTerrainMappingRegion,
    )

    private fun acceptRevision(key: MappingKey, revision: Long): Boolean {
        val previous = revisions[key]
        if (previous != null && revision <= previous) return false
        revisions[key] = revision
        return true
    }

    private data class TopologyRegion(
        val dimension: ResourceLocation,
        val region: CooTerrainMappingRegion
    )

    private data class MappingKey(val dimension: ResourceLocation, val id: ResourceLocation)
}
