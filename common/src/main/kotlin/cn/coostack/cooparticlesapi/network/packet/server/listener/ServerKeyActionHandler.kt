package cn.coostack.cooparticlesapi.network.packet.server.listener

import cn.coostack.cooparticlesapi.event.CooEventBus
import cn.coostack.cooparticlesapi.event.events.key.KeyActionEvent
import cn.coostack.cooparticlesapi.network.packet.client.PacketKeyActionC2S
import cn.coostack.cooparticlesapi.platform.network.ServerContext

object ServerKeyActionHandler {
    fun receive(packet: PacketKeyActionC2S, context: ServerContext) {
        val player = context.player()
        context.server().execute {
            CooEventBus.call(
                KeyActionEvent(player, packet.keyId, packet.action, packet.pressTick, packet.isRelease, true)
            )
        }
    }
}
