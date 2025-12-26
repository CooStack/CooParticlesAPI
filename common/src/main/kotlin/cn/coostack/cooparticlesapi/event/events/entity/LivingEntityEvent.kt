package cn.coostack.cooparticlesapi.event.events.entity

import cn.coostack.cooparticlesapi.event.api.CooEvent
import net.minecraft.world.entity.LivingEntity

abstract class LivingEntityEvent(entity: LivingEntity) : EntityEvent(entity)