package cn.coostack.cooparticlesapi.event.events.world

import cn.coostack.cooparticlesapi.event.api.CooEvent
import net.minecraft.world.level.Level

abstract class ClientWorldEvent(val world: Level) : CooEvent()