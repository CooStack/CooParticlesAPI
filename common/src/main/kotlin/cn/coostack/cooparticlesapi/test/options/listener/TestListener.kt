package cn.coostack.cooparticlesapi.test.options.listener

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.events.EventHandler
import cn.coostack.cooparticlesapi.annotations.events.EventListener
import cn.coostack.cooparticlesapi.test.options.event.TestEvent
import net.minecraft.network.chat.Component

@EventListener(CooParticlesConstants.MOD_ID)
class TestListener {

    @EventHandler
    fun onTestCalled(event: TestEvent) {
        event.player.sendSystemMessage(Component.literal("测试正常事件实例"))
    }

}