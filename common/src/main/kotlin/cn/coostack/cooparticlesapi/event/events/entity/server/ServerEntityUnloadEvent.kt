package cn.coostack.cooparticlesapi.event.events.entity.server

import cn.coostack.cooparticlesapi.event.events.entity.EntityUnloadEvent
import cn.coostack.cooparticlesapi.event.events.entity.EntityUnloadType
import net.minecraft.world.entity.Entity

class ServerEntityUnloadEvent(
    entity: Entity,
    unloadType: EntityUnloadType
) : EntityUnloadEvent(entity, unloadType)
