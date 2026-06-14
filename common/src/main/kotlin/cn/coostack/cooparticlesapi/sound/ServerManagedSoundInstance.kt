package cn.coostack.cooparticlesapi.sound

import cn.coostack.cooparticlesapi.CooParticlesAPI
import cn.coostack.cooparticlesapi.network.packet.server.PacketSoundInstanceS2C
import cn.coostack.cooparticlesapi.scheduler.CooScheduler
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import kotlin.math.max

/**
 * 服务端权威的音频实例，由 [ServerSoundManager] 同步到客户端播放。
 *
 * [key] 是实例身份，不只是调试名称。两个存活音频使用同一个 key 时，会被当成同一个
 * 可控实例；后来的 PLAY 包会替换客户端上更早的声音。需要多个声音重叠播放时，使用
 * [SoundInstanceBuilder] 自动生成 key，或使用 [ServerSoundManager.key] 加入 layer。
 *
 * 音量使用客户端 SoundInstance 的百分比/倍率语义：0f 表示静音，1f 表示资源正常音量。
 * 大于 1f 不应作为更大响度依赖；实际输出可能被原版声音引擎、声源分类音量或系统音量限制。
 *
 * [relative] 是原版 SoundInstance 的“相对监听者”模式：声音会按玩家耳朵/监听者来播放，
 * 不作为普通世界坐标声音处理。UI 声、耳鸣、玩家耳边提示音可以用 true。实体或方块发出的
 * 世界声音需要距离衰减和方向感时应保持 false；需要跟随实体时用 [bindToEntity]。
 */
