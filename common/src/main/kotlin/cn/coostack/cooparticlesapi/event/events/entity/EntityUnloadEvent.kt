package cn.coostack.cooparticlesapi.event.events.entity

import net.minecraft.world.entity.Entity

open class EntityUnloadEvent(
    entity: Entity,
    val unloadType: EntityUnloadType
) : EntityEvent(entity)
