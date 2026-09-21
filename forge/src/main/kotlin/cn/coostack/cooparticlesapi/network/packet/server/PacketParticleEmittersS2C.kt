package cn.coostack.cooparticlesapi.network.packet.server

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.network.PacketByteBuf
import net.minecraft.resources.ResourceLocation
import java.util.UUID

class PacketParticleEmittersS2C(
    val emitterID: String,
    val emitterUUID: UUID,
    val emitterData: ByteArray,
    val type: PacketType
) {
    enum class PacketType(val id: Int) {
        CREATE(0),
        REMOVE(1),
        CHANGE(2);

        companion object {
            @JvmStatic
            fun fromID(id: Int): PacketType {
                return when (id) {
                    0 -> CREATE
                    1 -> REMOVE
                    2 -> CHANGE
                    else -> CREATE
                }
            }
        }
    }

    companion object {
        private val id =
            ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "particle_emitters")
        val payloadID = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "particle_emitters")

        val CODEC = ForgeStreamCodec.of({ buf, packet ->
            val emitterID = packet.emitterID
            buf.writeInt(packet.type.id)
            buf.writeUtf(emitterID)
            buf.writeUUID(packet.emitterUUID)
            buf.writeInt(packet.emitterData.size)
            buf.writeBytes(packet.emitterData)
        }, { buf ->
            val packetTypeID = buf.readInt()
            val emitterID = buf.readUtf()
            val emitterUUID = buf.readUUID()
            val size = buf.readInt()
            PacketParticleEmittersS2C(
                emitterID,
                emitterUUID,
                ByteArray(size).also { buf.readBytes(it) },
                PacketType.fromID(packetTypeID)
            )
        })
    }
}
