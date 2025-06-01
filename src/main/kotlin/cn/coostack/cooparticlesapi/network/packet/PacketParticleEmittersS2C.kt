package cn.coostack.cooparticlesapi.network.packet

import cn.coostack.cooparticlesapi.CooParticleAPI
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import java.util.UUID

class PacketParticleEmittersS2C(
    val emitterBuf: PacketByteBuf,
    val emitterID: String,
    val type: PacketType
) :
    CustomPayload {
    enum class PacketType(val id: Int) {
        CHANGE_OR_CREATE(0),
        REMOVE(1);

        companion object {
            @JvmStatic
            fun fromID(id: Int): PacketType {
                return when (id) {
                    0 -> CHANGE_OR_CREATE
                    1 -> REMOVE
                    else -> CHANGE_OR_CREATE
                }
            }
        }
    }

    companion object {
        private val identifierID = Identifier.of(CooParticleAPI.MOD_ID, "particle_emitters")
        val payloadID = CustomPayload.Id<PacketParticleEmittersS2C>(identifierID)
        private val CODEC: PacketCodec<PacketByteBuf, PacketParticleEmittersS2C> =
            CustomPayload.codecOf({ packet, buf ->
                buf.writeInt(packet.type.id)
                buf.writeString(packet.emitterID)
                buf.writeBytes(packet.emitterBuf)
            }, { buf ->
                val packetTypeID = buf.readInt()
                val emitterID = buf.readString()
                val emitterBuf = buf.readBytes(buf.readableBytes())
                PacketParticleEmittersS2C(PacketByteBuf(emitterBuf), emitterID, PacketType.fromID(packetTypeID))
            })

        fun init() {
            PayloadTypeRegistry.playS2C().register(payloadID, CODEC)
        }
    }

    override fun getId(): CustomPayload.Id<out CustomPayload> {
        return payloadID
    }
}