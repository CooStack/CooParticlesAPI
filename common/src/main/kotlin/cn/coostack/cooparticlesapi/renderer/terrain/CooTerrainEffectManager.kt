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
import java.util.concurrent.atomic.AtomicLong

/**
 * 管理服务端批量方块效果及其客户端同步。
 *
 * Manager 按“维度 + 组 ID”保存当前效果组，并为每次替换或增量更新分配递增 revision。
 * 更新只发送给对应维度的玩家；新玩家加入或切换维度时由 [syncTo] 补发仍然有效的组。
 * Pipeline 必须在两端以同一 ID 构建。通过
 * [cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelines.block] 创建的 Pipeline 会自动注册，
 * 普通调用方不需要额外调用 [register]。
 *
 * 基本调用：
 * ```kotlin
 * CooTerrainEffectManager.apply(level, effectId, pipeline, affectedBlocks) {
 *     uniform("Strength", 0.8F)
 *     duration(40L)
 * }
 * CooTerrainEffectManager.append(level, effectId, nextWave, delayTicks = 5L)
 * CooTerrainEffectManager.remove(level, effectId)
 * ```
 *
 * 所有修改组状态的方法都应在逻辑服务端调用。
 */
object CooTerrainEffectManager {
    /** 按资源 ID 保存两端解析动态效果组所需的不可变方块 Pipeline 模板。 */
    private val pipelines = ConcurrentHashMap<ResourceLocation, CooRenderPipeline<BlockState>>()

    /** 按维度和组 ID 保存服务端当前有效的效果组快照。 */
    private val serverGroups = ConcurrentHashMap<ServerGroupKey, CooTerrainEffectGroupSnapshot>()

    /** 保存在线玩家最后同步的维度，用于检测传送后的补发需求。 */
    private val playerDimensions = ConcurrentHashMap<UUID, ResourceLocation>()

    /** 保护效果组复合更新和对应网络发送顺序的互斥锁。 */
    private val serverGroupsLock = Any()

    /** 为新建组分配重叠绘制顺序的进程内单调计数器。 */
    private val sequenceCounter = AtomicLong()

    /** 为全部组更新分配防止客户端乱序应用的单调协议 revision。 */
    private val protocolRevisionCounter = AtomicLong()

    /**
     * 注册客户端解析效果组时使用的不可变 Pipeline 模板。
     *
     * 相同 ID 再次注册时会替换旧模板，并尝试解析等待该 ID 的客户端效果组。
     * 通过 `CooPipelines.block(...)` 创建 Pipeline 时会自动完成此注册。
     *
     * @param pipeline 两端使用同一 ID 构建的方块 Pipeline 模板
     * @return 原 [pipeline]，便于在创建表达式中直接保存返回值
     */
    fun register(pipeline: CooRenderPipeline<BlockState>): CooRenderPipeline<BlockState> {
        pipelines[pipeline.id] = pipeline
        CooTerrainEffectRegistry.onPipelineRegistered(pipeline.id)
        return pipeline
    }

    /**
     * 在指定维度应用一个完整效果组，并向该维度的玩家发送替换更新。
     *
     * 同一维度内已有相同组 ID 时会被整体替换；其他维度的同名组不受影响。
     * 示例：`CooTerrainEffectManager.apply(level, group)`。
     *
     * @param level 组所属的服务端维度
     * @param group 包含组 ID、Pipeline 及位置配置的效果组
     */
    fun apply(level: ServerLevel, group: CooTerrainEffectGroup) {
        synchronized(serverGroupsLock) {
            val startedAt = level.gameTime
            val definition = group.definition
            val snapshot = CooTerrainEffectGroupSnapshot(
                dimension = level.dimension().location(),
                id = group.id,
                pipelineId = group.pipeline.id,
                startedAt = startedAt,
                expiresAt = definition.durationTicks?.let(startedAt::plus),
                activations = definition.activationOffsets.mapValues { (_, offset) -> startedAt + offset },
                uniforms = definition.uniforms,
                sequence = sequenceCounter.incrementAndGet(),
                revision = protocolRevisionCounter.incrementAndGet(),
                priority = definition.priority,
                composition = definition.composition
            )
            serverGroups[ServerGroupKey(snapshot.dimension, snapshot.id)] = snapshot
            CooServerPacketManager.sendWorlds(level, PacketTerrainEffectGroupS2C.replace(snapshot))
        }
    }

