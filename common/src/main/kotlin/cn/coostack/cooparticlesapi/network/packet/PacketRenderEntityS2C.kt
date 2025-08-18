package cn.coostack.cooparticlesapi.network.packet

import cn.coostack.cooparticlesapi.CooParticlesConstants
import io.netty.buffer.ByteBuf
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import java.util.UUID

class PacketRenderEntityS2C(var uuid: UUID, var entityData: ByteBuf, var id: ResourceLocation, var method: Method) :
    CustomPacketPayload {
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
        val payloadID = CustomPacketPayload.Type<PacketRenderEntityS2C>(identifierID)

        @JvmStatic
        val CODEC: StreamCodec<FriendlyByteBuf, PacketRenderEntityS2C> =
            CustomPacketPayload.codec({ packet, buf ->
                val entity = packet.entityData.copy().array()
                buf.writeInt(packet.method.id)
                buf.writeUUID(packet.uuid)
                buf.writeResourceLocation(packet.id)
                buf.writeBytes(entity)
            }, { buf ->
                val method = buf.readInt()
                val uuid = buf.readUUID()
                val id = buf.readResourceLocation()
                val entity = buf.readBytes(buf.readableBytes())
                val packet = PacketRenderEntityS2C(uuid, entity, id, Method.idOf(method))
                return@codec packet
            }
            )

    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return payloadID
    }
}