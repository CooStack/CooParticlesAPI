package cn.coostack.cooparticlesapi.network.packet.client.listener

import cn.coostack.cooparticlesapi.network.packet.server.PacketSoundInstanceS2C
import cn.coostack.cooparticlesapi.platform.network.ClientContext
import cn.coostack.cooparticlesapi.supports.sound.ClientSoundManager

object ClientSoundInstanceHandler {
    fun receive(payload: PacketSoundInstanceS2C, context: ClientContext) {
        ClientSoundManager.handle(payload)
    }
}
