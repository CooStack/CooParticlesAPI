package cn.coostack.cooparticlesapi.network.packet.client.listener

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.network.packet.server.PacketParticleCompositionS2C
import cn.coostack.cooparticlesapi.network.particle.composition.ParticleComposition
import cn.coostack.cooparticlesapi.network.particle.composition.manager.ParticleCompositionManager
import cn.coostack.cooparticlesapi.platform.network.ClientContext
import io.netty.buffer.Unpooled
import net.minecraft.client.Minecraft
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.concurrent.ConcurrentHashMap

object ClientParticleCompositionHandler {
    private val missingCodecWarnings = ConcurrentHashMap.newKeySet<String>()
    private val identityMismatchWarnings = ConcurrentHashMap.newKeySet<String>()

    fun receive(
        payload: PacketParticleCompositionS2C,
        context: ClientContext
    ) {
        context.client().execute {
            apply(payload, context)
        }
    }

    private fun apply(payload: PacketParticleCompositionS2C, context: ClientContext) {
        val distanceRemove = payload.distanceRemove
        // 优化新建
        var new: ParticleComposition? = null
        val old = ParticleCompositionManager.clientView[payload.uuid]
        if (!distanceRemove) {
            // 新建 如果old为null 则直接作为新的添加在里面
            new = decodeData(payload) ?: return
            if (new.controlUUID != payload.uuid && identityMismatchWarnings.add(payload.type)) {
                CooParticlesConstants.logger.warn(
                    "Composition 网络 UUID 不一致，已采用数据包 UUID: type={}, packet={}, decoded={}",
                    payload.type,
                    payload.uuid,
                    new.controlUUID,
                )
            }
            new.controlUUID = payload.uuid
            new.world = context.player().level()
        }
        if (old == null && !distanceRemove) {
            ParticleCompositionManager.addClient(new!!)
            // fix 当composition消散的时候 会抛出npe的问题
            return
        }
        val current = old ?: return
        // new == null时 distanceRemove应该为true
        if (distanceRemove) {
            current.remove()
        } else {
            if (payload.recreate || current.canceled || !current.displayed) {
                current.clear(true)
                ParticleCompositionManager.addClient(new!!)
                return
            }
            // new 不是null 且 old 不为null 更新old
            current.update(new!!)
        }
    }

    private fun decodeData(payload: PacketParticleCompositionS2C): ParticleComposition? {
        val data = payload.data
        val type = payload.type
        val codec = ParticleCompositionManager.registeredTypes[type] ?: run {
            if (missingCodecWarnings.add(type)) {
                CooParticlesConstants.logger.warn(
                    "收到未注册的 Composition codec: type={}, uuid={}",
                    type,
                    payload.uuid,
                )
            }
            return null
        }
        val buffer = RegistryFriendlyByteBuf(
            Unpooled.wrappedBuffer(data),
            Minecraft.getInstance().player!!.registryAccess()
        )
        return try {
            codec.decode(buffer)
        } finally {
            buffer.release()
        }
    }

}
