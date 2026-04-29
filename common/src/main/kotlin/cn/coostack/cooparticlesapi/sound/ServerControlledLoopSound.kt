package cn.coostack.cooparticlesapi.sound

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec3

class ServerControlledLoopSound(
    soundId: ResourceLocation,
    source: SoundSource,
    private val entityId: Int,
    fallbackPos: Vec3,
    volume: Float,
    pitch: Float
) : AbstractTickableSoundInstance(
    SoundEvent.createVariableRangeEvent(soundId),
    source,
    SoundInstance.createUnseededRandom()
) {
    var isStoppingAfterCurrentLoop = false
        private set

    init {
        this.looping = true
        this.delay = 0
        this.volume = volume
        this.pitch = pitch
        this.x = fallbackPos.x
        this.y = fallbackPos.y
        this.z = fallbackPos.z
        this.relative = false
    }

    override fun tick() {
        if (entityId < 0) {
            return
        }
        val level = Minecraft.getInstance().level ?: run {
            stopNow()
            return
        }
        val entity = level.getEntity(entityId) ?: run {
            stopNow()
            return
        }
        if (!entity.isAlive) {
            stopNow()
            return
        }
        x = entity.x
        y = entity.y
        z = entity.z
    }

    fun stopNow() {
        stop()
    }

    fun stopAfterCurrentLoop() {
        isStoppingAfterCurrentLoop = true
        looping = false
    }
}
