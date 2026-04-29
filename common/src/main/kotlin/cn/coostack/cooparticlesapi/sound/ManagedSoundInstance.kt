package cn.coostack.cooparticlesapi.sound

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec3

class ManagedSoundInstance(
    val key: String,
    soundId: ResourceLocation,
    source: SoundSource,
    entityId: Int,
    initialPos: Vec3,
    initialVolume: Float,
    initialPitch: Float,
    looping: Boolean,
    relative: Boolean
) : AbstractTickableSoundInstance(
    SoundEvent.createVariableRangeEvent(soundId),
    source,
    SoundInstance.createUnseededRandom()
) {
    var entityId: Int = entityId

    var volumeMultiplier: Float = initialVolume
        set(value) {
            field = value.coerceAtLeast(0f)
        }

    var pitchMultiplier: Float = initialPitch
        set(value) {
            field = value
        }

    var position: Vec3
        get() = Vec3(x, y, z)
        set(value) {
            entityId = -1
            setRawPosition(value)
        }

    var posX: Double
        get() = x
        set(value) {
            entityId = -1
            x = value
        }

    var posY: Double
        get() = y
        set(value) {
            entityId = -1
            y = value
        }

    var posZ: Double
        get() = z
        set(value) {
            entityId = -1
            z = value
        }

    var loopingSound: Boolean
        get() = looping
        set(value) {
            looping = value
        }

    var loopDelay: Int
        get() = delay
        set(value) {
            delay = value.coerceAtLeast(0)
        }

    var relativeSound: Boolean
        get() = relative
        set(value) {
            relative = value
        }

    var stopWhenBoundEntityMissing: Boolean = true
    var isStoppingAfterCurrentLoop = false
        private set

    init {
        this.looping = looping
        this.delay = 0
        this.volumeMultiplier = initialVolume
        this.pitchMultiplier = initialPitch
        setRawPosition(initialPos)
        this.relative = relative
    }

    override fun tick() {
        if (entityId < 0) {
            return
        }
        val level = Minecraft.getInstance().level ?: run {
            stopIfEntityTrackingFailed()
            return
        }
        val entity = level.getEntity(entityId) ?: run {
            stopIfEntityTrackingFailed()
            return
        }
        if (!entity.isAlive) {
            stopIfEntityTrackingFailed()
            return
        }
        setRawPosition(entity.position())
    }

    override fun canStartSilent(): Boolean {
        return true
    }

    override fun getVolume(): Float {
        return volumeMultiplier
    }

    override fun getPitch(): Float {
        return pitchMultiplier
    }

    fun update(
        entityId: Int,
        pos: Vec3,
        volume: Float,
        pitch: Float,
        looping: Boolean = loopingSound,
        relative: Boolean = relativeSound
    ) {
        this.entityId = entityId
        this.volumeMultiplier = volume
        this.pitchMultiplier = pitch
        this.loopingSound = looping
        this.relativeSound = relative
        setRawPosition(pos)
    }

    fun bindToEntity(entityId: Int) {
        this.entityId = entityId
    }

    fun unbindEntity() {
        this.entityId = -1
    }

    fun stopNow() {
        stop()
    }

    fun stopAfterCurrentLoop() {
        isStoppingAfterCurrentLoop = true
        looping = false
    }

    private fun setRawPosition(pos: Vec3) {
        x = pos.x
        y = pos.y
        z = pos.z
    }

    private fun stopIfEntityTrackingFailed() {
        if (stopWhenBoundEntityMissing) {
            stopNow()
        }
    }
}
