package cn.coostack.cooparticlesapi.event.events.entity.player

import cn.coostack.cooparticlesapi.event.api.CooEvent
import cn.coostack.cooparticlesapi.event.events.entity.EntityEvent
import net.minecraft.world.entity.player.Player

abstract class PlayerEvent(val player: Player) : EntityEvent(player)