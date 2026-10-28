package cn.coostack.cooparticlesapi.network.packet.server

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

class PacketKeyBindingCountdownS2C(val key: Identifier, val cd: Int) : CustomPacketPayload {
    companion object {
        private val identifierID =
            Identifier.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "key_binding_countdown")
        val payloadID = CustomPacketPayload.Type<PacketKeyBindingCountdownS2C>(identifierID)
        val CODEC: StreamCodec<FriendlyByteBuf, PacketKeyBindingCountdownS2C> =
            CustomPacketPayload.codec({ packet, buf ->
                buf.writeIdentifier(packet.key)
                buf.writeInt(packet.cd)
            }, { buf ->
                PacketKeyBindingCountdownS2C(buf.readIdentifier(), buf.readInt())
            })
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
        return payloadID
    }
}