package cn.coostack.cooparticlesapi.test.options.listener

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.events.EventHandler
import cn.coostack.cooparticlesapi.annotations.events.EventListener
import cn.coostack.cooparticlesapi.event.api.EventPriority
import cn.coostack.cooparticlesapi.test.options.event.TestEvent
import net.minecraft.network.chat.Component

@EventListener
object TestObjectListener {

    @EventHandler
    fun onTestCalled(event: TestEvent) {
        event.player.sendSystemMessage(Component.literal("测试object事件监听器"))
    }

    @JvmStatic
    @EventHandler(EventPriority.HIGHEST)
    fun onTestStatic(event: TestEvent) {
        event.player.sendSystemMessage(Component.literal("测试static 事件"))
    }
}