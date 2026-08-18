package cn.coostack.cooparticlesapi.network.packet.client.listener

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.coofx.server.CooFxSceneRenderEntity
import cn.coostack.cooparticlesapi.network.packet.server.PacketRenderEntityS2C
import cn.coostack.cooparticlesapi.coofx.client.CooFxSceneClientRegistry
import cn.coostack.cooparticlesapi.platform.network.ClientContext
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderEntityManager
import cn.coostack.cooparticlesapi.renderer.runtime.ClientRenderEntityRegistry
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import io.netty.buffer.Unpooled
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/** 把服务端 RenderEntity 同步包应用到客户端实体运行时。 */
object ClientRenderEntityPacketHandler {
    /**
     * 解码同步数据，并在客户端线程执行创建、更新或移除操作。
     *
     * @param packet 服务端发送的 RenderEntity 操作和序列化数据
     * @param context 提供客户端线程与当前客户端实例的网络上下文
     */
    fun receive(
        packet: PacketRenderEntityS2C,
        context: ClientContext
    ) {
        val method = packet.method
        val data = packet.entityData
        val id = packet.id
        val buf = FriendlyByteBuf(Unpooled.wrappedBuffer(data))
        val type = ClientRenderEntityRegistry.get(id)
        if (type == null) {
            CooParticlesConstants.logger.error(
                "[CooFX-CREATE] 客户端 RenderEntity 类型未注册：method=$method, packetUuid=${packet.uuid}, typeId=$id",
            )
            return
        }
        val entity = type.codec.decode(buf)
        context.client().execute {
            entity.world = context.client().level
            when (method) {
                PacketRenderEntityS2C.Method.CREATE -> {
                    val renderer = resolveRenderer(entity, id)
                    val instance = RenderEntityInstance(entity, renderer)
                    ClientRenderEntityManager.add(instance)
                    (entity as? CooFxSceneRenderEntity)?.let { created ->
                        CooFxSceneClientRegistry.onCreated(created)
                    }
                }

                PacketRenderEntityS2C.Method.TOGGLE -> {
                    val instance = ClientRenderEntityManager.getFrom(packet.uuid)
                    instance?.updateFrom(entity)
                    (instance?.entity as? CooFxSceneRenderEntity)?.let { updated ->
                        CooFxSceneClientRegistry.onUpdated(updated)
                    }
                }

                PacketRenderEntityS2C.Method.REMOVE -> {
                    ClientRenderEntityManager.getFrom(packet.uuid)?.markRemoved()
                    CooFxSceneClientRegistry.onRemoved(packet.uuid)
                }
            }
        }
    }

    /**
     * 解析实体使用的 renderer。旧式实体自带 renderer 时保留实例行为，V2 类型从注册表取共享实例。
     *
     * @param entity 已解码的客户端实体
     * @param id 实体类型注册 id
     * @return 可渲染当前实体的 renderer
     * @throws IllegalStateException 类型没有注册 renderer 时抛出
     */
    @Suppress("UNCHECKED_CAST")
    private fun resolveRenderer(
        entity: RenderEntity,
        id: ResourceLocation
    ): RenderEntityRenderer<RenderEntity> {
        if (entity is RenderEntityRenderer<*>) {
            return entity as RenderEntityRenderer<RenderEntity>
        }
        val renderer = ClientRenderEntityRegistry.resolveRenderer(id)
            ?: throw IllegalStateException("RenderEntity renderer not registered: $id")
        return renderer as RenderEntityRenderer<RenderEntity>
    }
}
