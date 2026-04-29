package cn.coostack.cooparticlesapi.sound

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3

class DuckingSoundEffect(
    val key: String,
    entityId: Int,
    initialPos: Vec3,
    volumeMultiplier: Float,
    range: Double,
    whitelistSounds: Set<ResourceLocation>,
    whitelistSources: Set<SoundSource>,
    whitelistKeys: Set<String>
) {
    var entityId: Int = entityId

    var position: Vec3 = initialPos
        set(value) {
            entityId = -1
            field = value
        }

    var volumeMultiplier: Float = Mth.clamp(volumeMultiplier, 0f, 1f)
        set(value) {
            field = Mth.clamp(value, 0f, 1f)
        }

    var range: Double = range
    val whitelistSounds: MutableSet<ResourceLocation> = whitelistSounds.toMutableSet()
    val whitelistSources: MutableSet<SoundSource> = whitelistSources.toMutableSet()
    val whitelistKeys: MutableSet<String> = whitelistKeys.toMutableSet()
    var isStopped = false
        private set

    fun tick() {
        if (entityId < 0) {
            return
        }
        val level = Minecraft.getInstance().level ?: run {
            isStopped = true
            return
        }
        val entity = level.getEntity(entityId) ?: run {
            isStopped = true
            return
        }
        if (!entity.isAlive) {
            isStopped = true
            return
        }
        setRawPosition(entity.position())
    }

    fun update(
        entityId: Int,
        pos: Vec3,
        volumeMultiplier: Float,
        range: Double,
        whitelistSounds: Set<ResourceLocation>,
        whitelistSources: Set<SoundSource>,
        whitelistKeys: Set<String>
    ) {
        this.entityId = entityId
        setRawPosition(pos)
        this.volumeMultiplier = volumeMultiplier
        this.range = range
        this.whitelistSounds.clear()
        this.whitelistSounds.addAll(whitelistSounds)
        this.whitelistSources.clear()
        this.whitelistSources.addAll(whitelistSources)
        this.whitelistKeys.clear()
        this.whitelistKeys.addAll(whitelistKeys)
        this.isStopped = false
    }

    fun bindToEntity(entityId: Int) {
        this.entityId = entityId
    }

    fun unbindEntity() {
        this.entityId = -1
    }

    fun stopNow() {
        isStopped = true
    }

    fun isWhitelisted(sound: SoundInstance): Boolean {
        if (sound.location in whitelistSounds || sound.source in whitelistSources) {
            return true
        }
        return sound is ManagedSoundInstance && sound.key in whitelistKeys
    }

    fun multiplierFor(listenerPos: Vec3): Float {
        if (range <= 0.0) {
            return volumeMultiplier
        }
        val distance = listenerPos.distanceTo(position)
        if (distance >= range) {
            return 1f
        }
        val strength = (1.0 - distance / range).toFloat()
        return 1f - (1f - volumeMultiplier) * strength
    }

    private fun setRawPosition(pos: Vec3) {
        val boundEntityId = entityId
        position = pos
        entityId = boundEntityId
    }
}
