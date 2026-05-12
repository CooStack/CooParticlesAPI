package cn.coostack.cooparticlesapi.event.events.entity.client

import cn.coostack.cooparticlesapi.event.events.entity.EntityUnloadEvent
import cn.coostack.cooparticlesapi.event.events.entity.EntityUnloadType
import net.minecraft.world.entity.Entity

class ClientEntityUnloadEvent(
    entity: Entity,
    unloadType: EntityUnloadType
) : EntityUnloadEvent(entity, unloadType)
