package cn.coostack.cooparticlesapi.event.events.entity

import cn.coostack.cooparticlesapi.event.api.EventCancelable
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level

/**
 * TODO 暂未实现call
 *
 * @property world
 * @property block
 * @property direction
 * @constructor
 * TODO
 *
 * @param entity
 */
class EntityPrePlaceBlockEvent(
    entity: Entity,
    val world: Level,
    val block: BlockPos,
    val direction: Direction
) :
    EntityEvent(entity),
    EventCancelable {
    override var isCancelled: Boolean = false
}