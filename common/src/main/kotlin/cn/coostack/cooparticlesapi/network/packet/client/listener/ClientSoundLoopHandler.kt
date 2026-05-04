package cn.coostack.cooparticlesapi.network.packet.client.listener

import cn.coostack.cooparticlesapi.network.packet.server.PacketSoundLoopS2C
import cn.coostack.cooparticlesapi.platform.network.ClientContext
import cn.coostack.cooparticlesapi.sound.ClientSoundLoopManager

object ClientSoundLoopHandler {
    fun receive(payload: PacketSoundLoopS2C, context: ClientContext) {
        ClientSoundLoopManager.handle(payload)
    }
}
