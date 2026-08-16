package cn.coostack.cooparticlesapi.network.packet.client.listener

import cn.coostack.cooparticlesapi.network.packet.server.PacketParticleCompositionStateS2C
import cn.coostack.cooparticlesapi.network.particle.composition.manager.ParticleCompositionManager
import cn.coostack.cooparticlesapi.platform.network.ClientContext

object ClientParticleCompositionStateHandler {
    fun receive(payload: PacketParticleCompositionStateS2C, context: ClientContext) {
        context.client().execute {
            ParticleCompositionManager.clientView[payload.uuid]?.applyRemoteState(
                payload.position,
                payload.visibleRange,
                payload.scale,
                payload.displayStatus,
                payload.closedInterval,
                payload.current,
            )
        }
    }
}
