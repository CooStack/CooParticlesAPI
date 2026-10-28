package cn.coostack.cooparticlesapi.renderer.terrain

import cn.coostack.cooparticlesapi.network.packet.api.CooServerPacketManager
import cn.coostack.cooparticlesapi.network.packet.server.PacketTerrainMappingS2C
import cn.coostack.cooparticlesapi.renderer.pipeline.CooRenderPipeline
import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import net.minecraft.resources.Identifier
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
 * 模板由 [register] 在两端初始化时注册，服务端调用 [create] 创建实例，服务器 tick 驱动过期回收。
 * 示例：`CooTerrainMappingManager.create(level, mappingId, instanceId, region)`。
 */
object CooTerrainMappingManager {
    /** 两端共享的只读模板，键为 mapping 注册标识。 */
    private val mappings = ConcurrentHashMap<Identifier, CooTerrainMapping>()
    /** 服务端实例与接收者状态，键由维度和实例标识组成。 */
    private val instances = ConcurrentHashMap<MappingKey, ManagedMapping>()
    /** 控制同优先级实例绘制顺序的单调序号。 */
    private val sequenceCounter = AtomicLong()
    /** 防止客户端乱序应用快照的单调协议版本。 */
    private val revisionCounter = AtomicLong()
    /** 串行化实例复合更新与相应的网络发送。 */
    private val lock = Any()

    /**
     * 注册两端共享的 mapping 模板，不向服务器或客户端传输 shader 程序。
     * @param mapping 尚未注册的不可变模板
     * @return 原模板实例
     * @throws IllegalArgumentException 模板标识已存在
     */
    fun register(mapping: CooTerrainMapping): CooTerrainMapping {
        require(mappings.putIfAbsent(mapping.id, mapping) == null) {
            "Terrain mapping is already registered: ${mapping.id}"
        }
        return mapping
    }

    /**
     * 通过 Pipeline 和区域类型注册 mapping 模板。
     * @param id 两端一致且尚未使用的模板标识
     * @param pipeline 已构建的方块效果管线
     * @param regionType 该模板接受的区域类型
     * @param defaults 新实例默认采用的参数与生命周期选项
     * @return 已登记的不可变模板
     */
    fun register(
        id: Identifier,
        pipeline: CooRenderPipeline<BlockState>,
        regionType: CooTerrainMappingRegionType,
        defaults: CooTerrainMappingDefaults = CooTerrainMappingDefaults()
    ): CooTerrainMapping = register(CooTerrainMapping(id, pipeline, regionType, defaults))

    /**
     * 在服务端创建实例并同步区域参数，不需要枚举方块。
     * 示例：`create(level, mappingId, instanceId, region) { duration(40L) }`。
     * @param level 实例所属维度
     * @param mappingId 已注册且接受该区域类型的模板标识
     * @param instanceId 当前维度内唯一的实例标识
     * @param region 使用世界坐标的区域参数
     * @param block 相对模板默认值覆盖的实例选项
     * @return 新实例的不可变快照
     * @throws IllegalArgumentException 模板缺失、区域类型不匹配或实例重复
     */
    fun create(
        level: ServerLevel,
        mappingId: Identifier,
        instanceId: Identifier,
        region: CooTerrainMappingRegion,
        block: CooTerrainMappingInstanceBuilder.() -> Unit = {}
    ): CooTerrainMappingInstance = createInternal(level, mappingId, instanceId, region, null, block)

