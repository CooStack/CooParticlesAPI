package cn.coostack.cooparticlesapi.listener

import cn.coostack.cooparticlesapi.event.CooEventBus
import cn.coostack.cooparticlesapi.event.events.entity.player.PlayerUseBlockEvent
import cn.coostack.cooparticlesapi.event.events.entity.player.PlayerUseEntityEvent
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback

/** Fabric 原生交互转发到公共事件，两端均由 Fabric 控制调用线程。 */
object StructureInteractionEvents {
    /** 仅在模组公共初始化调用一次。 */
    fun register() {
        UseBlockCallback.EVENT.register { player, _, hand, hit ->
            CooEventBus.call(PlayerUseBlockEvent(player, hand, hit)).result
        }
        UseEntityCallback.EVENT.register { player, _, hand, entity, _ ->
            CooEventBus.call(PlayerUseEntityEvent(player, hand, entity)).result
        }
    }
}
