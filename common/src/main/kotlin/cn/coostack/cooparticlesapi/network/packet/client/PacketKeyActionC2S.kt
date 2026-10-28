package cn.coostack.cooparticlesapi.network.packet.client

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.event.events.key.KeyActionBatch
import cn.coostack.cooparticlesapi.event.events.key.KeyActionData
import cn.coostack.cooparticlesapi.event.events.key.KeyActionType
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

/**
 * 客户端按键动作触发时发送给服务器
 */
class PacketKeyActionC2S(
    val keyActions: KeyActionBatch<Identifier>
) : CustomPacketPayload {
    companion object {
        private val identifierID =
            Identifier.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "key_action")
        val payloadID = CustomPacketPayload.Type<PacketKeyActionC2S>(identifierID)
        val CODEC: StreamCodec<FriendlyByteBuf, PacketKeyActionC2S> =
            CustomPacketPayload.codec({ packet, buf ->
                val entries = packet.keyActions.entries
                buf.writeVarInt(entries.size)
                entries.forEach { entry ->
                    buf.writeIdentifier(entry.keyId)
                    buf.writeVarInt(entry.actions.size)
                    entry.actions.forEach { action ->
                        buf.writeInt(action.id)
                    }
                    buf.writeInt(entry.pressTick)
                    buf.writeBoolean(entry.released)
                }
            }, { buf ->
                val entryCount = buf.readVarInt()
                val entries = ArrayList<KeyActionData<Identifier>>(entryCount)
                repeat(entryCount) {
                    val keyId = buf.readIdentifier()
                    val actionCount = buf.readVarInt()
                    val actions = ArrayList<KeyActionType>(actionCount)
                    repeat(actionCount) {
                        actions.add(KeyActionType.fromId(buf.readInt()))
                    }
                    val pressTick = buf.readInt()
                    val released = buf.readBoolean()
                    entries.add(KeyActionData(keyId, actions, pressTick, released))
                }
                PacketKeyActionC2S(KeyActionBatch(entries))
            })
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
        return payloadID
    }
}
