package cn.coostack.cooparticlesapi.renderer.terrain

import cn.coostack.cooparticlesapi.network.packet.api.CooServerPacketManager
import cn.coostack.cooparticlesapi.network.packet.server.PacketTerrainMappingS2C
import cn.coostack.cooparticlesapi.renderer.pipeline.CooRenderPipeline
import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.state.BlockState
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 管理程序化 terrain mapping 的服务端生命周期。
 *
 * 这里只保存区域参数和实例参数；网络协议不会发送离散位置，客户端也不会建立位置反向索引。
 */
object CooTerrainMappingManager {
    private val mappings = ConcurrentHashMap<ResourceLocation, CooTerrainMapping>()
    private val instances = ConcurrentHashMap<MappingKey, ManagedMapping>()
    private val sequenceCounter = AtomicLong()
    private val revisionCounter = AtomicLong()
    private val lock = Any()

    /** 注册两端共享的 mapping 模板。 */
    fun register(mapping: CooTerrainMapping): CooTerrainMapping {
        require(mappings.putIfAbsent(mapping.id, mapping) == null) {
            "Terrain mapping is already registered: ${mapping.id}"
        }
        return mapping
    }

    /** 通过 Pipeline 和区域类型注册 mapping 模板。 */
    fun register(
        id: ResourceLocation,
        pipeline: CooRenderPipeline<BlockState>,
        regionType: CooTerrainMappingRegionType,
        defaults: CooTerrainMappingDefaults = CooTerrainMappingDefaults()
    ): CooTerrainMapping = register(CooTerrainMapping(id, pipeline, regionType, defaults))

    /** 创建实例并同步区域 tagged union；调用方不需要也不允许枚举方块。 */
    fun create(
        level: ServerLevel,
        mappingId: ResourceLocation,
        instanceId: ResourceLocation,
        region: CooTerrainMappingRegion,
        block: CooTerrainMappingInstanceBuilder.() -> Unit = {}
    ): CooTerrainMappingInstance = createInternal(level, mappingId, instanceId, region, null, block)

    /** 创建只同步给指定玩家的实例；后续生命周期更新也只发送给该玩家。 */
    fun createTo(
        player: ServerPlayer,
        mappingId: ResourceLocation,
        instanceId: ResourceLocation,
        region: CooTerrainMappingRegion,
        block: CooTerrainMappingInstanceBuilder.() -> Unit = {}
    ): CooTerrainMappingInstance = createInternal(
        player.level() as? ServerLevel ?: error("Terrain mapping target must be a server player"),
        mappingId,
        instanceId,
        region,
        setOf(player.uuid),
        block
    )

    private fun createInternal(
        level: ServerLevel,
        mappingId: ResourceLocation,
        instanceId: ResourceLocation,
        region: CooTerrainMappingRegion,
        recipients: Set<UUID>?,
        block: CooTerrainMappingInstanceBuilder.() -> Unit
    ): CooTerrainMappingInstance {
        val mapping = requireMapping(mappingId)
        require(region.type == mapping.regionType) {
            "Terrain mapping $mappingId does not accept region type ${region.type}"
        }
        val key = MappingKey(level.dimension().location(), instanceId)
        val options = CooTerrainMappingInstanceBuilder(mapping.defaults).apply(block).build()
        synchronized(lock) {
            require(instances[key] == null) { "Terrain mapping instance already exists: $instanceId" }
            val instance = CooTerrainMappingInstance(
                instanceId,
                mappingId,
                key.dimension,
                region,
                options.uniforms,
                options.priority,
                options.composition,
                level.gameTime,
                options.durationTicks?.let(level.gameTime::plus),
                sequenceCounter.incrementAndGet(),
                revisionCounter.incrementAndGet()
            )
            instances[key] = ManagedMapping(instance, mapping, recipients)
            send(level, PacketTerrainMappingS2C.replace(instance, mapping.pipeline.id), recipients)
            return instance
        }
    }

    /** 暂停实例而不删除快照；暂停期间客户端不再将其加入绘制计划。 */
    fun pause(level: ServerLevel, instanceId: ResourceLocation): Boolean {
        val key = MappingKey(level.dimension().location(), instanceId)
        synchronized(lock) {
            val managed = instances[key] ?: return false
            if (managed.instance.isPaused()) return false
            val updated = managed.instance.copy(
                pausedAt = level.gameTime,
                revision = revisionCounter.incrementAndGet()
            )
            instances[key] = managed.copy(instance = updated)
            send(level, PacketTerrainMappingS2C.replace(updated, managed.mapping.pipeline.id), managed.recipients)
            return true
        }
    }

    /** 恢复实例并平移生命周期，保证暂停期间不消耗持续时间。 */
    fun resume(level: ServerLevel, instanceId: ResourceLocation): Boolean {
        val key = MappingKey(level.dimension().location(), instanceId)
        synchronized(lock) {
            val managed = instances[key] ?: return false
            val pausedAt = managed.instance.pausedAt ?: return false
            val pausedTicks = (level.gameTime - pausedAt).coerceAtLeast(0L)
            val updated = managed.instance.copy(
                startedAt = managed.instance.startedAt + pausedTicks,
                expiresAt = managed.instance.expiresAt?.plus(pausedTicks),
                pausedAt = null,
                revision = revisionCounter.incrementAndGet()
            )
            instances[key] = managed.copy(instance = updated)
            send(level, PacketTerrainMappingS2C.replace(updated, managed.mapping.pipeline.id), managed.recipients)
            return true
        }
    }

