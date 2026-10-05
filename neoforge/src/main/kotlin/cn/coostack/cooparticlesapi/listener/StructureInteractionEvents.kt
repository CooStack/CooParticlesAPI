package cn.coostack.cooparticlesapi.listener

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.event.CooEventBus
import cn.coostack.cooparticlesapi.event.events.entity.player.PlayerUseBlockEvent
import cn.coostack.cooparticlesapi.event.events.entity.player.PlayerUseEntityEvent
import net.minecraft.world.InteractionResult
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

/** NeoForge 游戏总线转发；只在公共事件已接管时取消原交互。 */
@EventBusSubscriber(modid = CooParticlesConstants.MOD_ID)
object StructureInteractionEvents {
    /** 方块交互取消同时阻止其物品后续使用。 */
    @SubscribeEvent
    @JvmStatic
    fun block(event: PlayerInteractEvent.RightClickBlock) {
        val result = CooEventBus.call(PlayerUseBlockEvent(event.entity, event.hand, event.hitVec)).result
        if (result != InteractionResult.PASS) {
            event.cancellationResult = result
            event.isCanceled = true
        }
    }

    /** 普通实体交互入口。 */
    @SubscribeEvent
    @JvmStatic
    fun entity(event: PlayerInteractEvent.EntityInteract) {
        val result = CooEventBus.call(PlayerUseEntityEvent(event.entity, event.hand, event.target)).result
        if (result != InteractionResult.PASS) {
            event.cancellationResult = result
            event.isCanceled = true
        }
    }

    /** 命中实体局部位置时也须拦截，成功后不会回落到普通实体交互。 */
    @SubscribeEvent
    @JvmStatic
    fun entityAt(event: PlayerInteractEvent.EntityInteractSpecific) {
        val result = CooEventBus.call(PlayerUseEntityEvent(event.entity, event.hand, event.target)).result
        if (result != InteractionResult.PASS) {
            event.cancellationResult = result
            event.isCanceled = true
        }
    }
}
