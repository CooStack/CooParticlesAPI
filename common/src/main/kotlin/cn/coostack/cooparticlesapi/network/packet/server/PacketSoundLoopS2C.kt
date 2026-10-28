package cn.coostack.cooparticlesapi.network.packet.server

import cn.coostack.cooparticlesapi.network.readVec3
import cn.coostack.cooparticlesapi.network.writeVec3
import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec3

class PacketSoundLoopS2C(
    val key: String,
    val sound: Identifier,
    val source: SoundSource,
    val entityId: Int,
    val pos: Vec3,
    val volume: Float,
    val pitch: Float,
    val start: Boolean,
    val stopImmediately: Boolean
) : CustomPacketPayload {
    companion object {
        private val identifierID = Identifier.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "sound_loop")
        val payloadID = CustomPacketPayload.Type<PacketSoundLoopS2C>(identifierID)

        val CODEC: StreamCodec<FriendlyByteBuf, PacketSoundLoopS2C> = CustomPacketPayload.codec(
            { packet, buf ->
                buf.writeUtf(packet.key)
                buf.writeIdentifier(packet.sound)
                buf.writeEnum(packet.source)
                buf.writeInt(packet.entityId)
                buf.writeVec3(packet.pos)
                buf.writeFloat(packet.volume)
                buf.writeFloat(packet.pitch)
                buf.writeBoolean(packet.start)
                buf.writeBoolean(packet.stopImmediately)
            },
            { buf ->
                PacketSoundLoopS2C(
                    key = buf.readUtf(),
                    sound = buf.readIdentifier(),
                    source = buf.readEnum(SoundSource::class.java),
                    entityId = buf.readInt(),
                    pos = buf.readVec3(),
                    volume = buf.readFloat(),
                    pitch = buf.readFloat(),
                    start = buf.readBoolean(),
                    stopImmediately = buf.readBoolean()
                )
            }
        )

        fun start(
            key: String,
            sound: Identifier,
            source: SoundSource,
            entityId: Int,
            pos: Vec3,
            volume: Float,
            pitch: Float
        ): PacketSoundLoopS2C {
            return PacketSoundLoopS2C(key, sound, source, entityId, pos, volume, pitch, true, true)
        }

        fun stop(key: String, interrupt: Boolean = true): PacketSoundLoopS2C {
            return PacketSoundLoopS2C(
                key = key,
                sound = Identifier.withDefaultNamespace("empty"),
                source = SoundSource.MASTER,
                entityId = -1,
                pos = Vec3.ZERO,
                volume = 0F,
                pitch = 1F,
                start = false,
                stopImmediately = interrupt
            )
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
        return payloadID
    }
}
