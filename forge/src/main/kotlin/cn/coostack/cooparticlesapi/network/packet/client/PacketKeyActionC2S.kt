package cn.coostack.cooparticlesapi.network.packet.client

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.event.events.key.KeyActionBatch
import cn.coostack.cooparticlesapi.event.events.key.KeyActionData
import cn.coostack.cooparticlesapi.event.events.key.KeyActionType
import net.minecraft.network.PacketByteBuf
import net.minecraft.resources.ResourceLocation

class PacketKeyActionC2S(
    val keyActions: KeyActionBatch<ResourceLocation>
) {
    companion object {
        private val identifierID =
            ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "key_action")
        val payloadID = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "key_action")
        val CODEC = ForgeStreamCodec.of({ packet, buf ->
            val entries = packet.keyActions.entries
            buf.writeVarInt(entries.size)
            entries.forEach { entry ->
                buf.writeResourceLocation(entry.keyId)
                buf.writeVarInt(entry.actions.size)
                entry.actions.forEach { action ->
                    buf.writeInt(action.id)
                }
                buf.writeInt(entry.pressTick)
                buf.writeBoolean(entry.released)
            }
        }, { buf ->
            val entryCount = buf.readVarInt()
            val entries = ArrayList<KeyActionData<ResourceLocation>>(entryCount)
            repeat(entryCount) {
                val keyId = buf.readResourceLocation()
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
}
