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

internal data class CooResolvedTerrainEffectGroup(
    val snapshot: CooTerrainEffectGroupSnapshot,
    val pipeline: CooRenderPipeline<BlockState>
)

/** 客户端保存的通用方块效果组索引，不引用 Minecraft 客户端或 OpenGL 类型。 */
internal object CooTerrainEffectRegistry {
    private data class GroupKey(
        val dimension: ResourceLocation,
        val id: ResourceLocation
    )

    private data class PositionKey(
        val dimension: ResourceLocation,
        val position: BlockPos
    )

    private data class StoredGroup(
        val snapshot: CooTerrainEffectGroupSnapshot,
        val pipeline: CooRenderPipeline<BlockState>
    )

    private data class PipelineConfigKey(
        val pipelineId: ResourceLocation,
        val uniforms: Map<String, CooUniformValue>
    )

    private val groups = ConcurrentHashMap<GroupKey, StoredGroup>()
    private val pendingGroups = ConcurrentHashMap<GroupKey, CooTerrainEffectGroupSnapshot>()
    private val configuredPipelines = ConcurrentHashMap<PipelineConfigKey, CooRenderPipeline<BlockState>>()
    private val positionIndex = ConcurrentHashMap<PositionKey, CopyOnWriteArrayList<GroupKey>>()
    private val changedPositions = ConcurrentHashMap.newKeySet<PositionKey>()
    private val sequenceCounter = AtomicLong()
    private val revisionCounter = AtomicLong()
    private val missingPipelines = ConcurrentHashMap.newKeySet<ResourceLocation>()

    fun install(snapshot: CooTerrainEffectGroupSnapshot) {
        val template = CooTerrainEffectManager.pipeline(snapshot.pipelineId)
        if (template == null) {
            pendingGroups[GroupKey(snapshot.dimension, snapshot.id)] = snapshot
            if (missingPipelines.add(snapshot.pipelineId)) {
                CooParticlesConstants.logger.error(
                    "Terrain effect group {} references unregistered pipeline {}; keeping vanilla terrain",
                    snapshot.id,
                    snapshot.pipelineId
                )
            }
            return
        }
        val key = GroupKey(snapshot.dimension, snapshot.id)
        pendingGroups.remove(key)
        val stored = StoredGroup(
            snapshot.copy(sequence = sequenceCounter.incrementAndGet()),
            configuredPipelines.computeIfAbsent(PipelineConfigKey(snapshot.pipelineId, snapshot.uniforms)) {
                template.uniformValues(snapshot.uniforms)
            }
        )
        val previous = groups.put(key, stored)
        previous?.snapshot?.activations?.keys?.forEach { position ->
            positionIndex[PositionKey(snapshot.dimension, position)]?.remove(key)
            changedPositions += PositionKey(snapshot.dimension, position)
        }
        stored.snapshot.activations.keys.forEach { position ->
            positionIndex.computeIfAbsent(PositionKey(snapshot.dimension, position)) {
                CopyOnWriteArrayList()
            }.addIfAbsent(key)
            changedPositions += PositionKey(snapshot.dimension, position)
        }
        revisionCounter.incrementAndGet()
    }

    fun append(
        dimension: ResourceLocation,
        groupId: ResourceLocation,
        additions: Map<BlockPos, Long>
    ) {
        if (additions.isEmpty()) return
        val key = GroupKey(dimension, groupId)
        while (true) {
            val current = groups[key]
            if (current == null) {
                while (true) {
                    val pending = pendingGroups[key] ?: return
                    val updated = pending.copy(activations = pending.activations + additions)
                    if (pendingGroups.replace(key, pending, updated)) return
                }
            }
            val added = additions.filterKeys { it !in current.snapshot.activations }
            if (added.isEmpty()) return
            val updated = current.copy(
                snapshot = current.snapshot.copy(
                    activations = current.snapshot.activations + added
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

    fun remove(dimension: ResourceLocation, groupId: ResourceLocation) {
        val key = GroupKey(dimension, groupId)
        pendingGroups.remove(key)
        val removed = groups.remove(key) ?: return
        removed.snapshot.activations.keys.forEach { position ->
            val positionKey = PositionKey(dimension, position)
            positionIndex[positionKey]?.let { entries ->
                entries.remove(key)
                if (entries.isEmpty()) positionIndex.remove(positionKey, entries)
            }
            changedPositions += positionKey
        }
        revisionCounter.incrementAndGet()
    }

    fun removePositions(
        dimension: ResourceLocation,
        groupId: ResourceLocation,
        positions: Set<BlockPos>
    ) {
        if (positions.isEmpty()) return
        val key = GroupKey(dimension, groupId)
        while (true) {
            val current = groups[key]
            if (current == null) {
                while (true) {
                    val pending = pendingGroups[key] ?: return
                    val retained = pending.activations - positions
                    if (retained.size == pending.activations.size) return
                    if (pendingGroups.replace(key, pending, pending.copy(activations = retained))) return
                }
            }
            val removed = current.snapshot.activations.keys.intersect(positions)
            if (removed.isEmpty()) return
            val updated = current.copy(
                snapshot = current.snapshot.copy(activations = current.snapshot.activations - removed)
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

    fun updateUniforms(
        dimension: ResourceLocation,
        groupId: ResourceLocation,
        uniforms: Map<String, CooUniformValue>
    ) {
        val key = GroupKey(dimension, groupId)
        while (true) {
            val current = groups[key]
            if (current == null) {
                while (true) {
                    val pending = pendingGroups[key] ?: return
                    if (pending.uniforms == uniforms) return
                    if (pendingGroups.replace(key, pending, pending.copy(uniforms = uniforms))) return
                }
            }
            if (current.snapshot.uniforms == uniforms) return
            val snapshot = current.snapshot.copy(uniforms = uniforms)
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
                revisionCounter.incrementAndGet()
                return
            }
        }
    }

    fun groupAt(
        dimension: ResourceLocation,
        position: BlockPos,
        gameTime: Long
    ): CooResolvedTerrainEffectGroup? = groupsAt(dimension, position, gameTime).firstOrNull()

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

    /** 到期清理和动态组的本地 section 刷新不需要服务器重复发包。 */
    fun advance(dimension: ResourceLocation, gameTime: Long) {
        val expired = groups.entries.asSequence()
            .filter { (key, stored) ->
                key.dimension == dimension && stored.snapshot.expiresAt?.let { gameTime >= it } == true
            }
            .map { it.key }
            .toList()
        expired.forEach { remove(it.dimension, it.id) }

    }

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

    fun drainChangedPositions(dimension: ResourceLocation): Set<BlockPos> {
        val result = LinkedHashSet<BlockPos>()
        changedPositions.removeIf { key ->
            if (key.dimension != dimension) return@removeIf false
            result += key.position
            true
        }
        return result
    }

    fun revision(): Long = revisionCounter.get()

    fun onPipelineRegistered(pipelineId: ResourceLocation) {
        val ready = pendingGroups.values.filter { it.pipelineId == pipelineId }
        ready.forEach(::install)
    }

    fun clear() {
        groups.clear()
        pendingGroups.clear()
        configuredPipelines.clear()
        positionIndex.clear()
        changedPositions.clear()
        missingPipelines.clear()
        revisionCounter.incrementAndGet()
    }
}
