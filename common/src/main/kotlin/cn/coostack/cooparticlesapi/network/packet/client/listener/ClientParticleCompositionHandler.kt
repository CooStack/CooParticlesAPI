package cn.coostack.cooparticlesapi.network.packet.client.listener

import cn.coostack.cooparticlesapi.network.packet.server.PacketParticleCompositionS2C
import cn.coostack.cooparticlesapi.network.particle.composition.ParticleComposition
import cn.coostack.cooparticlesapi.network.particle.composition.manager.ParticleCompositionManager
import cn.coostack.cooparticlesapi.platform.network.ClientContext
import io.netty.buffer.Unpooled
import net.minecraft.client.Minecraft
import net.minecraft.network.RegistryFriendlyByteBuf

object ClientParticleCompositionHandler {
    fun receive(
        payload: PacketParticleCompositionS2C,
        context: ClientContext
    ) {
        val distanceRemove = payload.distanceRemove
        val new = decodeData(payload)
        new.world = context.player().level()
        val old = ParticleCompositionManager.clientView[payload.uuid] ?: let {
            // 新建
            if (!distanceRemove) {
                ParticleCompositionManager.addClient(new)
            }
            return
        }
        // 更新
        if (!distanceRemove) {
            old.update(new)
        } else {
            old.remove()
        }
    }

    private fun decodeData(payload: PacketParticleCompositionS2C): ParticleComposition {
        val data = payload.data
        val type = payload.type
        val codec = ParticleCompositionManager.registeredTypes[type]!!
        val new = codec.decode(
            RegistryFriendlyByteBuf(
                Unpooled.wrappedBuffer(data),
                Minecraft.getInstance().player!!.registryAccess()
            )
        )
        return new
    }

}