class ServerManagedSoundInstance(
    val key: String,
    soundId: ResourceLocation,
    source: SoundSource,
    world: ServerLevel,
    initialPos: Vec3,
    initialVolume: Float = 1f,
    initialPitch: Float = 1f,
    looping: Boolean = false,
    relative: Boolean = false
) {
    constructor(
        key: String,
        sound: SoundEvent,
        source: SoundSource,
        world: ServerLevel,
        initialPos: Vec3,
        initialVolume: Float = 1f,
        initialPitch: Float = 1f,
        looping: Boolean = false,
        relative: Boolean = false
    ) : this(key, sound.location, source, world, initialPos, initialVolume, initialPitch, looping, relative)

    constructor(
        spec: SoundInstanceSpec,
        world: ServerLevel
    ) : this(
        key = spec.key,
        soundId = spec.soundId,
        source = spec.source,
        world = world,
        initialPos = spec.position,
        initialVolume = spec.volume,
        initialPitch = spec.pitch,
        looping = spec.looping,
        relative = spec.relative
    ) {
        entityId = spec.entityId
        self = spec.self
        visibleRange = spec.visibleRange
        volumeFalloff = spec.volumeFalloff
        syncEveryTick = spec.syncEveryTick
        stopWhenBoundEntityMissing = spec.stopWhenBoundEntityMissing
    }

    var soundId: ResourceLocation = soundId
        set(value) {
            if (field == value) {
                return
            }
            field = value
            markRestart()
        }

    var source: SoundSource = source
        set(value) {
            if (field == value) {
                return
            }
            field = value
            markRestart()
        }

    var world: ServerLevel = world
        set(value) {
            if (field == value) {
                return
            }
            field = value
            markDirty()
        }

    var targetPlayer: ServerPlayer? = null
        set(value) {
            field = value
            markDirty()
        }

    var self: Boolean = true
        set(value) {
            if (field == value) {
                return
            }
            field = value
            markDirty()
        }

    var entity: Entity? = null
        set(value) {
            field = value
            if (value != null) {
                entityId = value.id
                val level = value.level()
                if (level is ServerLevel) {
                    world = level
                }
                setRawPosition(value.position())
            } else {
                entityId = -1
            }
            markDirty()
        }

    var entityId: Int = -1
        set(value) {
            if (field == value) {
                return
            }
            field = value
            markDirty()
        }

    var position: Vec3 = initialPos
        set(value) {
            field = value
            if (!updatingFromBoundEntity) {
                entity = null
                entityId = -1
            }
            markDirty()
        }

    var posX: Double
        get() = position.x
        set(value) {
            position = Vec3(value, position.y, position.z)
        }

    var posY: Double
        get() = position.y
        set(value) {
            position = Vec3(position.x, value, position.z)
        }

    var posZ: Double
        get() = position.z
        set(value) {
            position = Vec3(position.x, position.y, value)
        }

    /**
     * 初始音量百分比/倍率。0f 为静音，1f 为正常资源音量。
     */
    val initialVolumeMultiplier: Float = initialVolume.coerceAtLeast(0f)

    /**
     * 当前音量百分比/倍率。该值会同步到客户端 SoundInstance#getVolume。
     */
    var volumeMultiplier: Float = initialVolumeMultiplier
        set(value) {
            val next = value.coerceAtLeast(0f)
            if (field == next) {
                return
            }
            field = next
            markDirty()
        }

    var pitchMultiplier: Float = initialPitch
        set(value) {
            if (field == value) {
                return
            }
            field = value
            markDirty()
        }

    var loopingSound: Boolean = looping
        set(value) {
            if (field == value) {
                return
            }
            field = value
            markDirty()
        }

    /**
     * 原版“相对监听者”模式。它不负责绑定实体；实体跟随由 [entity] / [bindToEntity] 控制。
     */
    var relativeSound: Boolean = relative
        set(value) {
            if (field == value) {
                return
            }
            field = value
            markDirty()
        }

    var visibleRange: Double = -1.0
        set(value) {
            if (field == value) {
                return
            }
            field = value
            markDirty()
        }

    var volumeFalloff: SoundVolumeFalloff = SoundVolumeFalloff.NONE
        set(value) {
            if (field == value) {
                return
            }
            field = value
            markDirty()
        }

    var syncEveryTick: Boolean = true
    var stopWhenBoundEntityMissing: Boolean = true
    var stopImmediately: Boolean = true
        private set
    var isStopped: Boolean = false
        private set

    private var dirty = true
    private var restartRequested = true
    private var updatingFromBoundEntity = false

    fun bindToEntity(entity: Entity) {
        this.entity = entity
    }

    fun unbindEntity() {
        entity = null
        entityId = -1
        markDirty()
    }

    fun stopNow(interrupt: Boolean = true) {
        stopImmediately = interrupt
        isStopped = true
        markDirty()
    }

    fun stopAfterCurrentLoop() {
        stopNow(false)
    }

    /**
     * 通过服务端 scheduler 每 tick 修改 [volumeMultiplier] 来实现音量渐变。
     * [targetVolume] 使用百分比/倍率语义：0f 为静音，1f 为正常资源音量。
     * 返回的任务可以由调用方 cancel。
     */
    @JvmOverloads
    fun fadeTo(
        targetVolume: Float,
        ticks: Int,
        stopWhenFinished: Boolean = false,
        interruptWhenStopped: Boolean = true
    ): CooScheduler.TickRunnable? {
        val fixedTicks = ticks.coerceAtLeast(0)
        val fixedTarget = targetVolume.coerceAtLeast(0f)
        if (fixedTicks == 0) {
            volumeMultiplier = fixedTarget
            if (stopWhenFinished) {
                stopNow(interruptWhenStopped)
            }
            return null
        }

        val startVolume = volumeMultiplier
        var elapsed = 0
        return CooParticlesAPI.scheduler.runTaskTimerMaxTick(fixedTicks) {
            if (isStopped) {
                cancel()
                return@runTaskTimerMaxTick
            }
            elapsed++
            val progress = (elapsed.toFloat() / fixedTicks).coerceIn(0f, 1f)
            volumeMultiplier = startVolume + (fixedTarget - startVolume) * progress
        }.setFinishCallback {
            volumeMultiplier = fixedTarget
            if (stopWhenFinished && !isStopped) {
                stopNow(interruptWhenStopped)
            }
        }
    }

    @JvmOverloads
    fun fadeOut(
        ticks: Int,
        stopWhenFinished: Boolean = true,
        interruptWhenStopped: Boolean = true
    ): CooScheduler.TickRunnable? {
        return fadeTo(0f, ticks, stopWhenFinished, interruptWhenStopped)
    }

    @JvmOverloads
    fun fadeIn(
        ticks: Int,
        targetVolume: Float = initialVolumeMultiplier,
        fromVolume: Float = 0f
    ): CooScheduler.TickRunnable? {
        volumeMultiplier = fromVolume
        return fadeTo(targetVolume, ticks, false)
    }

    fun markDirty() {
        dirty = true
    }

    fun markRestart() {
        restartRequested = true
        markDirty()
    }

    fun tick() {
        val bound = entity ?: return
        if (!bound.isAlive) {
            stopOrUnbindMissingEntity()
            return
        }
        val level = bound.level()
        if (level !is ServerLevel) {
            stopOrUnbindMissingEntity()
            return
        }
        world = level
        entityId = bound.id
        setRawPosition(bound.position())
    }

    fun shouldSyncTo(player: ServerPlayer): Boolean {
        val target = targetPlayer
        if (target != null && player.uuid != target.uuid) {
            return false
        }
        if (!self && entity is ServerPlayer && player.uuid == entity!!.uuid) {
            return false
        }
        if (player.level().dimension() != world.dimension()) {
            return false
        }
        if (target != null) {
            return true
        }
        return player.position().distanceTo(position) <= effectiveVisibleRange()
    }

    fun effectiveVisibleRange(): Double {
        if (visibleRange >= 0.0) {
            return visibleRange
        }
        return max(16.0, volumeMultiplier.toDouble() * 16.0)
    }

    fun volumeFor(player: ServerPlayer): Float {
        val distance = player.position().distanceTo(position)
        val range = effectiveVisibleRange()
        return volumeMultiplier * volumeFalloff.factor(distance, range)
    }

    @JvmOverloads
    fun toPlayPacket(volume: Float = volumeMultiplier): PacketSoundInstanceS2C {
        return PacketSoundInstanceS2C.play(
            key = key,
            sound = soundId,
            source = source,
            entityId = currentEntityId(),
            pos = position,
            volume = volume,
            pitch = pitchMultiplier,
            looping = loopingSound,
            relative = relativeSound
        )
    }

    @JvmOverloads
    fun toUpdatePacket(volume: Float = volumeMultiplier): PacketSoundInstanceS2C {
        return PacketSoundInstanceS2C.update(
            key = key,
            entityId = currentEntityId(),
            pos = position,
            volume = volume,
            pitch = pitchMultiplier,
            looping = loopingSound,
            relative = relativeSound
        )
    }

    fun toStopPacket(): PacketSoundInstanceS2C {
        return PacketSoundInstanceS2C.stop(key, stopImmediately)
    }

    fun toSpec(): SoundInstanceSpec {
        return SoundInstanceSpec(
            key = key,
            soundId = soundId,
            source = source,
            entityId = currentEntityId(),
            position = position,
            volume = volumeMultiplier,
            pitch = pitchMultiplier,
            looping = loopingSound,
            relative = relativeSound,
            self = self,
            visibleRange = visibleRange,
            volumeFalloff = volumeFalloff,
            syncEveryTick = syncEveryTick,
            stopWhenBoundEntityMissing = stopWhenBoundEntityMissing
        )
    }

    internal fun needsUpdatePacket(): Boolean {
        return dirty || syncEveryTick
    }

    internal fun needsPlayPacket(): Boolean {
        return restartRequested
    }

    internal fun canDiscardAfterSync(): Boolean {
        return !isStopped && !loopingSound && !syncEveryTick
    }

    internal fun clearSyncFlags() {
        dirty = false
        restartRequested = false
    }

    private fun currentEntityId(): Int {
        return entity?.id ?: entityId
    }

    private fun setRawPosition(pos: Vec3) {
        updatingFromBoundEntity = true
        position = pos
        updatingFromBoundEntity = false
    }

    private fun stopOrUnbindMissingEntity() {
        if (stopWhenBoundEntityMissing) {
            stopNow()
        } else {
            unbindEntity()
        }
    }
}
