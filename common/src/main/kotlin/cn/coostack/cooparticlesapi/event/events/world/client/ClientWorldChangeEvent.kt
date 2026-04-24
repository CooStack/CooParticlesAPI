package cn.coostack.cooparticlesapi.event.events.world.client

import cn.coostack.cooparticlesapi.event.events.world.ClientWorldEvent
import net.minecraft.world.level.Level

class ClientWorldChangeEvent(world: Level) : ClientWorldEvent(world)