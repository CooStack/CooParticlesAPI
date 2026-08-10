package cn.coostack.cooparticlesapi.renderer.terrain

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.pipeline.CooRenderPipeline
import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.state.BlockState
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * 管理客户端收到的地形效果组、位置反向索引和 Pipeline 配置缓存。
 *
 * 网络包通过 [install]、[append]、[removePositions]、[updateUniforms] 和 [remove] 写入状态；
 * 区块渲染通过 [groupsAt] 查询当前位置已生效的组。Registry 只保存通用数据，不引用
 * Minecraft 客户端实例或 OpenGL 对象，因此网络处理和区块构建不需要跨越渲染状态边界。
 * 每次可见状态变化都会记录受影响位置并推进 [revision]，帧入口随后调用
 * [drainChangedPositions] 触发局部 section 重建。
 */
internal object CooTerrainEffectRegistry {
    private val groups = ConcurrentHashMap<GroupKey, StoredGroup>()
    private val pendingGroups = ConcurrentHashMap<GroupKey, CooTerrainEffectGroupSnapshot>()
    private val configuredPipelines = ConcurrentHashMap<PipelineConfigKey, CooRenderPipeline<BlockState>>()
    private val positionIndex = ConcurrentHashMap<PositionKey, CopyOnWriteArrayList<GroupKey>>()
    private val changedPositions = ConcurrentHashMap.newKeySet<PositionKey>()
    /** 保存每个组最后接受的协议 revision，包括近期已删除组的 tombstone。 */
    private val protocolRevisions = ConcurrentHashMap<GroupKey, Long>()
    private val revisionCounter = AtomicLong()
    private val missingPipelines = ConcurrentHashMap.newKeySet<ResourceLocation>()

    /**
     * 安装或替换一个完整效果组快照。
     *
     * revision 过期的快照会被忽略；Pipeline 尚未注册时快照会进入等待队列，并保留原版地形。
     *
     * @param snapshot 服务端同步的完整效果组状态
     */
    @Synchronized
    fun install(snapshot: CooTerrainEffectGroupSnapshot) {
        val key = GroupKey(snapshot.dimension, snapshot.id)
        if (!acceptRevision(key, snapshot.revision)) return
        installAccepted(key, snapshot)
    }

    /** 在 revision 已通过校验后解析快照所引用的 Pipeline。 */
    private fun installAccepted(key: GroupKey, snapshot: CooTerrainEffectGroupSnapshot) {
        val template = CooTerrainEffectManager.pipeline(snapshot.pipelineId)
        if (template == null) {
            pendingGroups[key] = snapshot
            if (detachGroup(key) != null) {
                pruneConfiguredPipelines()
                revisionCounter.incrementAndGet()
            }
            if (missingPipelines.add(snapshot.pipelineId)) {
                CooParticlesConstants.logger.error(
                    "Terrain effect group {} references unregistered pipeline {}; keeping vanilla terrain",
                    snapshot.id,
                    snapshot.pipelineId
                )
            }
            return
        }
        installResolved(key, snapshot, template)
    }

    /** 将已找到 Pipeline 模板的快照写入主索引和位置反向索引。 */
    private fun installResolved(
        key: GroupKey,
        snapshot: CooTerrainEffectGroupSnapshot,
        template: CooRenderPipeline<BlockState>
    ) {
        pendingGroups.remove(key)
        val stored = StoredGroup(
            snapshot,
            configuredPipelines.computeIfAbsent(PipelineConfigKey(snapshot.pipelineId, snapshot.uniforms)) {
                template.uniformValues(snapshot.uniforms)
            }
        )
        detachGroup(key)
        groups[key] = stored
        stored.snapshot.activations.keys.forEach { position ->
            positionIndex.computeIfAbsent(PositionKey(snapshot.dimension, position)) {
                CopyOnWriteArrayList()
            }.addIfAbsent(key)
            changedPositions += PositionKey(snapshot.dimension, position)
        }
        pruneConfiguredPipelines()
        revisionCounter.incrementAndGet()
    }

