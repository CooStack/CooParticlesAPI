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
        // 优化新建
        var new: ParticleComposition? = null
        val old = ParticleCompositionManager.clientView[payload.uuid]
        if (!distanceRemove) {
            // 新建 如果old为null 则直接作为新的添加在里面
            new = decodeData(payload)
            new.world = context.player().level()
        }
        if (old == null && !distanceRemove) {
            ParticleCompositionManager.addClient(new!!)
            // fix 当composition消散的时候 会抛出npe的问题
            return
        }
        // new == null时 distanceRemove应该为true
        if (distanceRemove) {
            old?.remove()
        } else {
            // new 不是null 且 old 不为null 更新old
            old!!.update(new!!)
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
