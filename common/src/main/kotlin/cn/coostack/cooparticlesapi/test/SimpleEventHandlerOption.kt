package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.event.CooEventBus
import cn.coostack.cooparticlesapi.test.api.TestOption
import cn.coostack.cooparticlesapi.test.options.event.TestEvent
import net.minecraft.world.entity.player.Player

class SimpleEventHandlerOption(val testPlayer: Player, ticking: Int) : TickingTestOption(ticking) {
    override fun start() {
    }

    override fun doTick() {
        super.doTick()
        CooEventBus.call(TestEvent(testPlayer))
    }

    override fun stop() {
    }

    override fun onFailed() {
    }

    override fun onSuccess() {
    }

    override fun optionID(): String {
        return "SimpleEventHandlerOption"
    }
}