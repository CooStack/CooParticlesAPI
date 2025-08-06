package cn.coostack.cooparticlesapi.network.packet.client.listener

import cn.coostack.cooparticlesapi.network.packet.PacketCameraShakeS2C
import cn.coostack.cooparticlesapi.network.packet.PacketRenderEntityS2C
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderEntityManager
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.network.PacketByteBuf

object ClientRenderEntityPacketHandler : ClientPlayNetworking.PlayPayloadHandler<PacketRenderEntityS2C> {
    override fun receive(
        payload: PacketRenderEntityS2C,
        context: ClientPlayNetworking.Context
    ) {
        val method = payload.method
        val data = payload.entityData
        val id = payload.id
        val buf = PacketByteBuf(data)
        val codec = ClientRenderEntityManager.getCodecFromID(id) ?: return
        val entity = codec.decode(buf)
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