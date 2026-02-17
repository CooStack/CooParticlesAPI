package cn.coostack.cooparticlesapi.network.packet.client.listener

import cn.coostack.cooparticlesapi.key.CooKeyBindingManager
import cn.coostack.cooparticlesapi.network.packet.server.PacketKeyBindingCountdownS2C
import cn.coostack.cooparticlesapi.platform.network.ClientContext

object ClientKeyBindingCountdownHandler {
    fun receive(
        payload: PacketKeyBindingCountdownS2C,
        context: ClientContext
    ) {
        CooKeyBindingManager.setCountdown(payload.key, payload.cd)
    }

}