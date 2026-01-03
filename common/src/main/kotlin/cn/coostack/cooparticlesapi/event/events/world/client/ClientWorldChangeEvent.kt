package cn.coostack.cooparticlesapi.event.events.world.client

import cn.coostack.cooparticlesapi.event.events.world.WorldEvent
import net.minecraft.world.level.Level

class ClientWorldChangeEvent(world: Level) : WorldEvent(world)