    /**
     * 在目标玩家所在服务端维度创建仅发给该玩家的实例，后续更新保留相同接收范围。
     * 示例：`createTo(player, mappingId, instanceId, region)`。
     * @param player 唯一接收者
     * @param mappingId 已注册模板标识
     * @param instanceId 该维度内唯一的实例标识
     * @param region 使用世界坐标的区域参数
     * @param block 覆盖模板默认值的实例选项
     * @return 新实例的不可变快照
     */
    fun createTo(
        player: ServerPlayer,
        mappingId: Identifier,
        instanceId: Identifier,
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
        mappingId: Identifier,
        instanceId: Identifier,
        region: CooTerrainMappingRegion,
        recipients: Set<UUID>?,
        block: CooTerrainMappingInstanceBuilder.() -> Unit
    ): CooTerrainMappingInstance {
        val mapping = requireMapping(mappingId)
        require(region.type == mapping.regionType) {
            "Terrain mapping $mappingId does not accept region type ${region.type}"
        }
        val key = MappingKey(level.dimension().identifier(), instanceId)
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

    /**
     * 暂停实例而不删除快照，暂停期间客户端不将其加入绘制计划。
     * 示例：`pause(level, instanceId)`。
     * @param level 实例所属服务端维度
     * @param instanceId 该维度中的实例标识
     * @return 成功从运行变为暂停时返回 true，缺失或已经暂停时返回 false
     */
    fun pause(level: ServerLevel, instanceId: Identifier): Boolean {
        val key = MappingKey(level.dimension().identifier(), instanceId)
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

    /**
     * 恢复实例并平移生命周期，暂停期间不消耗持续时间。
     * 示例：`resume(level, instanceId)`。
     * @param level 实例所属服务端维度
     * @param instanceId 该维度中的实例标识
     * @return 成功恢复时返回 true，缺失或并未暂停时返回 false
     */
    fun resume(level: ServerLevel, instanceId: Identifier): Boolean {
        val key = MappingKey(level.dimension().identifier(), instanceId)
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

    /**
     * 替换区域并发送完整快照，不发送方块位置增量或要求区段重建。
     * 示例：`updateRegion(level, instanceId, nextRegion)`。
     * @param level 实例所属服务端维度
     * @param instanceId 该维度中的实例标识
     * @param region 与模板区域类型匹配的新世界区域
     * @return 已更新时返回 true，实例不存在时返回 false
     * @throws IllegalArgumentException 区域类型与模板不符
     */
    fun updateRegion(
        level: ServerLevel,
        instanceId: Identifier,
        region: CooTerrainMappingRegion
    ): Boolean {
        val key = MappingKey(level.dimension().identifier(), instanceId)
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

    /**
     * 替换完整 uniform 集合而非追加补丁，不触碰区域或地形区段。
     * 示例：`updateUniforms(level, instanceId, mapOf("Strength" to CooUniformValue.FloatValue(1F)))`。
     * @param level 实例所属服务端维度
     * @param instanceId 该维度中的实例标识
     * @param uniforms 键为非空白 shader uniform 名称，值为完整的新参数数据
     * @return 已更新时返回 true，实例不存在时返回 false
     */
    fun updateUniforms(
        level: ServerLevel,
        instanceId: Identifier,
        uniforms: Map<String, CooUniformValue>
    ): Boolean {
        require(uniforms.keys.all(String::isNotBlank)) { "Terrain mapping uniform name must not be blank" }
        val key = MappingKey(level.dimension().identifier(), instanceId)
        synchronized(lock) {
            val managed = instances[key] ?: return false
            val updated = managed.instance.copy(uniforms = uniforms.toMap(), revision = revisionCounter.incrementAndGet())
            instances[key] = managed.copy(instance = updated)
            send(level, PacketTerrainMappingS2C.updateUniforms(updated), managed.recipients)
            return true
        }
    }

    /**
     * 删除实例并发送带新版本号的移除通知。
     * 示例：`remove(level, instanceId)`。
     * @param level 实例所属服务端维度
     * @param instanceId 该维度中的实例标识
     * @return 找到并移除实例时返回 true
     */
    fun remove(level: ServerLevel, instanceId: Identifier): Boolean {
        val key = MappingKey(level.dimension().identifier(), instanceId)
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

    /**
     * 返回当前维度未暂停且尚未到期的快照，优先级和序号均从大到小排序。
     * 示例：`val snapshots = active(level)`。
     * @param level 要查询的服务端维度
     * @return 独立列表，不允许通过结果修改管理器内部状态
     */
    fun active(level: ServerLevel): List<CooTerrainMappingInstance> = instances.values
        .asSequence()
        .map(ManagedMapping::instance)
        .filter { it.dimension == level.dimension().identifier() }
        .filter { !it.isPaused() }
        .filter { it.expiresAt?.let { expiresAt -> level.gameTime < expiresAt } ?: true }
        .sortedWith(compareByDescending<CooTerrainMappingInstance> { it.priority }
            .thenByDescending { it.sequence }
            .thenBy { it.instanceId.toString() })
        .toList()

    /**
     * 向登录或换维度的玩家补发仍有效且接收范围允许的实例，包括暂停快照。
     * 示例：`syncTo(player)`。
     * @param player 需要补齐当前维度状态的服务端玩家
     */
    fun syncTo(player: ServerPlayer) {
        val level = player.level()
        synchronized(lock) {
            instances.values.asSequence()
                .map { managed -> managed to managed.instance }
                .filter { (_, instance) -> instance.dimension == level.dimension().identifier() }
                .filter { (managed, instance) -> managed.recipients == null || player.uuid in managed.recipients }
                .filter { (_, instance) -> instance.expiresAt?.let { expiresAt -> level.gameTime < expiresAt } ?: true }
                .forEach { (_, instance) ->
                    val pipeline = requireMapping(instance.mappingId).pipeline
                    CooServerPacketManager.sendTo(player, PacketTerrainMappingS2C.replace(instance, pipeline.id))
                }
        }
    }

    /**
     * 向玩家补发当前维度中的一个有效实例，不绕过实例接收范围。
     * 示例：`syncTo(player, instanceId)`。
     * @param player 请求补发的服务端玩家
     * @param instanceId 当前维度中的实例标识
     * @return 找到有效实例并成功发送时返回 true
     */
    fun syncTo(player: ServerPlayer, instanceId: Identifier): Boolean {
        val level = player.level()
        val key = MappingKey(level.dimension().identifier(), instanceId)
        synchronized(lock) {
            val managed = instances[key] ?: return false
            val instance = managed.instance
            if (instance.expiresAt?.let { level.gameTime >= it } == true) return false
            if (managed.recipients != null && player.uuid !in managed.recipients) return false
            val pipeline = requireMapping(instance.mappingId).pipeline
            return CooServerPacketManager.sendTo(player, PacketTerrainMappingS2C.replace(instance, pipeline.id))
        }
    }
    /**
     * 在服务器 tick 清理过期或所属维度卸载的实例，维度仍存在时发送移除通知。
     * 示例：`CooTerrainMappingManager.tick(server)`。
     * @param server 当前逻辑服务器，只能由服务端生命周期调用
     */
    fun tick(server: MinecraftServer) {
        val levels = server.allLevels.associateBy { it.dimension().identifier() }
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


    /** 返回服务端当前登记的实例数；到期实例在下次 [tick] 后移除。 */
    fun serverInstanceCount(): Int = instances.size

    /** 清空服务端运行态，保留模板注册。 */
    internal fun clearServerInstances() {
        synchronized(lock) { instances.clear() }
    }

    internal fun pipeline(id: Identifier): CooRenderPipeline<BlockState>? = mappings[id]?.pipeline

    private fun requireMapping(id: Identifier): CooTerrainMapping = requireNotNull(mappings[id]) {
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

    /** 按维度隔离实例名称，避免跨维度同名实例互相覆盖。 */
    private data class MappingKey(val dimension: Identifier, val id: Identifier)

    /** 实例快照、模板与可选固定接收者的内部组合，不随网络发送。 */
    private data class ManagedMapping(
        val instance: CooTerrainMappingInstance,
        val mapping: CooTerrainMapping,
        val recipients: Set<UUID>?
    )
}
