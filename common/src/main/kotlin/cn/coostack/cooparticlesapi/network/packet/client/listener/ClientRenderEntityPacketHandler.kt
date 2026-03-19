package cn.coostack.cooparticlesapi.network.packet.client.listener

import cn.coostack.cooparticlesapi.network.packet.server.PacketRenderEntityS2C
import cn.coostack.cooparticlesapi.platform.network.ClientContext
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderEntityManager
import cn.coostack.cooparticlesapi.renderer.runtime.ClientRenderEntityRegistry
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import io.netty.buffer.Unpooled
import net.minecraft.network.FriendlyByteBuf

object ClientRenderEntityPacketHandler {
    fun receive(
        packet: PacketRenderEntityS2C,
        context: ClientContext
    ) {
        val method = packet.method
        val data = packet.entityData
        val id = packet.id
        val buf = FriendlyByteBuf(Unpooled.wrappedBuffer(data))
        val type = ClientRenderEntityRegistry.get(id) ?: return
        val entity = type.codec.decode(buf)
        context.client().execute {
            entity.world = context.client().level
            when (method) {
                PacketRenderEntityS2C.Method.CREATE -> {
                    val renderer = resolveRenderer(entity, type, id)
                    val instance = RenderEntityInstance(entity, renderer)
                    ClientRenderEntityManager.add(instance)
                }

                PacketRenderEntityS2C.Method.TOGGLE -> {
                    ClientRenderEntityManager.getFrom(packet.uuid)?.updateFrom(entity)
                }

                PacketRenderEntityS2C.Method.REMOVE -> {
                    ClientRenderEntityManager.getFrom(packet.uuid)?.markRemoved()
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolveRenderer(
        entity: RenderEntity,
        type: cn.coostack.cooparticlesapi.renderer.runtime.ClientRenderEntityType,
        id: net.minecraft.resources.ResourceLocation
    ): RenderEntityRenderer<RenderEntity> {
        if (entity is RenderEntityRenderer<*>) {
            return entity as RenderEntityRenderer<RenderEntity>
        }
        val factory = type.rendererFactory
            ?: throw IllegalStateException("RenderEntity renderer not registered: $id")
        return factory.invoke() as RenderEntityRenderer<RenderEntity>
    }
}
