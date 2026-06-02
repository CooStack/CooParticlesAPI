package cn.coostack.cooparticlesapi.network.packet.client.listener

import cn.coostack.cooparticlesapi.display.DisplayEntity
import cn.coostack.cooparticlesapi.display.DisplayEntityManager
import cn.coostack.cooparticlesapi.network.packet.server.PacketDisplayEntityS2C
import cn.coostack.cooparticlesapi.platform.network.ClientContext
import io.netty.buffer.Unpooled
import net.minecraft.client.Minecraft
import net.minecraft.network.RegistryFriendlyByteBuf

object ClientDisplayEntityPacketHandler {
    fun receive(
        payload: PacketDisplayEntityS2C,
        context: ClientContext
    ) {
        if (payload.removed) {
            DisplayEntityManager.clientView.remove(payload.uuid)
            return
        }
        val new = decodeData(payload)
        new.world = context.player().level()
        val old = DisplayEntityManager.clientView[payload.uuid] ?: let {
            // 新建
            DisplayEntityManager.addClient(new)
            return
        }
        // 更新
        old.update(new)
    }

    private fun decodeData(payload: PacketDisplayEntityS2C): DisplayEntity {
        val data = payload.data
        val type = payload.type
        val codec = DisplayEntityManager.registeredTypes[type]!!
        val new = codec.decode(
            RegistryFriendlyByteBuf(
                Unpooled.wrappedBuffer(data),
                Minecraft.getInstance().player!!.registryAccess()
            )
        )
        return new
    }

}
