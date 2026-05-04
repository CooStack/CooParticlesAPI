package cn.coostack.cooparticlesapi.sound

import cn.coostack.cooparticlesapi.CooParticlesAPI
import cn.coostack.cooparticlesapi.network.packet.server.PacketSoundInstanceS2C
import cn.coostack.cooparticlesapi.scheduler.CooScheduler
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

/**
 * 服务端权威的音频压低/屏蔽实例，用来降低客户端上非白名单声音的音量。
 *
 * [key] 是实例身份。再次启动同 key 的 ducking 会更新/替换同一个效果；需要多个 ducking
 * 同时存在时必须使用不同 key。
 *
 * [volumeMultiplier] 会作用到非白名单声音上。0f 表示完全静音，1f 表示不压低。
 * [range] < 0 表示对已同步玩家全局生效；否则按监听者距离 [position] 的远近逐渐减弱。
 */
class ServerDuckingSoundEffect(
    val key: String,
    world: ServerLevel,
    initialPos: Vec3 = Vec3.ZERO,
    volumeMultiplier: Float = 0f,
    range: Double = -1.0,
    whitelistSounds: Set<ResourceLocation> = emptySet(),
    whitelistSources: Set<SoundSource> = emptySet(),
    whitelistKeys: Set<String> = emptySet()
) {
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

    val initialVolumeMultiplier: Float = Mth.clamp(volumeMultiplier, 0f, 1f)

    var volumeMultiplier: Float = initialVolumeMultiplier
        set(value) {
            val next = Mth.clamp(value, 0f, 1f)
            if (field == next) {
                return
            }
            field = next
            markDirty()
        }

    var range: Double = range
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

    val whitelistSounds: MutableSet<ResourceLocation> = whitelistSounds.toMutableSet()
    val whitelistSources: MutableSet<SoundSource> = whitelistSources.toMutableSet()
    val whitelistKeys: MutableSet<String> = whitelistKeys.toMutableSet()

    var syncEveryTick: Boolean = true
    var stopWhenBoundEntityMissing: Boolean = true
    var isStopped: Boolean = false
        private set

    private var dirty = true
    private var updatingFromBoundEntity = false

    fun bindToEntity(entity: Entity) {
        this.entity = entity
    }

    fun unbindEntity() {
        entity = null
        entityId = -1
        markDirty()
    }

    fun stopNow() {
        isStopped = true
        markDirty()
    }

    /**
     * 通过服务端 scheduler 每 tick 修改 ducking 倍率来实现渐变。
     * 注意：数值越低压低越强，1f 表示不压低。
     */
    @JvmOverloads
    fun fadeTo(targetVolumeMultiplier: Float, ticks: Int, stopWhenFinished: Boolean = false): CooScheduler.TickRunnable? {
        val fixedTicks = ticks.coerceAtLeast(0)
        val fixedTarget = Mth.clamp(targetVolumeMultiplier, 0f, 1f)
        if (fixedTicks == 0) {
            volumeMultiplier = fixedTarget
            if (stopWhenFinished) {
                stopNow()
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
                stopNow()
            }
        }
    }

    @JvmOverloads
    fun fadeOut(ticks: Int, stopWhenFinished: Boolean = true): CooScheduler.TickRunnable? {
        return fadeTo(1f, ticks, stopWhenFinished)
    }

    @JvmOverloads
    fun fadeIn(
        ticks: Int,
        targetVolumeMultiplier: Float = initialVolumeMultiplier,
        fromVolumeMultiplier: Float = 1f
    ): CooScheduler.TickRunnable? {
        volumeMultiplier = fromVolumeMultiplier
        return fadeTo(targetVolumeMultiplier, ticks, false)
    }

    fun markDirty() {
        dirty = true
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
        val range = effectiveVisibleRange()
        if (range < 0.0) {
            return true
        }
        return player.position().distanceTo(position) <= range
    }

    fun effectiveVisibleRange(): Double {
        if (visibleRange >= 0.0) {
            return visibleRange
        }
        return range
    }

    fun toStartPacket(): PacketSoundInstanceS2C {
        return packet(PacketSoundInstanceS2C.Action.DUCK_START)
    }

    fun toUpdatePacket(): PacketSoundInstanceS2C {
        return packet(PacketSoundInstanceS2C.Action.DUCK_UPDATE)
    }

    fun toStopPacket(): PacketSoundInstanceS2C {
        return packet(PacketSoundInstanceS2C.Action.DUCK_STOP)
    }

    internal fun needsUpdatePacket(): Boolean {
        return dirty || syncEveryTick
    }

    internal fun clearSyncFlags() {
        dirty = false
    }

    private fun packet(action: PacketSoundInstanceS2C.Action): PacketSoundInstanceS2C {
        return PacketSoundInstanceS2C.duck(
            action = action,
            key = key,
            entityId = currentEntityId(),
            pos = position,
            volumeMultiplier = volumeMultiplier,
            range = range,
            whitelistSounds = whitelistSounds,
            whitelistSources = whitelistSources,
            whitelistKeys = whitelistKeys
        )
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
