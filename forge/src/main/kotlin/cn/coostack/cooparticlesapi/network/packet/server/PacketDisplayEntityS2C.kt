package cn.coostack.cooparticlesapi.network.packet.server

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.network.PacketByteBuf
import net.minecraft.resources.ResourceLocation
import java.util.UUID

class PacketDisplayEntityS2C(
    val uuid: UUID,
    val type: String,
    val data: ByteArray,
    val removed: Boolean = false
) {
    companion object {
        private val identifierID = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "display_entity")
        val payloadID = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "display_entity")
        val CODEC = ForgeStreamCodec.of({ packet, buf ->
            buf.writeUtf(packet.type)
            buf.writeUUID(packet.uuid)
            buf.writeBoolean(packet.removed)
            buf.writeInt(packet.data.size)
            buf.writeBytes(packet.data)
        }, { buf ->
            val type = buf.readUtf()
            val uuid = buf.readUUID()
            val removed = buf.readBoolean()
            val size = buf.readInt()
            val data = ByteArray(size).also { buf.readBytes(it) }
            PacketDisplayEntityS2C(uuid, type, data, removed)
        })
    }
}
