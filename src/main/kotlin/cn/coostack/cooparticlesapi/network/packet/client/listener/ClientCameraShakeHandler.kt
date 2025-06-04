package cn.coostack.cooparticlesapi.network.packet.client.listener

import cn.coostack.cooparticlesapi.network.packet.PacketCameraShakeS2C
import cn.coostack.cooparticlesapi.utils.ClientCameraUtil
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.network.packet.CustomPayload

object ClientCameraShakeHandler : ClientPlayNetworking.PlayPayloadHandler<PacketCameraShakeS2C> {
    override fun receive(
        payload: PacketCameraShakeS2C,
        context: ClientPlayNetworking.Context
    ) {
        val range = payload.range
        val player = context.player() ?: return
        val distance = player.pos.distanceTo(payload.origin)
        if (distance > range && range > 0) {
            return
        }
        ClientCameraUtil.startShakeCamera(payload.tick, payload.amplitude)
    }
}