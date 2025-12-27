package cn.coostack.cooparticlesapi.test.options.listener

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.events.EventHandler
import cn.coostack.cooparticlesapi.annotations.events.EventListener
import cn.coostack.cooparticlesapi.event.api.EventPriority
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

}