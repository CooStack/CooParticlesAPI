package cn.coostack.cooparticlesapi.network.packet.client.listener

import cn.coostack.cooparticlesapi.network.packet.server.PacketParticleEmittersS2C
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.platform.network.ClientContext
import io.netty.buffer.Unpooled
import net.minecraft.client.Minecraft
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf

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
        val emitterID = payload.emitterID
        val codec = ParticleEmittersManager.getCodecFromID(emitterID) ?: return
        val data = payload.emitterData
        val emitter = codec.decode(
            RegistryFriendlyByteBuf(
                Unpooled.wrappedBuffer(data),
                Minecraft.getInstance().level!!.registryAccess()
            )
        )
        ParticleEmittersManager.createOrChangeClient(emitter, context.player().level())
    }

    fun handleRemove(payload: PacketParticleEmittersS2C) {
        val emitterID = payload.emitterID
        val codec = ParticleEmittersManager.getCodecFromID(emitterID) ?: return
        val data = payload.emitterData
        val emitter = codec.decode(
            RegistryFriendlyByteBuf(
                Unpooled.wrappedBuffer(data),
                Minecraft.getInstance().player!!.registryAccess()
            )
        )
        ParticleEmittersManager.clientEmitters[emitter.uuid]?.cancelled = true
    }
}