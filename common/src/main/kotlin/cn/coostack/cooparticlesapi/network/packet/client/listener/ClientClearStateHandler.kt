package cn.coostack.cooparticlesapi.network.packet.client.listener

import cn.coostack.cooparticlesapi.CooParticlesAPIClient
import cn.coostack.cooparticlesapi.network.packet.server.PacketClearClientStateS2C
import cn.coostack.cooparticlesapi.platform.network.ClientContext

object ClientClearStateHandler {
    fun receive(payload: PacketClearClientStateS2C, context: ClientContext) {
        context.client().execute {
            CooParticlesAPIClient.clearTransientClientState()
        }
    }
}
