package cn.coostack.cooparticlesapi.sound

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
    private var configuredSyncEveryTick: Boolean = true
    private var configuredStopWhenBoundEntityMissing: Boolean = true
    private var cachedAutoKey: String? = null

    fun sound(sound: SoundEvent): SoundInstanceBuilder {
        return sound(sound.location)
    }

    fun sound(soundId: ResourceLocation): SoundInstanceBuilder {
        configuredSoundId = soundId
        clearAutoKey()
        return this
    }

    fun source(source: SoundSource): SoundInstanceBuilder {
        configuredSource = source
        return this
    }

    fun key(key: String): SoundInstanceBuilder {
        explicitKey = key
        cachedAutoKey = key
        return this
    }

    fun name(name: String): SoundInstanceBuilder {
        configuredKeyName = name
        clearAutoKey()
        return this
    }

    fun layer(layer: String): SoundInstanceBuilder {
        configuredKeyLayer = layer
        clearAutoKey()
        return this
    }

    @JvmOverloads
    fun uniqueKey(prefix: String = SoundInstanceKeys.soundName(configuredSoundId)): SoundInstanceBuilder {
        explicitKey = SoundInstanceKeys.unique(prefix)
        cachedAutoKey = explicitKey
        return this
    }

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

    @JvmOverloads
    fun entity(entity: Entity, self: Boolean = true): SoundInstanceBuilder {
        return bindToEntity(entity, self)
    }

    fun player(player: ServerPlayer): SoundInstanceBuilder {
        configuredTargetPlayer = player
        bindToEntity(player, true)
        configuredWorld = player.level() as ServerLevel
        return this
    }

    fun world(world: ServerLevel): SoundInstanceBuilder {
        configuredWorld = world
        return this
    }

    fun at(world: ServerLevel, position: Vec3): SoundInstanceBuilder {
        configuredWorld = world
        configuredPosition = position
        boundEntity = null
        clearAutoKey()
        return this
    }

    fun position(position: Vec3): SoundInstanceBuilder {
        configuredPosition = position
        boundEntity = null
        clearAutoKey()
        return this
    }

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

    fun pitch(pitch: Float): SoundInstanceBuilder {
        configuredPitch = pitch
        return this
    }

    @JvmOverloads
    fun looping(looping: Boolean = true): SoundInstanceBuilder {
        configuredLooping = looping
        return this
    }

    @JvmOverloads
    fun relative(relative: Boolean = true): SoundInstanceBuilder {
        configuredRelative = relative
        return this
    }

    fun self(self: Boolean): SoundInstanceBuilder {
        includeSelf = self
        return this
    }

    fun visibleRange(visibleRange: Double): SoundInstanceBuilder {
        configuredVisibleRange = visibleRange
        return this
    }

    fun syncEveryTick(syncEveryTick: Boolean): SoundInstanceBuilder {
        configuredSyncEveryTick = syncEveryTick
        return this
    }

    fun stopWhenBoundEntityMissing(stopWhenBoundEntityMissing: Boolean): SoundInstanceBuilder {
        configuredStopWhenBoundEntityMissing = stopWhenBoundEntityMissing
        return this
    }

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
            syncEveryTick = configuredSyncEveryTick,
            stopWhenBoundEntityMissing = configuredStopWhenBoundEntityMissing
        )
    }

    fun build(): ServerManagedSoundInstance {
        val fixedWorld = resolveWorld()
        val spec = buildSpec()
        val instance = ServerManagedSoundInstance(spec, fixedWorld)
        instance.targetPlayer = configuredTargetPlayer
        boundEntity?.let(instance::bindToEntity)
        return instance
    }

    fun spawn(): ServerManagedSoundInstance {
        return ServerSoundManager.spawn(build())
    }

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

    private fun clearAutoKey() {
        if (explicitKey == null) {
            cachedAutoKey = null
        }
    }
}
