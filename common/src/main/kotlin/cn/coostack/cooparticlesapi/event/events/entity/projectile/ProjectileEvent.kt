package cn.coostack.cooparticlesapi.event.events.entity.projectile

import cn.coostack.cooparticlesapi.event.events.entity.EntityEvent
import net.minecraft.world.entity.projectile.Projectile

abstract class ProjectileEvent(entity: Projectile) : EntityEvent(entity)