package cn.coostack.cooparticlesapi.sound

import cn.coostack.cooparticlesapi.network.packet.server.PacketSoundInstanceS2C
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec3
import kotlin.math.min

object ClientSoundManager {
    private val sounds = HashMap<String, ManagedSoundInstance>()
    private val ducks = HashMap<String, DuckingSoundEffect>()
    private var volumeRefreshRequested = false

    @JvmStatic
    fun get(key: String): ManagedSoundInstance? {
        return sounds[key]
    }

    @JvmStatic
    fun getDucking(key: String): DuckingSoundEffect? {
        return ducks[key]
    }

    @JvmStatic
    fun activeSounds(): List<ManagedSoundInstance> {
        return sounds.values.toList()
    }

    @JvmStatic
    fun activeDuckingEffects(): List<DuckingSoundEffect> {
        return ducks.values.toList()
    }

    @JvmStatic
    fun isPlaying(key: String): Boolean {
        return sounds[key]?.isStopped == false
    }

    @JvmStatic
    fun isDucking(key: String): Boolean {
        return ducks.containsKey(key)
    }

    @JvmStatic
    fun play(
        key: String,
        soundId: ResourceLocation,
        source: SoundSource,
        entityId: Int = -1,
        pos: Vec3 = Vec3.ZERO,
        volume: Float = 1f,
        pitch: Float = 1f,
        looping: Boolean = false,
        relative: Boolean = false
    ): ManagedSoundInstance {
        return play(
            SoundInstanceSpec(
                key = key,
                soundId = soundId,
                source = source,
                entityId = entityId,
                position = pos,
                volume = volume,
                pitch = pitch,
                looping = looping,
                relative = relative
            )
        )
    }

    @JvmStatic
    fun play(spec: SoundInstanceSpec): ManagedSoundInstance {
        stop(spec.key, true)
        val sound = ManagedSoundInstance(spec)
        sounds[spec.key] = sound
        Minecraft.getInstance().soundManager.play(sound)
        return sound
    }

    @JvmStatic
    fun startDucking(
        key: String,
        entityId: Int = -1,
        pos: Vec3 = Vec3.ZERO,
        volumeMultiplier: Float = 0f,
        range: Double = -1.0,
        whitelistSounds: Set<ResourceLocation> = emptySet(),
        whitelistSources: Set<SoundSource> = emptySet(),
        whitelistKeys: Set<String> = emptySet()
    ): DuckingSoundEffect {
        val duck = ducks[key]?.also {
            it.update(entityId, pos, volumeMultiplier, range, whitelistSounds, whitelistSources, whitelistKeys)
        } ?: DuckingSoundEffect(
            key = key,
            entityId = entityId,
            initialPos = pos,
            volumeMultiplier = volumeMultiplier,
            range = range,
            whitelistSounds = whitelistSounds,
            whitelistSources = whitelistSources,
            whitelistKeys = whitelistKeys
        )
        ducks[key] = duck
        requestVolumeRefresh()
        return duck
    }

    @JvmStatic
    fun stop(key: String, interrupt: Boolean = true) {
        val sound = sounds[key] ?: return
        if (interrupt) {
            sounds.remove(key)
            sound.stopNow()
        } else {
            sound.stopAfterCurrentLoop()
        }
    }

    @JvmStatic
    fun stopDucking(key: String) {
        if (ducks.remove(key) != null) {
            requestVolumeRefresh()
        }
    }

    fun handle(packet: PacketSoundInstanceS2C) {
        when (packet.action) {
            PacketSoundInstanceS2C.Action.PLAY -> play(packet)
            PacketSoundInstanceS2C.Action.UPDATE -> update(packet)
            PacketSoundInstanceS2C.Action.STOP -> stop(packet.key, packet.stopImmediately)
            PacketSoundInstanceS2C.Action.DUCK_START -> startOrUpdateDucking(packet)
            PacketSoundInstanceS2C.Action.DUCK_UPDATE -> startOrUpdateDucking(packet)
            PacketSoundInstanceS2C.Action.DUCK_STOP -> stopDucking(packet.key)
        }
    }

    fun tick() {
        val soundIterator = sounds.iterator()
        while (soundIterator.hasNext()) {
            val (_, sound) = soundIterator.next()
            if (sound.isStopped) {
                soundIterator.remove()
            }
        }

        val duckIterator = ducks.iterator()
        while (duckIterator.hasNext()) {
            val (_, effect) = duckIterator.next()
            effect.tick()
            if (effect.isStopped) {
                duckIterator.remove()
                requestVolumeRefresh()
            }
        }
    }

    fun clear() {
        sounds.values.forEach { it.stopNow() }
        sounds.clear()
        ducks.clear()
        requestVolumeRefresh()
    }

    @JvmStatic
    fun shouldRefreshSoundVolumes(): Boolean {
        return volumeRefreshRequested || ducks.isNotEmpty()
    }

    @JvmStatic
    fun markSoundVolumesRefreshed() {
        if (ducks.isEmpty()) {
            volumeRefreshRequested = false
        }
    }

    @JvmStatic
    fun duckVolumeMultiplier(sound: SoundInstance): Float {
        if (ducks.isEmpty()) {
            return 1f
        }
        val playerPos = Minecraft.getInstance().player?.position() ?: return 1f
        var multiplier = 1f
        ducks.values.forEach { duck ->
            if (!duck.isStopped && !duck.isWhitelisted(sound)) {
                multiplier = min(multiplier, duck.multiplierFor(playerPos))
            }
        }
        return multiplier
    }

    private fun play(packet: PacketSoundInstanceS2C) {
        play(
            key = packet.key,
            soundId = packet.sound,
            source = packet.source,
            entityId = packet.entityId,
            pos = packet.pos,
            volume = packet.volume,
            pitch = packet.pitch,
            looping = packet.looping,
            relative = packet.relative
        )
    }

    private fun update(packet: PacketSoundInstanceS2C) {
        sounds[packet.key]?.update(
            packet.entityId,
            packet.pos,
            packet.volume,
            packet.pitch,
            packet.looping,
            packet.relative
        )
    }

    private fun startOrUpdateDucking(packet: PacketSoundInstanceS2C) {
        startDucking(
            key = packet.key,
            entityId = packet.entityId,
            pos = packet.pos,
            volumeMultiplier = packet.duckVolume,
            range = packet.duckRange,
            whitelistSounds = packet.whitelistSounds,
            whitelistSources = packet.whitelistSources,
            whitelistKeys = packet.whitelistKeys
        )
    }

    private fun requestVolumeRefresh() {
        volumeRefreshRequested = true
    }
}
