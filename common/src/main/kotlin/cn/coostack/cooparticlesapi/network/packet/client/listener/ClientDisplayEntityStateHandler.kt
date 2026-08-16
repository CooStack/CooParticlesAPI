package cn.coostack.cooparticlesapi.network.packet.client.listener

import cn.coostack.cooparticlesapi.display.DisplayEntityManager
import cn.coostack.cooparticlesapi.network.packet.server.PacketDisplayEntityStateS2C
import cn.coostack.cooparticlesapi.platform.network.ClientContext

object ClientDisplayEntityStateHandler {
    fun receive(payload: PacketDisplayEntityStateS2C, context: ClientContext) {
        context.client().execute {
            DisplayEntityManager.clientView[payload.uuid]?.applyRemoteState(
                payload.position,
                payload.yaw,
                payload.pitch,
                payload.roll,
                payload.scale,
            )
        }
    }
}
