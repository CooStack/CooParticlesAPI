package cn.coostack.cooparticlesapi.sound

import cn.coostack.cooparticlesapi.network.packet.server.PacketSoundLoopS2C
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

object ServerSoundLoopManager {
    private val trackingKeys = HashSet<String>()

    fun key(entity: Entity, name: String): String {
        return "${entity.uuid}:$name"
    }

    @JvmStatic
    fun isTracking(key: String): Boolean {
        return synchronized(trackingKeys) {
            trackingKeys.contains(key)
        }
    }

    @JvmStatic
    fun isTrackingEntity(entity: Entity, name: String): Boolean {
        return isTracking(key(entity, name))
    }

    @JvmStatic
    fun startTrackingEntity(
        entity: Entity,
        key: String,
        sound: SoundEvent,
        source: SoundSource,
        volume: Float = 1f,
        pitch: Float = 1f,
        self: Boolean = true
    ): Boolean {
        return startTrackingEntity(entity, key, sound.location, source, volume, pitch, self)
    }

    @JvmStatic
    fun startTrackingEntity(
        entity: Entity,
        key: String,
        sound: ResourceLocation,
        source: SoundSource,
        volume: Float = 1f,
        pitch: Float = 1f,
        self: Boolean = true
    ): Boolean {
        if (!markTracking(key)) {
            return false
        }
        val packet = PacketSoundLoopS2C.start(
            key = key,
            sound = sound,
            source = source,
            entityId = entity.id,
            pos = entity.position(),
            volume = volume,
            pitch = pitch
        )
        if (!sendToEntityWatchers(entity, packet, self)) {
            unmarkTracking(key)
            return false
        }
        return true
    }

    @JvmStatic
    fun startAt(
        world: ServerLevel,
        pos: Vec3,
        key: String,
        sound: SoundEvent,
        source: SoundSource,
        volume: Float = 1f,
        pitch: Float = 1f
    ): Boolean {
        return startAt(world, pos, key, sound.location, source, volume, pitch)
    }

    @JvmStatic
    fun startAt(
        world: ServerLevel,
        pos: Vec3,
        key: String,
        sound: ResourceLocation,
        source: SoundSource,
        volume: Float = 1f,
        pitch: Float = 1f
    ): Boolean {
        if (!markTracking(key)) {
            return false
        }
        val packet = PacketSoundLoopS2C.start(
            key = key,
            sound = sound,
            source = source,
            entityId = -1,
            pos = pos,
            volume = volume,
            pitch = pitch
        )
        CooParticlesServices.SERVER_NETWORK.sendToPlayersTrackingChunk(
            world,
            world.getChunkAt(BlockPos.containing(pos)).pos,
            packet
        )
        return true
    }

    @JvmStatic
    fun stopTrackingEntity(entity: Entity, key: String, self: Boolean = true, interrupt: Boolean = true) {
        unmarkTracking(key)
        sendToEntityWatchers(entity, PacketSoundLoopS2C.stop(key, interrupt), self)
    }

    @JvmStatic
    fun stopAt(world: ServerLevel, pos: Vec3, key: String, interrupt: Boolean = true) {
        unmarkTracking(key)
        CooParticlesServices.SERVER_NETWORK.sendToPlayersTrackingChunk(
            world,
            world.getChunkAt(BlockPos.containing(pos)).pos,
            PacketSoundLoopS2C.stop(key, interrupt)
        )
    }

    @JvmStatic
    fun stop(player: ServerPlayer, key: String, interrupt: Boolean = true) {
        CooParticlesServices.SERVER_NETWORK.send(PacketSoundLoopS2C.stop(key, interrupt), player)
    }

    private fun sendToEntityWatchers(entity: Entity, packet: PacketSoundLoopS2C, self: Boolean): Boolean {
        val level = entity.level() as? ServerLevel ?: return false
        CooParticlesServices.SERVER_NETWORK.sendToPlayersTrackingChunk(level, entity.chunkPosition(), packet)
        if (self && entity is ServerPlayer) {
            CooParticlesServices.SERVER_NETWORK.send(packet, entity)
        }
        return true
    }

    private fun markTracking(key: String): Boolean {
        return synchronized(trackingKeys) {
            trackingKeys.add(key)
        }
    }

    private fun unmarkTracking(key: String) {
        synchronized(trackingKeys) {
            trackingKeys.remove(key)
        }
    }
}