    /**
     * 把一批绝对生效时间追加到现有组。
     *
     * @param dimension 组所属维度 ID
     * @param groupId 组 ID
     * @param revision 本次网络更新的单调版本号
     * @param additions 键为新增方块坐标，值为该位置的绝对生效 tick
     */
    @Synchronized
    fun append(
        dimension: ResourceLocation,
        groupId: ResourceLocation,
        revision: Long,
        additions: Map<BlockPos, Long>
    ) {
        val key = GroupKey(dimension, groupId)
        if (!acceptRevision(key, revision)) return
        if (additions.isEmpty()) return
        while (true) {
            val current = groups[key]
            if (current == null) {
                while (true) {
                    val pending = pendingGroups[key] ?: return
                    val updated = pending.copy(
                        activations = pending.activations + additions,
                        revision = revision
                    )
                    if (pendingGroups.replace(key, pending, updated)) {
                        return
                    }
                }
            }
            val added = additions.filterKeys { it !in current.snapshot.activations }
            if (added.isEmpty()) return
            val updated = current.copy(
                snapshot = current.snapshot.copy(
                    activations = current.snapshot.activations + added,
                    revision = revision
                )
            )
            if (groups.replace(key, current, updated)) {
                added.keys.forEach { position ->
                    val positionKey = PositionKey(dimension, position)
                    positionIndex.computeIfAbsent(positionKey) { CopyOnWriteArrayList() }.addIfAbsent(key)
                    changedPositions += positionKey
                }
                revisionCounter.incrementAndGet()
                return
            }
        }
    }

    /**
     * 按维度和组 ID 删除整个客户端效果组。
     *
     * @param dimension 组所属维度 ID
     * @param groupId 组 ID
     * @param revision 本次网络更新的单调版本号
     */
    @Synchronized
    fun remove(dimension: ResourceLocation, groupId: ResourceLocation, revision: Long) {
        val key = GroupKey(dimension, groupId)
        if (!acceptRevision(key, revision)) return
        pendingGroups.remove(key)
        if (detachGroup(key) == null) return
        pruneConfiguredPipelines()
        revisionCounter.incrementAndGet()
    }

    /**
     * 从已有组中删除指定方块位置，并标记对应 section 需要重建。
     *
     * @param dimension 组所属维度 ID
     * @param groupId 组 ID
     * @param revision 本次网络更新的单调版本号
     * @param positions 要删除的方块坐标
     */
    @Synchronized
    fun removePositions(
        dimension: ResourceLocation,
        groupId: ResourceLocation,
        revision: Long,
        positions: Set<BlockPos>
    ) {
        val key = GroupKey(dimension, groupId)
        if (!acceptRevision(key, revision)) return
        if (positions.isEmpty()) return
        while (true) {
            val current = groups[key]
            if (current == null) {
                while (true) {
                    val pending = pendingGroups[key] ?: return
                    val retained = pending.activations - positions
                    if (retained.size == pending.activations.size) return
                    val updated = pending.copy(activations = retained, revision = revision)
                    if (pendingGroups.replace(key, pending, updated)) {
                        return
                    }
                }
            }
            val removed = current.snapshot.activations.keys.intersect(positions)
            if (removed.isEmpty()) return
            val updated = current.copy(
                snapshot = current.snapshot.copy(
                    activations = current.snapshot.activations - removed,
                    revision = revision
                )
            )
            if (groups.replace(key, current, updated)) {
                removed.forEach { position ->
                    val positionKey = PositionKey(dimension, position)
                    positionIndex[positionKey]?.let { entries ->
                        entries.remove(key)
                        if (entries.isEmpty()) positionIndex.remove(positionKey, entries)
                    }
                    changedPositions += positionKey
                }
                revisionCounter.incrementAndGet()
                return
            }
        }
    }

