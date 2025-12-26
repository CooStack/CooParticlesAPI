package cn.coostack.cooparticlesapi.test.options.event

import cn.coostack.cooparticlesapi.event.api.CooEvent
import net.minecraft.world.entity.player.Player

data class TestEvent(val player: Player) : CooEvent()