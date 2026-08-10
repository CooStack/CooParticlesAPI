package cn.coostack.cooparticlesapi.renderer.terrain

import cn.coostack.cooparticlesapi.network.packet.api.CooServerPacketManager
import cn.coostack.cooparticlesapi.network.packet.server.PacketTerrainEffectGroupS2C
import cn.coostack.cooparticlesapi.renderer.pipeline.CooRenderPipeline
import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.state.BlockState
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 一组共享同一方块 Pipeline 和 uniform 的位置。
 *
 * 默认持续到显式移除；调用 [CooTerrainEffectGroupBuilder.duration] 后会在指定 tick 数后自动结束。
 */
class CooTerrainEffectGroup(
    val id: ResourceLocation,
    val pipeline: CooRenderPipeline<BlockState>,
    block: CooTerrainEffectGroupBuilder.() -> Unit
) {
    internal val definition = CooTerrainEffectGroupBuilder().apply(block).build()
}

/** 批量方块效果组的 Kotlin 构建器。 */
class CooTerrainEffectGroupBuilder internal constructor() {
    private val activationOffsets = LinkedHashMap<BlockPos, Long>()
    private val uniforms = LinkedHashMap<String, CooUniformValue>()
    private var durationTicks: Long? = null

    /** 加入一个位置，可用延迟让该位置晚于组开始时间生效。 */
    @JvmOverloads
    fun position(position: BlockPos, delayTicks: Long = 0L) = apply {
        require(delayTicks >= 0L) { "Terrain effect position delay must not be negative" }
        activationOffsets[position.immutable()] = delayTicks
    }

    /** 批量加入使用同一生效延迟的位置。 */
    @JvmOverloads
    fun positions(positions: Iterable<BlockPos>, delayTicks: Long = 0L) = apply {
        positions.forEach { position(it, delayTicks) }
    }

    /** 批量加入各自使用不同生效延迟的位置。 */
    fun positions(positions: Map<BlockPos, Long>) = apply {
        positions.forEach(::position)
    }

    /** 为整组设置一个共享 uniform。 */
    fun uniform(name: String, value: Float) = uniform(name, CooUniformValue.FloatValue(value))

    /** 为整组设置一个共享 uniform。 */
    fun uniform(name: String, value: CooUniformValue) = apply {
        require(name.isNotBlank()) { "Terrain effect uniform name must not be blank" }
        uniforms[name] = value
    }

    /** 一次设置整组共享的多个 uniform。 */
    fun uniforms(values: Map<String, CooUniformValue>) = apply {
        values.forEach(::uniform)
    }

    /** 让整组在指定 tick 数后自动结束；不调用时为持久组。 */
    fun duration(ticks: Long) = apply {
        require(ticks > 0L) { "Terrain effect duration must be greater than zero" }
        durationTicks = ticks
    }

    internal fun build(): CooTerrainEffectGroupDefinition {
        return CooTerrainEffectGroupDefinition(
            activationOffsets = activationOffsets.toMap(),
            uniforms = uniforms.toMap(),
            durationTicks = durationTicks
        )
    }
}

internal data class CooTerrainEffectGroupDefinition(
    val activationOffsets: Map<BlockPos, Long>,
    val uniforms: Map<String, CooUniformValue>,
    val durationTicks: Long?
)

internal data class CooTerrainEffectGroupSnapshot(
    val dimension: ResourceLocation,
    val id: ResourceLocation,
    val pipelineId: ResourceLocation,
    val startedAt: Long,
    val expiresAt: Long?,
    val activations: Map<BlockPos, Long>,
    val uniforms: Map<String, CooUniformValue>,
    val sequence: Long = 0L
)

/**
 * 批量方块效果的注册、应用、增量更新和移除入口。
 *
 * Pipeline 必须在两端以同一 ID 构建。通过 [cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelines.block]
 * 创建的 Pipeline 会自动注册，因此普通调用方不需要额外注册步骤。
 */
