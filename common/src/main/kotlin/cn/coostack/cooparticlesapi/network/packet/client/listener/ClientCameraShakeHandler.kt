package cn.coostack.cooparticlesapi.network.packet.client.listener

import cn.coostack.cooparticlesapi.network.packet.server.PacketCameraShakeS2C
import cn.coostack.cooparticlesapi.platform.network.ClientContext
import cn.coostack.cooparticlesapi.utils.ClientCameraUtil
object ClientCameraShakeHandler  {
    fun receive(
        payload: PacketCameraShakeS2C,
        context: ClientContext
    ) {
        val range = payload.range
        val player = context.player()
        val distance = player.position().distanceTo(payload.origin)
        if (distance > range && range > 0) {
            return
        }
        ClientCameraUtil.startShakeCamera(payload.tick, payload.amplitude)
    }
}