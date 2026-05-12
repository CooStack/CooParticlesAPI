package cn.coostack.cooparticlesapi.event.events.entity.server

import cn.coostack.cooparticlesapi.event.events.entity.EntityLoadEvent
import net.minecraft.world.entity.Entity

class ServerEntityLoadEvent(entity: Entity) : EntityLoadEvent(entity)
