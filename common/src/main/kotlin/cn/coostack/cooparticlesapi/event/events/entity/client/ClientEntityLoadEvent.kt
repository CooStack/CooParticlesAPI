package cn.coostack.cooparticlesapi.event.events.entity.client

import cn.coostack.cooparticlesapi.event.events.entity.EntityLoadEvent
import net.minecraft.world.entity.Entity

class ClientEntityLoadEvent(entity: Entity) : EntityLoadEvent(entity)
