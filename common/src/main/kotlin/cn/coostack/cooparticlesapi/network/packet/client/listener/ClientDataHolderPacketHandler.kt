package cn.coostack.cooparticlesapi.network.packet.client.listener

import cn.coostack.cooparticlesapi.data.holder.DataHolderManager
import cn.coostack.cooparticlesapi.network.packet.server.PacketDataHolderS2C
import cn.coostack.cooparticlesapi.platform.network.ClientContext

object ClientDataHolderPacketHandler {
    fun receive(
        payload: PacketDataHolderS2C,
        context: ClientContext
    ) {
        val entity = context.player().level().getEntity(payload.entityId) ?: return
        DataHolderManager.applyClient(entity, payload)
    }
}
