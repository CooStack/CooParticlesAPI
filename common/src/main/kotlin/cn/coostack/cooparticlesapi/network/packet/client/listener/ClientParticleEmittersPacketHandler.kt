package cn.coostack.cooparticlesapi.network.packet.client.listener

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.network.packet.server.PacketParticleEmittersS2C
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.platform.network.ClientContext
import io.netty.buffer.Unpooled
import net.minecraft.client.Minecraft
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.concurrent.ConcurrentHashMap

object ClientParticleEmittersPacketHandler {
    private val missingCodecWarnings = ConcurrentHashMap.newKeySet<String>()
    private val identityMismatchWarnings = ConcurrentHashMap.newKeySet<String>()

    fun receive(
        payload: PacketParticleEmittersS2C,
        context: ClientContext
    ) {
        context.client().execute {
            when (payload.type) {
                PacketParticleEmittersS2C.PacketType.CREATE -> handleCreate(payload, context)
                PacketParticleEmittersS2C.PacketType.CHANGE -> handleChange(payload, context)
                PacketParticleEmittersS2C.PacketType.REMOVE -> handleRemove(payload)
            }
        }
    }

    private fun handleCreate(payload: PacketParticleEmittersS2C, context: ClientContext) {
        val emitters = decode(payload) ?: return
        ParticleEmittersManager.createClient(emitters, context.player().level())
    }

    private fun handleChange(payload: PacketParticleEmittersS2C, context: ClientContext) {
        val emitters = decode(payload) ?: return
        ParticleEmittersManager.changeClient(emitters, context.player().level())
    }

    private fun handleRemove(payload: PacketParticleEmittersS2C) {
        ParticleEmittersManager.removeClient(payload.emitterUUID)
    }

    private fun decode(payload: PacketParticleEmittersS2C): ParticleEmitters? {
        val codec = ParticleEmittersManager.getCodecFromID(payload.emitterID) ?: run {
            if (missingCodecWarnings.add(payload.emitterID)) {
                CooParticlesConstants.logger.warn(
                    "收到未注册的 Emitter codec: id={}, uuid={}",
                    payload.emitterID,
                    payload.emitterUUID,
                )
            }
            return null
        }
        val buffer = RegistryFriendlyByteBuf(
            Unpooled.wrappedBuffer(payload.emitterData),
            Minecraft.getInstance().level!!.registryAccess(),
        )
        return try {
            codec.decode(buffer).also { emitters ->
                if (emitters.uuid != payload.emitterUUID && identityMismatchWarnings.add(payload.emitterID)) {
                    CooParticlesConstants.logger.warn(
                        "Emitter 网络 UUID 不一致，已采用数据包 UUID: id={}, packet={}, decoded={}",
                        payload.emitterID,
                        payload.emitterUUID,
                        emitters.uuid,
                    )
                }
                emitters.uuid = payload.emitterUUID
            }
        } finally {
            buffer.release()
        }
    }
}
