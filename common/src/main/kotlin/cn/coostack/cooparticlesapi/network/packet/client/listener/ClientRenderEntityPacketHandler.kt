package cn.coostack.cooparticlesapi.network.packet.client.listener

import cn.coostack.cooparticlesapi.network.packet.PacketRenderEntityS2C
import cn.coostack.cooparticlesapi.platform.network.ClientContext
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderEntityManager
import net.minecraft.network.FriendlyByteBuf

object ClientRenderEntityPacketHandler {
    fun receive(
        payload: PacketRenderEntityS2C,
        context: ClientContext
    ) {
        val method = payload.method
        val data = payload.entityData
        val id = payload.id
        val buf = FriendlyByteBuf(data)
        val codec = ClientRenderEntityManager.getCodecFromID(id) ?: return
        val entity = codec.decode(buf)
        entity.world = context.client().level
        when (method) {
            PacketRenderEntityS2C.Method.CREATE -> {
                ClientRenderEntityManager.add(entity)
            }

            PacketRenderEntityS2C.Method.TOGGLE -> {
                ClientRenderEntityManager.getFrom(payload.uuid)?.loadProfileFromEntity(entity)
            }

            PacketRenderEntityS2C.Method.REMOVE -> {
                ClientRenderEntityManager.getFrom(payload.uuid)?.canceled = true
            }
        }
    }
}