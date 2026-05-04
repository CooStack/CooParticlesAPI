package cn.coostack.cooparticlesapi.network.packet.server

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec3

class PacketSoundInstanceS2C(
    val action: Action,
    val key: String,
    val sound: ResourceLocation,
    val source: SoundSource,
    val entityId: Int,
    val pos: Vec3,
    val volume: Float,
    val pitch: Float,
    val looping: Boolean,
    val relative: Boolean,
    val stopImmediately: Boolean,
    val duckVolume: Float,
    val duckRange: Double,
    val whitelistSounds: Set<ResourceLocation>,
    val whitelistSources: Set<SoundSource>,
    val whitelistKeys: Set<String>
) : CustomPacketPayload {
    enum class Action {
        PLAY,
        UPDATE,
        STOP,
        DUCK_START,
        DUCK_UPDATE,
        DUCK_STOP
    }

    companion object {
        private val identifierID = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "sound_instance")
        val payloadID = CustomPacketPayload.Type<PacketSoundInstanceS2C>(identifierID)

        private val emptySound = ResourceLocation.withDefaultNamespace("empty")

        val CODEC: StreamCodec<FriendlyByteBuf, PacketSoundInstanceS2C> = CustomPacketPayload.codec(
            { packet, buf ->
                buf.writeEnum(packet.action)
                buf.writeUtf(packet.key)
                buf.writeResourceLocation(packet.sound)
                buf.writeEnum(packet.source)
                buf.writeInt(packet.entityId)
                buf.writeVec3(packet.pos)
                buf.writeFloat(packet.volume)
                buf.writeFloat(packet.pitch)
                buf.writeBoolean(packet.looping)
                buf.writeBoolean(packet.relative)
                buf.writeBoolean(packet.stopImmediately)
                buf.writeFloat(packet.duckVolume)
                buf.writeDouble(packet.duckRange)
                writeResourceLocationSet(buf, packet.whitelistSounds)
                writeSoundSourceSet(buf, packet.whitelistSources)
                writeStringSet(buf, packet.whitelistKeys)
            },
            { buf ->
                PacketSoundInstanceS2C(
                    action = buf.readEnum(Action::class.java),
                    key = buf.readUtf(),
                    sound = buf.readResourceLocation(),
                    source = buf.readEnum(SoundSource::class.java),
                    entityId = buf.readInt(),
                    pos = buf.readVec3(),
                    volume = buf.readFloat(),
                    pitch = buf.readFloat(),
                    looping = buf.readBoolean(),
                    relative = buf.readBoolean(),
                    stopImmediately = buf.readBoolean(),
                    duckVolume = buf.readFloat(),
                    duckRange = buf.readDouble(),
                    whitelistSounds = readResourceLocationSet(buf),
                    whitelistSources = readSoundSourceSet(buf),
                    whitelistKeys = readStringSet(buf)
                )
            }
        )

        fun play(
            key: String,
            sound: ResourceLocation,
            source: SoundSource,
            entityId: Int,
            pos: Vec3,
            volume: Float,
            pitch: Float,
            looping: Boolean,
            relative: Boolean = false
        ): PacketSoundInstanceS2C {
            return PacketSoundInstanceS2C(
                Action.PLAY,
                key,
                sound,
                source,
                entityId,
                pos,
                volume,
                pitch,
                looping,
                relative,
                true,
                1f,
                -1.0,
                emptySet(),
                emptySet(),
                emptySet()
            )
        }

        fun update(
            key: String,
            entityId: Int,
            pos: Vec3,
            volume: Float,
            pitch: Float,
            looping: Boolean = false,
            relative: Boolean = false
        ): PacketSoundInstanceS2C {
            return PacketSoundInstanceS2C(
                Action.UPDATE,
                key,
                emptySound,
                SoundSource.MASTER,
                entityId,
                pos,
                volume,
                pitch,
                looping,
                relative,
                true,
                1f,
                -1.0,
                emptySet(),
                emptySet(),
                emptySet()
            )
        }

        fun stop(key: String, interrupt: Boolean = true): PacketSoundInstanceS2C {
            return PacketSoundInstanceS2C(
                Action.STOP,
                key,
                emptySound,
                SoundSource.MASTER,
                -1,
                Vec3.ZERO,
                0f,
                1f,
                false,
                false,
                interrupt,
                1f,
                -1.0,
                emptySet(),
                emptySet(),
                emptySet()
            )
        }

        fun duck(
            action: Action,
            key: String,
            entityId: Int,
            pos: Vec3,
            volumeMultiplier: Float,
            range: Double,
            whitelistSounds: Set<ResourceLocation>,
            whitelistSources: Set<SoundSource>,
            whitelistKeys: Set<String>
        ): PacketSoundInstanceS2C {
            require(action == Action.DUCK_START || action == Action.DUCK_UPDATE || action == Action.DUCK_STOP) {
                "Ducking packet action must be a ducking action."
            }
            return PacketSoundInstanceS2C(
                action,
                key,
                emptySound,
                SoundSource.MASTER,
                entityId,
                pos,
                0f,
                1f,
                false,
                false,
                true,
                volumeMultiplier,
                range,
                whitelistSounds,
                whitelistSources,
                whitelistKeys
            )
        }

        private fun writeResourceLocationSet(buf: FriendlyByteBuf, values: Set<ResourceLocation>) {
            buf.writeVarInt(values.size)
            values.forEach(buf::writeResourceLocation)
        }

        private fun readResourceLocationSet(buf: FriendlyByteBuf): Set<ResourceLocation> {
            val result = LinkedHashSet<ResourceLocation>()
            repeat(buf.readVarInt()) {
                result.add(buf.readResourceLocation())
            }
            return result
        }

        private fun writeSoundSourceSet(buf: FriendlyByteBuf, values: Set<SoundSource>) {
            buf.writeVarInt(values.size)
            values.forEach(buf::writeEnum)
        }

        private fun readSoundSourceSet(buf: FriendlyByteBuf): Set<SoundSource> {
            val result = LinkedHashSet<SoundSource>()
            repeat(buf.readVarInt()) {
                result.add(buf.readEnum(SoundSource::class.java))
            }
            return result
        }

        private fun writeStringSet(buf: FriendlyByteBuf, values: Set<String>) {
            buf.writeVarInt(values.size)
            values.forEach(buf::writeUtf)
        }

        private fun readStringSet(buf: FriendlyByteBuf): Set<String> {
            val result = LinkedHashSet<String>()
            repeat(buf.readVarInt()) {
                result.add(buf.readUtf())
            }
            return result
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
        return payloadID
    }
}
