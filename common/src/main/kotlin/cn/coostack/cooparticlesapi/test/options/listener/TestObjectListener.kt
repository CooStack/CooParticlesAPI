package cn.coostack.cooparticlesapi.test.options.listener

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.events.EventHandler
import cn.coostack.cooparticlesapi.annotations.events.EventListener
import cn.coostack.cooparticlesapi.event.api.EventPriority
import cn.coostack.cooparticlesapi.test.options.event.TestEvent
import net.minecraft.network.chat.Component

/**
 *输入一个假的mod id进去看看能不能做好标识
 */
@EventListener("TestModID")
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

    @EventHandler
    fun onTestFailed(event: TestEvent) {
        throw NullPointerException("测试空指针异常 - 事件执行失败")
    }

    @EventHandler(EventPriority.LOW)
    fun onTestCanceled(event: TestEvent) {
        val player = event.player
        player.sendSystemMessage(Component.literal("这段代码之后就不会再有下一段了"))
        event.hasInterrupted = true
    }

    @EventHandler(EventPriority.LOWEST)
    fun onTestInterrupt(event: TestEvent) {
        val player = event.player
        player.sendSystemMessage(Component.literal("这段代码不应该被执行"))
    }
}