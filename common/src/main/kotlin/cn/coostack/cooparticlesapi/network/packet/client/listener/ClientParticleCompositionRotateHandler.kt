package cn.coostack.cooparticlesapi.network.packet.client.listener

import cn.coostack.cooparticlesapi.extend.asRelative
import cn.coostack.cooparticlesapi.network.packet.server.PacketParticleCompositionRotateS2C
import cn.coostack.cooparticlesapi.network.particle.composition.manager.ParticleCompositionManager
import cn.coostack.cooparticlesapi.platform.network.ClientContext

object ClientParticleCompositionRotateHandler {
    fun receive(
        payload: PacketParticleCompositionRotateS2C,
        context: ClientContext
    ) {
        context.client().execute {
            val composition = ParticleCompositionManager.clientView[payload.uuid] ?: return@execute
            composition.applyRemoteRotation(payload.direction?.asRelative(), payload.rollDelta)
        }
    }
}
