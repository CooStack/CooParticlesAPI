package cn.coostack.cooparticlesapi.listener

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.entities.CooModEntityTypes
import cn.coostack.cooparticlesapi.entities.structure.StructureModelEntity
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent

/** 在模组注册总线为结构模型提供生物属性，专用服务器无需加载客户端代码。 */
@EventBusSubscriber(modid = CooParticlesConstants.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
object StructureModelAttributes {
    /** 属性创建时登记已注册的实体类型。 */
    @SubscribeEvent
    @JvmStatic
    fun register(event: EntityAttributeCreationEvent) {
        event.put(CooModEntityTypes.STRUCTURE_MODEL.get(), StructureModelEntity.createAttributes().build())
    }
}
