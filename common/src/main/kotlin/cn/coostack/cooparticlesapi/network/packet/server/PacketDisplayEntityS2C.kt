package cn.coostack.cooparticlesapi.network.packet.server

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import java.util.UUID


class PacketDisplayEntityS2C(val uuid: UUID, val type: String, val data: ByteArray) : CustomPacketPayload {
    companion object {
        private val identifierID = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "display_entity")
        val payloadID = CustomPacketPayload.Type<PacketDisplayEntityS2C>(identifierID)
        val CODEC: StreamCodec<FriendlyByteBuf, PacketDisplayEntityS2C> =
            CustomPacketPayload.codec({ packet, buf ->
                buf.writeUtf(packet.type)
                buf.writeUUID(packet.uuid)
                buf.writeInt(packet.data.size)
                buf.writeBytes(packet.data)
            }, { buf ->
                val type = buf.readUtf()
                val uuid = buf.readUUID()
                val size = buf.readInt()
                val copy = buf.readBytes(size).copy()
                val data = ByteArray(size).apply {
                    copy.readBytes(this)
                }
                PacketDisplayEntityS2C(uuid, type, data)
            })
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
        return payloadID
    }
}