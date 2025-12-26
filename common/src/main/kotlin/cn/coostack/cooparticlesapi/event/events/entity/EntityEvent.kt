package cn.coostack.cooparticlesapi.event.events.entity

import cn.coostack.cooparticlesapi.event.api.CooEvent
import net.minecraft.world.entity.Entity

abstract class EntityEvent(val entity: Entity) : CooEvent() {
}