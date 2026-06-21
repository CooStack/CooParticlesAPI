package cn.coostack.cooparticlesapi.supports.sound

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

/**
 * 服务端可控音频实例的构建器。
 *
 * 常规用法是先配置位置/实体、音量、循环等参数，再调用 [build] 预构建实例；
 * 预构建实例不会播放，传给 [ServerSoundManager.spawn] 后才会同步到客户端。
 * 如果希望一步完成，可以直接调用 [spawn]。
 *
 * 音量参数使用客户端 SoundInstance 的百分比/倍率语义：0f 表示静音，1f 表示资源正常音量。
 * 大于 1f 不应作为更大响度依赖；实际输出可能被原版声音引擎、声源分类音量或系统音量限制。
 *
 * 默认 key 会自动生成：绑定实体时使用“实体 UUID + 声音 id”，不同音效天然不会互相覆盖；
 * 同一个实体上同一个声音要重叠播放时，再调用 [layer] 或 [uniqueKey]。
 */
class SoundInstanceBuilder @JvmOverloads constructor(
    initialSoundId: ResourceLocation,
    initialSource: SoundSource = SoundSource.MASTER
) {
    constructor(sound: SoundEvent, source: SoundSource = SoundSource.MASTER) : this(sound.location, source)

    private var configuredSoundId: ResourceLocation = initialSoundId
    private var configuredSource: SoundSource = initialSource
    private var explicitKey: String? = null
    private var configuredKeyName: String? = null
    private var configuredKeyLayer: String? = null
    private var configuredWorld: ServerLevel? = null
    private var configuredTargetPlayer: ServerPlayer? = null
    private var boundEntity: Entity? = null
    private var configuredPosition: Vec3 = Vec3.ZERO
    private var configuredVolume: Float = 1f
    private var configuredPitch: Float = 1f
    private var configuredLooping: Boolean = false
    private var configuredRelative: Boolean = false
    private var includeSelf: Boolean = true
    private var configuredVisibleRange: Double = -1.0
    private var configuredVolumeFalloff: SoundVolumeFalloff = SoundVolumeFalloff.NONE
    private var configuredSyncEveryTick: Boolean = true
    private var configuredStopWhenBoundEntityMissing: Boolean = true
    private var cachedAutoKey: String? = null

    /**
     * 设置要播放的声音事件。
     */
    fun sound(sound: SoundEvent): SoundInstanceBuilder {
        return sound(sound.location)
    }

    /**
     * 设置要播放的声音资源 id。
     */
    fun sound(soundId: ResourceLocation): SoundInstanceBuilder {
        configuredSoundId = soundId
        clearAutoKey()
        return this
    }

    /**
     * 设置声音分类，最终会受玩家对应分类音量设置影响。
     */
    fun source(source: SoundSource): SoundInstanceBuilder {
        configuredSource = source
        return this
    }

    /**
     * 显式指定实例 key。同 key 的声音会被当作同一个可控实例。
     */
    fun key(key: String): SoundInstanceBuilder {
        explicitKey = key
        cachedAutoKey = key
        return this
    }

    /**
     * 设置自动 key 使用的逻辑名称。
     */
    fun name(name: String): SoundInstanceBuilder {
        configuredKeyName = name
        clearAutoKey()
        return this
    }

    /**
     * 设置自动 key 使用的层名，用于同一实体上叠放多个同名声音。
     */
    fun layer(layer: String): SoundInstanceBuilder {
        configuredKeyLayer = layer
        clearAutoKey()
        return this
    }

    /**
     * 生成一个唯一 key，适合允许多个实例同时播放且互不覆盖的短声音。
     */
    @JvmOverloads
    fun uniqueKey(prefix: String = SoundInstanceKeys.soundName(configuredSoundId)): SoundInstanceBuilder {
        explicitKey = SoundInstanceKeys.unique(prefix)
        cachedAutoKey = explicitKey
        return this
    }

    /**
     * 让声音跟随实体位置同步；self 为 false 时实体自身玩家不会收到这个声音。
     */
    @JvmOverloads
    fun bindToEntity(entity: Entity, self: Boolean = true): SoundInstanceBuilder {
        boundEntity = entity
        includeSelf = self
        configuredPosition = entity.position()
        val level = entity.level()
        if (level is ServerLevel) {
            configuredWorld = level
        }
        clearAutoKey()
        return this
    }

    /**
     * [bindToEntity] 的别名，用于链式调用时更简短。
     */
    @JvmOverloads
    fun entity(entity: Entity, self: Boolean = true): SoundInstanceBuilder {
        return bindToEntity(entity, self)
    }

    /**
     * 只把声音同步给指定玩家，并默认绑定到该玩家位置。
     */
    fun player(player: ServerPlayer): SoundInstanceBuilder {
        configuredTargetPlayer = player
        bindToEntity(player, true)
        configuredWorld = player.level() as ServerLevel
        return this
    }

    /**
     * 设置声音所属服务端世界；使用固定位置播放时必须提供。
     */
    fun world(world: ServerLevel): SoundInstanceBuilder {
        configuredWorld = world
        return this
    }

    /**
     * 设置固定世界坐标播放位置，并解除实体绑定。
     */
    fun at(world: ServerLevel, position: Vec3): SoundInstanceBuilder {
        configuredWorld = world
        configuredPosition = position
        boundEntity = null
        clearAutoKey()
        return this
    }

    /**
     * 设置固定播放位置，并解除实体绑定；需要另行设置 [world]。
     */
    fun position(position: Vec3): SoundInstanceBuilder {
        configuredPosition = position
        boundEntity = null
        clearAutoKey()
        return this
    }

    /**
     * 使用三个坐标值设置固定播放位置。
     */
    fun position(x: Double, y: Double, z: Double): SoundInstanceBuilder {
        return position(Vec3(x, y, z))
    }

    /**
     * 设置播放音量百分比/倍率。0f 为静音，1f 为正常资源音量。
     */
    fun volume(volume: Float): SoundInstanceBuilder {
        configuredVolume = volume
        return this
    }

    /**
     * 设置播放音高倍率。1f 为资源原始音高。
     */
    fun pitch(pitch: Float): SoundInstanceBuilder {
        configuredPitch = pitch
        return this
    }

    /**
     * 设置是否循环播放。循环声音通常应使用稳定 key，方便后续 update/stop。
     */
    @JvmOverloads
    fun looping(looping: Boolean = true): SoundInstanceBuilder {
        configuredLooping = looping
        return this
    }

    /**
     * 设置原版相对监听者模式
     */
    @JvmOverloads
    fun relative(relative: Boolean = true): SoundInstanceBuilder {
        configuredRelative = relative
        return this
    }

    /**
     * 设置绑定实体是玩家时，是否也把声音同步给实体本人。
     */
    fun self(self: Boolean): SoundInstanceBuilder {
        includeSelf = self
        return this
    }

    /**
     * 设置服务端可见范围。玩家在范围外不会收到该声音同步。
     */
    fun visibleRange(visibleRange: Double): SoundInstanceBuilder {
        configuredVisibleRange = visibleRange
        return this
    }

    /**
     * 设置音量距离衰减方式。默认 [SoundVolumeFalloff.NONE] 保持固定音量。
     */
    fun volumeFalloff(volumeFalloff: SoundVolumeFalloff): SoundInstanceBuilder {
        configuredVolumeFalloff = volumeFalloff
        return this
    }

    /**
     * 设置是否每 tick 同步位置和音量。实体跟随或距离衰减声音通常应保持 true。
     */
    fun syncEveryTick(syncEveryTick: Boolean): SoundInstanceBuilder {
        configuredSyncEveryTick = syncEveryTick
        return this
    }

    /**
     * 设置绑定实体丢失或死亡时，客户端声音是否自动停止。
     */
    fun stopWhenBoundEntityMissing(stopWhenBoundEntityMissing: Boolean): SoundInstanceBuilder {
        configuredStopWhenBoundEntityMissing = stopWhenBoundEntityMissing
        return this
    }

    /**
     * 生成可同步声音参数快照，不创建服务端实例。
     */
    fun buildSpec(): SoundInstanceSpec {
        val entity = boundEntity
        val currentPosition = entity?.position() ?: configuredPosition
        return SoundInstanceSpec(
            key = resolveKey(),
            soundId = configuredSoundId,
            source = configuredSource,
            entityId = entity?.id ?: -1,
            position = currentPosition,
            volume = configuredVolume,
            pitch = configuredPitch,
            looping = configuredLooping,
            relative = configuredRelative,
            self = includeSelf,
            visibleRange = configuredVisibleRange,
            volumeFalloff = configuredVolumeFalloff,
            syncEveryTick = configuredSyncEveryTick,
            stopWhenBoundEntityMissing = configuredStopWhenBoundEntityMissing
        )
    }

    /**
     * 构建服务端音频实例但不播放，调用方可继续修改实例后再交给 [ServerSoundManager.spawn]。
     */
    fun build(): ServerManagedSoundInstance {
        val fixedWorld = resolveWorld()
        val spec = buildSpec()
        val instance = ServerManagedSoundInstance(spec, fixedWorld)
        instance.targetPlayer = configuredTargetPlayer
        boundEntity?.let(instance::bindToEntity)
        return instance
    }

    /**
     * 构建并立即注册播放该服务端音频实例。
     */
    fun spawn(): ServerManagedSoundInstance {
        return ServerSoundManager.spawn(build())
    }

    /**
     * 解析构建时要使用的服务端世界，优先使用显式 world，其次是绑定实体，再次是目标玩家。
     */
    private fun resolveWorld(): ServerLevel {
        val fixedWorld = configuredWorld
        if (fixedWorld != null) {
            return fixedWorld
        }
        val entityLevel = boundEntity?.level()
        if (entityLevel is ServerLevel) {
            return entityLevel
        }
        val playerLevel = configuredTargetPlayer?.level()
        if (playerLevel is ServerLevel) {
            return playerLevel
        }
        throw IllegalStateException("构建服务端音频实例前必须指定 ServerLevel、ServerPlayer 或服务端实体。")
    }

    /**
     * 解析最终 key。优先使用显式 key，其次使用缓存的自动 key，再根据 name/layer/实体生成。
     */
    private fun resolveKey(): String {
        val cached = cachedAutoKey
        if (cached != null) {
            return cached
        }
        val explicit = explicitKey
        if (explicit != null) {
            cachedAutoKey = explicit
            return explicit
        }

        val name = configuredKeyName ?: SoundInstanceKeys.soundName(configuredSoundId)
        val layer = configuredKeyLayer
        val entity = boundEntity
        val next = when {
            entity != null && !layer.isNullOrBlank() -> SoundInstanceKeys.entity(entity, name, layer)
            entity != null -> SoundInstanceKeys.entity(entity, name)
            !layer.isNullOrBlank() -> SoundInstanceKeys.unique("$name:$layer")
            else -> SoundInstanceKeys.unique(name)
        }
        cachedAutoKey = next
        return next
    }

    /**
     * 在会影响自动 key 的配置项变化后清掉缓存，避免继续复用旧 key。
     */
    private fun clearAutoKey() {
        if (explicitKey == null) {
            cachedAutoKey = null
        }
    }
}
