package cn.coostack.cooparticlesapi.network.packet.server

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

class PacketParticleEmittersS2C(
    val emitterID: String,
    val emitterData: ByteArray,
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
        private val id =
            ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "particle_emitters")
        val payloadID = CustomPacketPayload.Type<PacketParticleEmittersS2C>(id)

        val CODEC =
            StreamCodec.of<FriendlyByteBuf, PacketParticleEmittersS2C>({ buf, packet ->
                val emitterID = packet.emitterID
                buf.writeInt(packet.type.id)
                buf.writeUtf(emitterID)
                buf.writeInt(packet.emitterData.size)
                buf.writeBytes(packet.emitterData)
            }, { buf ->
                val packetTypeID = buf.readInt()
                val emitterID = buf.readUtf()
                val size = buf.readInt()
                val data = buf.readBytes(size)
                PacketParticleEmittersS2C(
                    emitterID,
                    ByteArray(size).apply {
                        data.copy().readBytes(this)
                    },
                    PacketType.fromID(packetTypeID)
                )
            })
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return payloadID
    }
}