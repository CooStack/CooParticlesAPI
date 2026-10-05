package cn.coostack.cooparticlesapi.listener.client

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.entities.CooModEntityTypes
import cn.coostack.cooparticlesapi.entities.structure.client.StructureModelRenderer
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.EntityRenderersEvent

/** 结构渲染器只在客户端模组注册总线上加载，不依赖游戏事件总线。 */
@EventBusSubscriber(modid = CooParticlesConstants.MOD_ID, value = [Dist.CLIENT], bus = EventBusSubscriber.Bus.MOD)
object StructureModelClientRegistration {
    /** 注册公共结构渲染器，使用加载器提供的上下文工厂。 */
    @SubscribeEvent
    @JvmStatic
    fun register(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerEntityRenderer(CooModEntityTypes.STRUCTURE_MODEL.get(), ::StructureModelRenderer)
    }
}