    /**
     * 从一批位置创建效果组并立即应用到指定维度。
     *
     * [positions] 会先加入构建器，再执行 [block]，因此构建块可以覆盖重复位置的延迟。
     * 单个位置同样走批量协议。
     *
     * 示例：
     * ```kotlin
     * CooTerrainEffectManager.apply(level, effectId, pipeline, positions) {
     *     duration(60L)
     * }
     * ```
     *
     * @param level 组所属的服务端维度
     * @param groupId 组 ID；同一维度内重复使用会替换旧组
     * @param pipeline 渲染这些位置的方块 Pipeline
     * @param positions 初始方块坐标
     * @param block 追加延迟、uniform 或持续时间的配置块
     */
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
     * 将一批使用同一延迟的位置追加到已有组，不重复发送 Pipeline 和 uniform。
     *
     * 已存在于组中的位置保持原生效时间；空集合或全部重复时也视为成功。
     * 示例：`append(level, effectId, nextWave, delayTicks = 5L)`。
     *
     * @param level 组所属的服务端维度
     * @param groupId 要更新的组 ID
     * @param positions 要新增的方块坐标
     * @param delayTicks 相对本次追加时刻的生效延迟 tick，必须大于或等于 `0`
     * @return 找到组并完成更新时返回 `true`；组不存在时返回 `false`
     * @throws IllegalArgumentException 当 [delayTicks] 为负数时抛出
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

    /**
     * 将各自使用不同延迟的位置追加到已有组。
     *
     * 示例：`append(level, effectId, mapOf(origin to 0L, target to 10L))`。
     *
     * @param level 组所属的服务端维度
     * @param groupId 要更新的组 ID
     * @param positions 键为方块坐标，值为相对本次追加时刻的延迟 tick；延迟不可为负数
     * @return 找到组并完成更新时返回 `true`；组不存在时返回 `false`
     * @throws IllegalArgumentException 当任一延迟为负数时抛出
     */
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

    /**
     * 只移除组中的指定位置，不重发 Pipeline、uniform 或其余位置。
     *
     * 请求为空或指定位置均不在组中时仍返回 `true`。
     *
     * @param level 组所属的服务端维度
     * @param groupId 要更新的组 ID
     * @param positions 要从组中移除的方块坐标
     * @return 找到组并处理请求时返回 `true`；组不存在时返回 `false`
     */
    fun removePositions(
        level: ServerLevel,
        groupId: ResourceLocation,
        positions: Iterable<BlockPos>
    ): Boolean {
        val key = ServerGroupKey(level.dimension().location(), groupId)
        val requested = positions.mapTo(LinkedHashSet()) { it.immutable() }
        if (requested.isEmpty()) return true
        return synchronized(serverGroupsLock) {
            val current = serverGroups[key] ?: return false
            val removed = current.activations.keys.intersect(requested)
            if (removed.isEmpty()) return true
            val updated = current.copy(
                activations = current.activations - removed,
                revision = protocolRevisionCounter.incrementAndGet()
            )
            serverGroups[key] = updated
            CooServerPacketManager.sendWorlds(
                level,
                PacketTerrainEffectGroupS2C.removePositions(
                    updated.dimension,
                    updated.id,
                    updated.revision,
                    removed
                )
            )
            true
        }
    }

    /**
     * 替换整组共享的 uniform，不重发位置表。
     *
     * 传入的映射是完整的新 uniform 集合，不是增量补丁。示例：
     * `updateUniforms(level, effectId, mapOf("Strength" to CooUniformValue.FloatValue(1F)))`。
     *
     * @param level 组所属的服务端维度
     * @param groupId 要更新的组 ID
     * @param uniforms 键为 shader uniform 名称，值为新的 uniform 数据；名称不可为空白
     * @return 找到组并完成更新时返回 `true`；组不存在时返回 `false`
     * @throws IllegalArgumentException 当任一 uniform 名称为空白时抛出
     */
    fun updateUniforms(
        level: ServerLevel,
        groupId: ResourceLocation,
        uniforms: Map<String, CooUniformValue>
    ): Boolean {
        require(uniforms.keys.all(String::isNotBlank)) { "Terrain effect uniform name must not be blank" }
        val key = ServerGroupKey(level.dimension().location(), groupId)
        return synchronized(serverGroupsLock) {
            val current = serverGroups[key] ?: return false
            if (current.uniforms == uniforms) return true
            val updated = current.copy(
                uniforms = uniforms.toMap(),
                revision = protocolRevisionCounter.incrementAndGet()
            )
            serverGroups[key] = updated
            CooServerPacketManager.sendWorlds(
                level,
                PacketTerrainEffectGroupS2C.updateUniforms(
                    updated.dimension,
                    updated.id,
                    updated.revision,
                    updated.uniforms
                )
            )
            true
        }
    }

    /**
     * 原子替换组的排序和合成选项，并发送一次 options 更新。
     *
     * @return 找到组并完成更新时返回 `true`；组不存在时返回 `false`
     */
    fun updateOrdering(
        level: ServerLevel,
        groupId: ResourceLocation,
        priority: Int,
        composition: CooTerrainEffectComposition
    ): Boolean {
        val key = ServerGroupKey(level.dimension().location(), groupId)
        return synchronized(serverGroupsLock) {
            val current = serverGroups[key] ?: return false
            if (current.priority == priority && current.composition == composition) return true
            val updated = current.copy(
                priority = priority,
                composition = composition,
                revision = protocolRevisionCounter.incrementAndGet()
            )
            serverGroups[key] = updated
            CooServerPacketManager.sendWorlds(
                level,
                PacketTerrainEffectGroupS2C.updateOrdering(
                    updated.dimension,
                    updated.id,
                    updated.revision,
                    priority,
                    composition
                )
            )
            true
        }
    }