    /**
     * 替换已有组的完整 uniform 集合，并复用或创建对应的已配置 Pipeline。
     *
     * @param dimension 组所属维度 ID
     * @param groupId 组 ID
     * @param revision 本次网络更新的单调版本号
     * @param uniforms 键为 shader uniform 名称，值为对应的 uniform 数据
     */
    @Synchronized
    fun updateUniforms(
        dimension: ResourceLocation,
        groupId: ResourceLocation,
        revision: Long,
        uniforms: Map<String, CooUniformValue>
    ) {
        val key = GroupKey(dimension, groupId)
        if (!acceptRevision(key, revision)) return
        while (true) {
            val current = groups[key]
            if (current == null) {
                while (true) {
                    val pending = pendingGroups[key] ?: return
                    if (pending.uniforms == uniforms) return
                    val updated = pending.copy(uniforms = uniforms, revision = revision)
                    if (pendingGroups.replace(key, pending, updated)) {
                        return
                    }
                }
            }
            if (current.snapshot.uniforms == uniforms) return
            val snapshot = current.snapshot.copy(uniforms = uniforms, revision = revision)
            val template = CooTerrainEffectManager.pipeline(snapshot.pipelineId) ?: return
            val updated = StoredGroup(
                snapshot = snapshot,
                pipeline = configuredPipelines.computeIfAbsent(PipelineConfigKey(snapshot.pipelineId, uniforms)) {
                    template.uniformValues(uniforms)
                }
            )
            if (groups.replace(key, current, updated)) {
                snapshot.activations.keys.forEach { position ->
                    changedPositions += PositionKey(dimension, position)
                }
                pruneConfiguredPipelines()
                revisionCounter.incrementAndGet()
                return
            }
        }
    }

    /**
     * 查询指定位置优先级最高的已生效效果组。
     *
     * @param dimension 查询维度 ID
     * @param position 查询方块坐标
     * @param gameTime 当前维度的绝对游戏 tick
     * @return sequence 最新的有效组；没有匹配时返回 `null`
     */
    fun groupAt(
        dimension: ResourceLocation,
        position: BlockPos,
        gameTime: Long
    ): CooResolvedTerrainEffectGroup? = groupsAt(dimension, position, gameTime).firstOrNull()

    /**
     * 查询指定位置全部已生效且未到期的效果组。
     *
     * @param dimension 查询维度 ID
     * @param position 查询方块坐标
     * @param gameTime 当前维度的绝对游戏 tick
     * @return 按 sequence 从新到旧排列的效果组
     */
    fun groupsAt(
        dimension: ResourceLocation,
        position: BlockPos,
        gameTime: Long
    ): List<CooResolvedTerrainEffectGroup> {
        val keys = positionIndex[PositionKey(dimension, position)].orEmpty()
        return keys.asSequence()
            .mapNotNull(groups::get)
            .filter { stored ->
                val activation = stored.snapshot.activations[position] ?: return@filter false
                gameTime >= activation && stored.snapshot.expiresAt?.let { gameTime < it } != false
            }
            .sortedByDescending { it.snapshot.sequence }
            .map { CooResolvedTerrainEffectGroup(it.snapshot, it.pipeline) }
            .toList()
    }

    /**
     * 清理指定维度已到期的组，并记录其位置需要局部重建。
     *
     * 到期时间已随快照同步，客户端无需等待服务端再次发送移除包。
     *
     * @param dimension 要推进的维度 ID
     * @param gameTime 当前维度的绝对游戏 tick
     */
    @Synchronized
    fun advance(dimension: ResourceLocation, gameTime: Long) {
        pendingGroups.entries.removeIf { (key, snapshot) ->
            key.dimension == dimension && snapshot.expiresAt?.let { gameTime >= it } == true
        }
        val expired = groups.entries.asSequence()
            .filter { (key, stored) ->
                key.dimension == dimension && stored.snapshot.expiresAt?.let { gameTime >= it } == true
            }
            .map { it.key }
            .toList()
        expired.forEach { key ->
            pendingGroups.remove(key)
            if (detachGroup(key) != null) {
                pruneConfiguredPipelines()
                revisionCounter.incrementAndGet()
            }
        }

    }

