package cn.coostack.cooparticlesapi.network.packet.client.listener

import cn.coostack.cooparticlesapi.network.packet.PacketParticleS2C
import cn.coostack.cooparticlesapi.platform.network.ClientContext

object ClientParticlePacketHandler {
    fun receive(packet: PacketParticleS2C, context: ClientContext) {
        val player = context.player()
        val world = player.level()
        world.addParticle(
            packet.type, true, packet.pos.x, packet.pos.y, packet.pos.z,
            packet.velocity.x, packet.velocity.y, packet.velocity.z,
        )
    }
}