package cn.coostack.cooparticlesapi.event.events.entity

import cn.coostack.cooparticlesapi.event.api.CooEvent
import cn.coostack.cooparticlesapi.event.api.EventCancelable
import cn.coostack.cooparticlesapi.event.api.EventInterruptible
import net.minecraft.world.entity.Entity

class EntityPostTickEvent(entity: Entity) : EntityEvent(entity), EventInterruptible {
    override var isInterrupted: Boolean = false
}