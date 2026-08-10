package cn.coostack.cooparticlesapi.network.packet.client.listener

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.network.packet.server.PacketRendererPostEffectS2C
import cn.coostack.cooparticlesapi.platform.network.ClientContext
import cn.coostack.cooparticlesapi.renderer.post.CooPostEffects
import cn.coostack.cooparticlesapi.renderer.post.PostEffectRuntimeRegistry

object ClientRendererPostEffectHandler {
    fun receive(packet: PacketRendererPostEffectS2C, context: ClientContext) {
        context.client().execute {
            apply(packet)
        }
    }

    internal fun apply(packet: PacketRendererPostEffectS2C) {
        when (packet.operation) {
            PacketRendererPostEffectS2C.Operation.CREATE,
            PacketRendererPostEffectS2C.Operation.UPDATE -> applyState(packet)
            PacketRendererPostEffectS2C.Operation.REMOVE -> CooPostEffects.client.remove(packet.instanceId)
        }
    }

    private fun applyState(packet: PacketRendererPostEffectS2C) {
        val state = packet.state ?: return
        if (!PostEffectRuntimeRegistry.containsType(state.effectType)) {
            CooParticlesConstants.logger.warn(
                "Skipping synced post effect id={} because type={} is not registered on the client",
                state.instanceId,
                state.effectType
            )
            return
        }
        val instance = state.instantiate(serverSynced = true) ?: return
        when (packet.operation) {
            PacketRendererPostEffectS2C.Operation.CREATE -> CooPostEffects.client.add(instance)
            PacketRendererPostEffectS2C.Operation.UPDATE -> CooPostEffects.client.update(instance)
            PacketRendererPostEffectS2C.Operation.REMOVE -> Unit
        }
    }
}
