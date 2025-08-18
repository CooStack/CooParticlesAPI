package cn.coostack.cooparticlesapi.network.packet.client.listener

import cn.coostack.cooparticlesapi.network.packet.PacketParticleEmittersS2C
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.platform.network.ClientContext

object ClientParticleEmittersPacketHandler {
    fun receive(
        payload: PacketParticleEmittersS2C,
        context: ClientContext
    ) {
        when (payload.type) {
            PacketParticleEmittersS2C.PacketType.CHANGE_OR_CREATE -> handleChangeOrCreate(payload, context)
            PacketParticleEmittersS2C.PacketType.REMOVE -> handleRemove(payload)
        }

    }

    fun handleChangeOrCreate(payload: PacketParticleEmittersS2C, context: ClientContext) {
        val codec = ParticleEmittersManager.getCodecFromID(payload.emitterID) ?: return
        val emitter = codec.decode(payload.emitterBuf)
        ParticleEmittersManager.createOrChangeClient(emitter, context.player().level())
    }

    fun handleRemove(payload: PacketParticleEmittersS2C) {
        val codec = ParticleEmittersManager.getCodecFromID(payload.emitterID) ?: return
        val emitter = codec.decode(payload.emitterBuf)
        ParticleEmittersManager.clientEmitters[emitter.uuid]?.cancelled = true
    }
}