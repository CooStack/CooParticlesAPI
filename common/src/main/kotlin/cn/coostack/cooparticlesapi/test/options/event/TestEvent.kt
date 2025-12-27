package cn.coostack.cooparticlesapi.test.options.event

import cn.coostack.cooparticlesapi.event.api.CooEvent
import cn.coostack.cooparticlesapi.event.api.EventInterruptible
import net.minecraft.world.entity.player.Player

abstract class TestEvent(val player: Player) : CooEvent(), EventInterruptible {
    override var isInterrupted: Boolean = false
}