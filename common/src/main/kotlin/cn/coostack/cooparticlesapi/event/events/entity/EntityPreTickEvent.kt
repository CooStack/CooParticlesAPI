package cn.coostack.cooparticlesapi.event.events.entity

import cn.coostack.cooparticlesapi.event.api.CooEvent
import cn.coostack.cooparticlesapi.event.api.EventCancelable
import cn.coostack.cooparticlesapi.event.api.EventInterruptible
import net.minecraft.world.entity.Entity

class EntityPreTickEvent(entity: Entity) : EntityEvent(entity), EventCancelable, EventInterruptible {
    override var isCancelled: Boolean = false
    override var isInterrupted: Boolean = false
}