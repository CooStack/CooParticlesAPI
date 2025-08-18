package cn.coostack.cooparticlesapi.network.packet

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import java.util.UUID

class PacketParticleEmittersS2C(
    val emitterBuf: FriendlyByteBuf,
    val emitterID: String,
    val type: PacketType
) :
    CustomPacketPayload {
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
        private val identifierID =
            ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "particle_emitters")
        val payloadID = CustomPacketPayload.Type<PacketParticleEmittersS2C>(identifierID)
        val CODEC: StreamCodec<FriendlyByteBuf, PacketParticleEmittersS2C> =
            CustomPacketPayload.codec({ packet, buf ->
                buf.writeInt(packet.type.id)
                buf.writeUtf(packet.emitterID)
                buf.writeBytes(packet.emitterBuf.copy().array())
            }, { buf ->
                val packetTypeID = buf.readInt()
                val emitterID = buf.readUtf()
                val emitterBuf = buf.readBytes(buf.readableBytes())
                PacketParticleEmittersS2C(FriendlyByteBuf(emitterBuf), emitterID, PacketType.fromID(packetTypeID))
            })
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return payloadID
    }
}