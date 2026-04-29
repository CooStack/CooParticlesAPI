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

    var volumeMultiplier: Float = initialVolume
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
    fun fadeOut(ticks: Int, stopWhenFinished: Boolean = true, interruptWhenStopped: Boolean = true): CooScheduler.TickRunnable? {
        return fadeTo(0f, ticks, stopWhenFinished, interruptWhenStopped)
    }

    @JvmOverloads
    fun fadeIn(ticks: Int, targetVolume: Float = 1f, fromVolume: Float = volumeMultiplier): CooScheduler.TickRunnable? {
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
        if (player.level() != world) {
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

    fun toPlayPacket(): PacketSoundInstanceS2C {
        return PacketSoundInstanceS2C.play(
            key = key,
            sound = soundId,
            source = source,
            entityId = currentEntityId(),
            pos = position,
            volume = volumeMultiplier,
            pitch = pitchMultiplier,
            looping = loopingSound,
            relative = relativeSound
        )
    }

    fun toUpdatePacket(): PacketSoundInstanceS2C {
        return PacketSoundInstanceS2C.update(
            key = key,
            entityId = currentEntityId(),
            pos = position,
            volume = volumeMultiplier,
            pitch = pitchMultiplier,
            looping = loopingSound,
            relative = relativeSound
        )
    }

    fun toStopPacket(): PacketSoundInstanceS2C {
        return PacketSoundInstanceS2C.stop(key, stopImmediately)
    }

    internal fun needsUpdatePacket(): Boolean {
        return dirty || syncEveryTick
    }

    internal fun needsPlayPacket(): Boolean {
        return restartRequested
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