object CooTerrainEffectManager {
    private data class ServerGroupKey(
        val dimension: ResourceLocation,
        val id: ResourceLocation
    )

    private val pipelines = ConcurrentHashMap<ResourceLocation, CooRenderPipeline<BlockState>>()
    private val serverGroups = ConcurrentHashMap<ServerGroupKey, CooTerrainEffectGroupSnapshot>()
    private val playerDimensions = ConcurrentHashMap<UUID, ResourceLocation>()

    /** 注册客户端解析效果组时使用的不可变 Pipeline 模板。 */
    fun register(pipeline: CooRenderPipeline<BlockState>): CooRenderPipeline<BlockState> {
        pipelines[pipeline.id] = pipeline
        CooTerrainEffectRegistry.onPipelineRegistered(pipeline.id)
        return pipeline
    }

    /** 把一组位置作为一个网络更新应用到当前维度。 */
    fun apply(level: ServerLevel, group: CooTerrainEffectGroup) {
        register(group.pipeline)
        val startedAt = level.gameTime
        val definition = group.definition
        val snapshot = CooTerrainEffectGroupSnapshot(
            dimension = level.dimension().location(),
            id = group.id,
            pipelineId = group.pipeline.id,
            startedAt = startedAt,
            expiresAt = definition.durationTicks?.let(startedAt::plus),
            activations = definition.activationOffsets.mapValues { (_, offset) -> startedAt + offset },
            uniforms = definition.uniforms
        )
        serverGroups[ServerGroupKey(snapshot.dimension, snapshot.id)] = snapshot
        CooServerPacketManager.sendWorlds(level, PacketTerrainEffectGroupS2C.replace(snapshot))
    }

    /** 直接创建并应用一批位置；单个位置也使用同一批量协议。 */
    fun apply(
        level: ServerLevel,
        groupId: ResourceLocation,
        pipeline: CooRenderPipeline<BlockState>,
        positions: Iterable<BlockPos>,
        block: CooTerrainEffectGroupBuilder.() -> Unit = {}
    ) {
        apply(
            level,
            CooTerrainEffectGroup(groupId, pipeline) {
                positions(positions)
                block()
            }
        )
    }

    /**
     * 只把新增位置追加到已有组。共享 Pipeline 和 uniform 不会重复发送。
     *
     * @return 找到并更新组时返回 `true`
     */
    @JvmOverloads
    fun append(
        level: ServerLevel,
        groupId: ResourceLocation,
        positions: Iterable<BlockPos>,
        delayTicks: Long = 0L
    ): Boolean {
        require(delayTicks >= 0L) { "Terrain effect position delay must not be negative" }
        val key = ServerGroupKey(level.dimension().location(), groupId)
        val additions = positions
            .map { it.immutable() }
            .distinct()
            .associateWith { level.gameTime + delayTicks }
        return append(level, key, additions)
    }


    /** 批量追加各自使用不同生效延迟的位置。 */
    fun append(
        level: ServerLevel,
        groupId: ResourceLocation,
        positions: Map<BlockPos, Long>
    ): Boolean {
        require(positions.values.all { it >= 0L }) { "Terrain effect position delay must not be negative" }
        val key = ServerGroupKey(level.dimension().location(), groupId)
        val additions = positions.entries.associate { (position, delay) ->
            position.immutable() to level.gameTime + delay
        }
        return append(level, key, additions)
    }

    /** 只移除组中的指定位置，不重发 Pipeline、uniform 或其余位置。 */
    fun removePositions(
        level: ServerLevel,
        groupId: ResourceLocation,
        positions: Iterable<BlockPos>
    ): Boolean {
        val key = ServerGroupKey(level.dimension().location(), groupId)
        val requested = positions.mapTo(LinkedHashSet()) { it.immutable() }
        if (requested.isEmpty()) return true
        while (true) {
            val current = serverGroups[key] ?: return false
            val removed = current.activations.keys.intersect(requested)
            if (removed.isEmpty()) return true
            val updated = current.copy(activations = current.activations - removed)
            if (serverGroups.replace(key, current, updated)) {
                CooServerPacketManager.sendWorlds(
                    level,
                    PacketTerrainEffectGroupS2C.removePositions(current.dimension, current.id, removed)
                )
                return true
            }
        }
    }