    /** 替换区域参数并发送完整 Mapping 快照；不存在位置增量，也不会重建 section。 */
    fun updateRegion(
        level: ServerLevel,
        instanceId: ResourceLocation,
        region: CooTerrainMappingRegion
    ): Boolean {
        val key = MappingKey(level.dimension().location(), instanceId)
        synchronized(lock) {
            val managed = instances[key] ?: return false
            require(region.type == managed.mapping.regionType) {
                "Terrain mapping $instanceId does not accept region type ${region.type}"
            }
            val updated = managed.instance.copy(region = region, revision = revisionCounter.incrementAndGet())
            instances[key] = managed.copy(instance = updated)
            send(level, PacketTerrainMappingS2C.replace(updated, managed.mapping.pipeline.id), managed.recipients)
            return true
        }
    }

    /** 替换完整 Mapping 参数集合，不触碰区域或地形 section。 */
    fun updateUniforms(
        level: ServerLevel,
        instanceId: ResourceLocation,
        uniforms: Map<String, CooUniformValue>
    ): Boolean {
        require(uniforms.keys.all(String::isNotBlank)) { "Terrain mapping uniform name must not be blank" }
        val key = MappingKey(level.dimension().location(), instanceId)
        synchronized(lock) {
            val managed = instances[key] ?: return false
            val updated = managed.instance.copy(uniforms = uniforms.toMap(), revision = revisionCounter.incrementAndGet())
            instances[key] = managed.copy(instance = updated)
            send(level, PacketTerrainMappingS2C.updateUniforms(updated), managed.recipients)
            return true
        }
    }

    /** 删除实例并通知客户端。 */
    fun remove(level: ServerLevel, instanceId: ResourceLocation): Boolean {
        val key = MappingKey(level.dimension().location(), instanceId)
        synchronized(lock) {
            val removed = instances.remove(key) ?: return false
            send(
                level,
                PacketTerrainMappingS2C.remove(key.dimension, instanceId, revisionCounter.incrementAndGet()),
                removed.recipients
            )
            return removed.instance.instanceId == instanceId
        }
    }

    /** 返回当前维度按优先级和序号排序的 Mapping 快照。 */
    fun active(level: ServerLevel): List<CooTerrainMappingInstance> = instances.values
        .asSequence()
        .map(ManagedMapping::instance)
        .filter { it.dimension == level.dimension().location() }
        .filter { !it.isPaused() }
        .filter { it.expiresAt?.let { expiresAt -> level.gameTime < expiresAt } ?: true }
        .sortedWith(compareByDescending<CooTerrainMappingInstance> { it.priority }
            .thenByDescending { it.sequence }
            .thenBy { it.instanceId.toString() })
        .toList()

    /** 向登录或换维度的玩家补发当前维度仍有效的 Mapping 快照。 */
    fun syncTo(player: ServerPlayer) {
        val level = player.level()
        synchronized(lock) {
            instances.values.asSequence()
                .map { managed -> managed to managed.instance }
                .filter { (_, instance) -> instance.dimension == level.dimension().location() }
                .filter { (managed, instance) -> managed.recipients == null || player.uuid in managed.recipients }
                .filter { (_, instance) -> instance.expiresAt?.let { expiresAt -> level.gameTime < expiresAt } ?: true }
                .forEach { (_, instance) ->
                    val pipeline = requireMapping(instance.mappingId).pipeline
                    CooServerPacketManager.sendTo(player, PacketTerrainMappingS2C.replace(instance, pipeline.id))
                }
        }
    }

    /** 向指定玩家补发一个仍有效的 Mapping 实例。 */
    fun syncTo(player: ServerPlayer, instanceId: ResourceLocation): Boolean {
        val level = player.level()
        val key = MappingKey(level.dimension().location(), instanceId)
        synchronized(lock) {
            val managed = instances[key] ?: return false
            val instance = managed.instance
            if (instance.expiresAt?.let { level.gameTime >= it } == true) return false
            if (managed.recipients != null && player.uuid !in managed.recipients) return false
            val pipeline = requireMapping(instance.mappingId).pipeline
            return CooServerPacketManager.sendTo(player, PacketTerrainMappingS2C.replace(instance, pipeline.id))
        }
    }
    /** 清理已过期或所属维度已卸载的实例，并通知仍在线客户端。 */
    fun tick(server: MinecraftServer) {
        val levels = server.allLevels.associateBy { it.dimension().location() }
        synchronized(lock) {
            instances.entries.removeIf { (key, managed) ->
                val level = levels[key.dimension]
                val expired = level == null || (!managed.instance.isPaused() && managed.instance.expiresAt?.let { level.gameTime >= it } == true)
                if (expired && level != null) {
                    send(
                        level,
                        PacketTerrainMappingS2C.remove(key.dimension, key.id, revisionCounter.incrementAndGet()),
                        managed.recipients
                    )
                }
                expired
            }
        }
    }


    /** 清空服务端运行态，保留模板注册。 */
    internal fun clearServerInstances() {
        synchronized(lock) { instances.clear() }
    }

    internal fun pipeline(id: ResourceLocation): CooRenderPipeline<BlockState>? = mappings[id]?.pipeline

    private fun requireMapping(id: ResourceLocation): CooTerrainMapping = requireNotNull(mappings[id]) {
        "Terrain mapping is not registered: $id"
    }

    private fun send(level: ServerLevel, packet: PacketTerrainMappingS2C, recipients: Set<UUID>?) {
        if (recipients == null) {
            CooServerPacketManager.sendWorlds(level, packet)
            return
        }
        level.players().filter { it.uuid in recipients }.forEach { player ->
            CooServerPacketManager.sendTo(player, packet)
        }
    }

    private data class MappingKey(val dimension: ResourceLocation, val id: ResourceLocation)

    private data class ManagedMapping(
        val instance: CooTerrainMappingInstance,
        val mapping: CooTerrainMapping,
        val recipients: Set<UUID>?
    )
}
