package cn.coostack.cooparticlesapi.network.packet.server

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.network.PacketByteBuf
import net.minecraft.resources.ResourceLocation
import java.util.UUID

class PacketRenderEntityS2C(var uuid: UUID, var entityData: ByteArray, var id: ResourceLocation, var method: Method) {
    enum class Method(val id: Int) {
        CREATE(0),
        TOGGLE(1),
        REMOVE(2);

        companion object {
            fun idOf(id: Int): Method {
                return when (id) {
                    CREATE.id -> CREATE
                    TOGGLE.id -> TOGGLE
                    REMOVE.id -> REMOVE
                    else -> CREATE
                }
            }
        }
    }

    companion object {
        private val identifierID =
            ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "renderer_entity_packet")
        val payloadID = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "renderer_entity_packet")

        val CODEC = ForgeStreamCodec.of({ packet, buf ->
            val entity = packet.entityData
            buf.writeInt(packet.method.id)
            buf.writeUUID(packet.uuid)
            buf.writeResourceLocation(packet.id)
            buf.writeInt(entity.size)
            buf.writeBytes(entity)
        }, { buf ->
            val method = buf.readInt()
            val uuid = buf.readUUID()
            val id = buf.readResourceLocation()
            val size = buf.readInt()
            val entity = buf.readBytes(size)
            val bytes = ByteArray(size)
            entity.readBytes(bytes)
            PacketRenderEntityS2C(uuid, bytes, id, Method.idOf(method))
        })
    }
}
