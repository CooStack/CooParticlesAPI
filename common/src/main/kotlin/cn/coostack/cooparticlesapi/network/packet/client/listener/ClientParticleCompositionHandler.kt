package cn.coostack.cooparticlesapi.network.packet.client.listener

import cn.coostack.cooparticlesapi.display.DisplayEntity
import cn.coostack.cooparticlesapi.display.DisplayEntityManager
import cn.coostack.cooparticlesapi.network.packet.PacketDisplayEntityS2C
import cn.coostack.cooparticlesapi.network.packet.PacketParticleCompositionS2C
import cn.coostack.cooparticlesapi.network.particle.composition.ParticleComposition
import cn.coostack.cooparticlesapi.network.particle.composition.manager.ParticleCompositionManager
import cn.coostack.cooparticlesapi.platform.network.ClientContext
import io.netty.buffer.Unpooled
import net.minecraft.network.FriendlyByteBuf

object ClientParticleCompositionHandler {
    fun receive(
        payload: PacketParticleCompositionS2C,
        context: ClientContext
    ) {
        val new = decodeData(payload)
        new.world = context.player().level()
        val old = ParticleCompositionManager.clientView[payload.uuid] ?: let {
            // 新建
            ParticleCompositionManager.addClient(new)
            return
        }
        // 更新
        old.update(new)
    }

    private fun decodeData(payload: PacketParticleCompositionS2C): ParticleComposition {
        val data = payload.data
        val type = payload.type
        val codec = ParticleCompositionManager.registeredTypes[type]!!
        val new = codec.decode(FriendlyByteBuf(Unpooled.wrappedBuffer(data)))
        return new
    }

}
