package cn.coostack.cooparticlesapi.event.events

import cn.coostack.cooparticlesapi.event.api.CooEvent
import net.minecraft.world.entity.player.Player

abstract class PlayerEvent(val player: Player) : CooEvent()