    /**
     * 移除整组效果，并通知客户端局部重建受影响的 section。
     *
     * @param groupId 要移除的组 ID
     * @return 找到并移除组时返回 `true`；组不存在时返回 `false`
     */
    fun remove(level: ServerLevel, groupId: ResourceLocation): Boolean {
        val key = ServerGroupKey(level.dimension().location(), groupId)
        return synchronized(serverGroupsLock) {
            val removed = serverGroups.remove(key) ?: return false
            val revision = protocolRevisionCounter.incrementAndGet()
            CooServerPacketManager.sendWorlds(
                level,
                PacketTerrainEffectGroupS2C.remove(removed.dimension, removed.id, revision)
            )
            true
        }
    }

    /**
     * 向玩家补发其当前维度内仍有效的持久组和临时组。
     *
     * 该方法由玩家加入事件调用，也用于玩家切换维度后的状态补齐；普通效果代码无需手动调用。
     *
     * @param player 需要同步当前维度地形效果的服务端玩家
     */
    fun syncTo(player: ServerPlayer) {
        val level = player.level()
        val gameTime = level.gameTime
        playerDimensions[player.uuid] = level.dimension().location()
        synchronized(serverGroupsLock) {
            serverGroups.values.asSequence()
                .filter { it.dimension == level.dimension().location() }
                .filter { it.expiresAt == null || gameTime < it.expiresAt }
                .sortedWith(compareBy<CooTerrainEffectGroupSnapshot> { it.priority }
                    .thenBy { it.sequence }
                    .thenBy { it.id.toString() })
                .forEach { snapshot ->
                    CooServerPacketManager.sendTo(player, PacketTerrainEffectGroupS2C.replace(snapshot))
                }
        }
    }

    /**
     * 跟踪玩家维度变化并清理服务端已到期组。
     *
     * 客户端会按同步的绝对 tick 自行结束临时组，因此清理时无需再次发送移除包。
     *
     * @param server 当前逻辑服务器
     */
    internal fun tick(server: MinecraftServer) {
        val onlinePlayers = server.playerList.players
        onlinePlayers.forEach { player ->
            val dimension = player.level().dimension().location()
            val previous = playerDimensions.put(player.uuid, dimension)
            if (previous != null && previous != dimension) syncTo(player)
        }
        val onlineIds = onlinePlayers.mapTo(HashSet()) { it.uuid }
        playerDimensions.keys.removeIf { it !in onlineIds }

        synchronized(serverGroupsLock) {
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
    }

    /**
     * 按 ID 查询客户端解析效果组所需的 Pipeline 模板。
     *
     * @param id Pipeline ID
     * @return 已注册模板；未注册时返回 `null`
     */
    internal fun pipeline(id: ResourceLocation): CooRenderPipeline<BlockState>? = pipelines[id]

    /**
     * 在持有统一更新语义的路径中把绝对生效时间追加到现有组。
     *
     * @param level 组所属服务端维度
     * @param key 维度和组 ID 组成的服务端索引键
     * @param additions 键为方块坐标，值为绝对生效 tick
     * @return 找到组并处理更新时返回 `true`；组不存在时返回 `false`
     */
    private fun append(
        level: ServerLevel,
        key: ServerGroupKey,
        additions: Map<BlockPos, Long>
    ): Boolean {
        if (additions.isEmpty()) return true
        return synchronized(serverGroupsLock) {
            val current = serverGroups[key] ?: return false
            val added = additions.filterKeys { it !in current.activations }
            if (added.isEmpty()) return true
            val updated = current.copy(
                activations = current.activations + added,
                revision = protocolRevisionCounter.incrementAndGet()
            )
            serverGroups[key] = updated
            CooServerPacketManager.sendWorlds(
                level,
                PacketTerrainEffectGroupS2C.append(
                    updated.dimension,
                    updated.id,
                    updated.revision,
                    added
                )
            )
            true
        }
    }

    /** 返回服务端当前有效的 Terrain effect group 数量。 */
    fun serverGroupCount(): Int = serverGroups.size

    /**
     * 清空服务端效果组和玩家维度缓存。
     *
     * 由服务器停止生命周期调用；Pipeline 模板不随世界状态清空。
     */
    internal fun clearServerGroups() {
        synchronized(serverGroupsLock) {
            serverGroups.clear()
        }
        playerDimensions.clear()
    }
}
