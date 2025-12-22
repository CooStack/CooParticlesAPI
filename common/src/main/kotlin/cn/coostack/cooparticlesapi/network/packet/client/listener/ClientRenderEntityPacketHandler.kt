package cn.coostack.cooparticlesapi.network.packet.client.listener

import cn.coostack.cooparticlesapi.network.packet.PacketRenderEntityS2C
import cn.coostack.cooparticlesapi.platform.network.ClientContext
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderEntityManager
import io.netty.buffer.Unpooled
import net.minecraft.network.FriendlyByteBuf

object ClientRenderEntityPacketHandler {
    fun receive(
        packet: PacketRenderEntityS2C,
        context: ClientContext
    ) {
        val method = packet.method
        val data = packet.entityData
        val id = packet.id
        val buf = FriendlyByteBuf(Unpooled.wrappedBuffer(data))
        val codec = ClientRenderEntityManager.getCodecFromID(id) ?: return
        val entity = codec.decode(buf)
        entity.world = context.client().level
        when (method) {
            PacketRenderEntityS2C.Method.CREATE -> {
                ClientRenderEntityManager.add(entity)
            }

            PacketRenderEntityS2C.Method.TOGGLE -> {
                ClientRenderEntityManager.getFrom(packet.uuid)?.loadProfileFromEntity(entity)
            }

            PacketRenderEntityS2C.Method.REMOVE -> {
                ClientRenderEntityManager.getFrom(packet.uuid)?.canceled = true
            }
        }
    }
}