    /**
     * 查询某个位置在指定 Pipeline 下的绝对生效 tick。
     *
     * Pipeline 使用引用相等性匹配，调用方应传入 Registry 返回的已配置实例。
     *
     * @param dimension 查询维度 ID
     * @param position 查询方块坐标
     * @param pipeline 已解析的 Pipeline 实例
     * @param gameTime 当前维度的绝对游戏 tick
     * @return 匹配组中该位置的绝对生效 tick；没有匹配时返回 `null`
     */
    fun activationAt(
        dimension: ResourceLocation,
        position: BlockPos,
        pipeline: CooRenderPipeline<BlockState>,
        gameTime: Long
    ): Long? {
        return groupsAt(dimension, position, gameTime)
            .firstOrNull { it.pipeline === pipeline }
            ?.snapshot
            ?.activations
            ?.get(position)
    }

    /**
     * 取出并清除指定维度自上次调用以来发生可见变化的位置。
     *
     * @param dimension 要消费变更的维度 ID
     * @return 需要重新编译所在 section 的方块坐标集合
     */
    fun drainChangedPositions(dimension: ResourceLocation): Set<BlockPos> {
        val result = LinkedHashSet<BlockPos>()
        changedPositions.removeIf { key ->
            if (key.dimension != dimension) return@removeIf false
            result += key.position
            true
        }
        return result
    }

    /**
     * 获取客户端效果索引的本地版本号。
     *
     * @return 每次可见组状态改变后递增的版本号
     */
    fun revision(): Long = revisionCounter.get()

    /**
     * 在 Pipeline 模板注册后解析等待该 ID 的效果组。
     *
     * @param pipelineId 新注册的 Pipeline ID
     */
    @Synchronized
    fun onPipelineRegistered(pipelineId: ResourceLocation) {
        val template = CooTerrainEffectManager.pipeline(pipelineId) ?: return
        val ready = pendingGroups.entries.filter { it.value.pipelineId == pipelineId }
        ready.forEach { (key, snapshot) ->
            if (protocolRevisions[key] == snapshot.revision && pendingGroups.remove(key, snapshot)) {
                installResolved(key, snapshot, template)
            }
        }
    }

    /** 清空客户端效果组、反向索引、缓存和协议 revision。 */
    @Synchronized
    fun clear() {
        groups.clear()
        pendingGroups.clear()
        configuredPipelines.clear()
        positionIndex.clear()
        changedPositions.clear()
        protocolRevisions.clear()
        missingPipelines.clear()
        revisionCounter.incrementAndGet()
    }

    /** 记录组的最新协议 revision，并拒绝重复或乱序更新。 */
    private fun acceptRevision(key: GroupKey, revision: Long): Boolean {
        if (revision <= (protocolRevisions[key] ?: Long.MIN_VALUE)) return false
        protocolRevisions[key] = revision
        pruneProtocolTombstones()
        return true
    }

    /** 保留最近的删除 revision，限制长连接中一次性组 ID 造成的 tombstone 增长。 */
    private fun pruneProtocolTombstones() {
        val tombstones = protocolRevisions.entries
            .filter { (key, _) -> !groups.containsKey(key) && !pendingGroups.containsKey(key) }
        val overflow = tombstones.size - 4096
        if (overflow <= 0) return
        tombstones.sortedBy { it.value }
            .take(overflow)
            .forEach { entry -> protocolRevisions.remove(entry.key, entry.value) }
    }

    /** 从主索引移除组，同时解除全部位置反向索引。 */
    private fun detachGroup(key: GroupKey): StoredGroup? {
        val removed = groups.remove(key) ?: return null
        removed.snapshot.activations.keys.forEach { position ->
            val positionKey = PositionKey(key.dimension, position)
            positionIndex[positionKey]?.let { entries ->
                entries.remove(key)
                if (entries.isEmpty()) positionIndex.remove(positionKey, entries)
            }
            changedPositions += positionKey
        }
        return removed
    }

    /** 移除已没有效果组引用的 uniform 配置 Pipeline。 */
    private fun pruneConfiguredPipelines() {
        val used = groups.values.mapTo(HashSet()) { stored ->
            PipelineConfigKey(stored.snapshot.pipelineId, stored.snapshot.uniforms)
        }
        configuredPipelines.keys.removeIf { it !in used }
    }
}
