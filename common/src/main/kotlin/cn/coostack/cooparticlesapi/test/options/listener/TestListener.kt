package cn.coostack.cooparticlesapi.test.options.listener

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.events.EventHandler
import cn.coostack.cooparticlesapi.annotations.events.EventListener
import cn.coostack.cooparticlesapi.event.api.EventPriority
import cn.coostack.cooparticlesapi.event.events.entity.EntityPrePlaceBlockEvent
import cn.coostack.cooparticlesapi.event.events.entity.player.PlayerBlockBreakEvent
import cn.coostack.cooparticlesapi.event.events.entity.player.PlayerItemDestroyEvent
import cn.coostack.cooparticlesapi.event.events.entity.player.PlayerLoggedInEvent
import cn.coostack.cooparticlesapi.event.events.entity.player.ServerPlayerDeathEvent
import cn.coostack.cooparticlesapi.event.events.entity.player.ServerPlayerRespawnEvent
import cn.coostack.cooparticlesapi.test.options.event.TestChildEvent
import cn.coostack.cooparticlesapi.test.options.event.TestEvent
import net.minecraft.network.chat.Component

@EventListener(CooParticlesConstants.MOD_ID)
class TestListener {

    @EventHandler
    fun onTestEvent(event: TestEvent) {
        val player = event.player
        player.sendSystemMessage(Component.literal("父类 event 执行"))
    }

    @EventHandler
    fun onTestChild(event: TestChildEvent) {
        val player = event.player
        player.sendSystemMessage(Component.literal("子类 event 执行 id: ${event.id}"))
    }

    @EventHandler(EventPriority.LOW)
    fun onTestChildCanceled(event: TestChildEvent) {
        event.isInterrupted = true
    }

//    @EventHandler
//    fun onTestItemBreak(event: PlayerItemDestroyEvent) {
//        val player = event.player
//        player.sendSystemMessage(Component.literal("物品破坏事件 :${event.original}"))
//    }

//    @EventHandler
//    fun onTestBlockPlace(event: EntityPrePlaceBlockEvent) {
//        event.isCancelled = true
//        event.entity.sendSystemMessage(Component.literal("不准放! + ${event.entity.level().isClientSide}"))
//    }
//
//    @EventHandler
//    fun onTestBlockBreak(event: PlayerBlockBreakEvent) {
//        event.isCancelled = true
//        event.player.sendSystemMessage(Component.literal("不准破坏!"))
//    }

//    @EventHandler
//    fun onPlayerDeath(event: ServerPlayerDeathEvent) {
//        event.player.sendSystemMessage(Component.literal("菜! + "))
//        event.isCancelled = true
//        event.player.health = 1f
//    }
//
//    @EventHandler
//    fun onPlayerRespawn(event: ServerPlayerRespawnEvent) {
//        event.player.sendSystemMessage(Component.literal("你活了"))
//    }


}