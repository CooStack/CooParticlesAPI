package cn.coostack.cooparticlesapi.network.packet.client

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.event.events.key.KeyActionType
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

/**
 * 客户端按键动作触发时发送给服务器
 *
 * @param pressTick 按键已按住的 tick 数(长按时每 tick 递增)
 * @param isRelease 是否为松开按键时的触发
 */
class PacketKeyActionC2S(
    val keyId: ResourceLocation,
    val action: KeyActionType,
    val pressTick: Int,
    val isRelease: Boolean
) : CustomPacketPayload {
    companion object {
        private val identifierID =
            ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "key_action")
        val payloadID = CustomPacketPayload.Type<PacketKeyActionC2S>(identifierID)
        val CODEC: StreamCodec<FriendlyByteBuf, PacketKeyActionC2S> =
            CustomPacketPayload.codec({ packet, buf ->
                buf.writeResourceLocation(packet.keyId)
                buf.writeInt(packet.action.id)
                buf.writeInt(packet.pressTick)
                buf.writeBoolean(packet.isRelease)
            }, { buf ->
                val keyId = buf.readResourceLocation()
                val action = KeyActionType.Companion.fromId(buf.readInt())
                val pressTick = buf.readInt()
                val isRelease = buf.readBoolean()
                PacketKeyActionC2S(keyId, action, pressTick, isRelease)
            })
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
        return payloadID
    }
}