    /** 更新整组共享 uniform，不重发位置表。 */
    fun updateUniforms(
        level: ServerLevel,
        groupId: ResourceLocation,
        uniforms: Map<String, CooUniformValue>
    ): Boolean {
        require(uniforms.keys.all(String::isNotBlank)) { "Terrain effect uniform name must not be blank" }
        val key = ServerGroupKey(level.dimension().location(), groupId)
        while (true) {
            val current = serverGroups[key] ?: return false
            if (current.uniforms == uniforms) return true
            val updated = current.copy(uniforms = uniforms.toMap())
            if (serverGroups.replace(key, current, updated)) {
                CooServerPacketManager.sendWorlds(
                    level,
                    PacketTerrainEffectGroupS2C.updateUniforms(current.dimension, current.id, updated.uniforms)
                )
                return true
            }
        }
    }

    /** 移除整组效果并让客户端局部重建受影响 section。 */
    fun remove(level: ServerLevel, groupId: ResourceLocation): Boolean {
        val key = ServerGroupKey(level.dimension().location(), groupId)
        val removed = serverGroups.remove(key) ?: return false
        CooServerPacketManager.sendWorlds(
            level,
            PacketTerrainEffectGroupS2C.remove(removed.dimension, removed.id)
        )
        return true
    }

    /** 玩家进入服务器时补发其当前维度内仍有效的持久组和临时组。 */
    fun syncTo(player: ServerPlayer) {
        val level = player.level()
        val gameTime = level.gameTime
        playerDimensions[player.uuid] = level.dimension().location()
        serverGroups.values.asSequence()
            .filter { it.dimension == level.dimension().location() }
            .filter { it.expiresAt == null || gameTime < it.expiresAt }
            .forEach { snapshot ->
                CooServerPacketManager.sendTo(player, PacketTerrainEffectGroupS2C.replace(snapshot))
            }
    }

    /** 清理服务端已到期组；客户端也会按同一个绝对 tick 自行结束。 */
    internal fun tick(server: MinecraftServer) {
        val onlinePlayers = server.playerList.players
        onlinePlayers.forEach { player ->
            val dimension = player.level().dimension().location()
            val previous = playerDimensions.put(player.uuid, dimension)
            if (previous != null && previous != dimension) syncTo(player)
        }
        val onlineIds = onlinePlayers.mapTo(HashSet()) { it.uuid }
        playerDimensions.keys.removeIf { it !in onlineIds }

        serverGroups.entries.removeIf { (_, snapshot) ->
            val level = server.getLevel(
                ResourceKey.create(
                    Registries.DIMENSION,
                    snapshot.dimension
                )
            ) ?: return@removeIf true
            snapshot.expiresAt?.let { level.gameTime >= it } == true
        }
    }

    internal fun pipeline(id: ResourceLocation): CooRenderPipeline<BlockState>? = pipelines[id]

    private fun append(
        level: ServerLevel,
        key: ServerGroupKey,
        additions: Map<BlockPos, Long>
    ): Boolean {
        if (additions.isEmpty()) return true
        while (true) {
            val current = serverGroups[key] ?: return false
            val added = additions.filterKeys { it !in current.activations }
            if (added.isEmpty()) return true
            val updated = current.copy(activations = current.activations + added)
            if (serverGroups.replace(key, current, updated)) {
                CooServerPacketManager.sendWorlds(
                    level,
                    PacketTerrainEffectGroupS2C.append(current.dimension, current.id, added)
                )
                return true
            }
        }
    }

    internal fun clearServerGroups() {
        serverGroups.clear()
        playerDimensions.clear()
    }
}
