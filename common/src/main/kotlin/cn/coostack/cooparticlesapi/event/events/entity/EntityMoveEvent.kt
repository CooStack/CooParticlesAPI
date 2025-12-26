package cn.coostack.cooparticlesapi.event.events.entity

import cn.coostack.cooparticlesapi.event.api.EventCancelable
import cn.coostack.cooparticlesapi.event.api.EventInterruptible
import cn.coostack.cooparticlesapi.extend.plus
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

/**
 * 实体准备移动事件
 * 可以直接修改实体的移动向量
 * 如果取消或者中断则会不允许实体发生移动 （直接设置为0）
 *
 * @property movement 实体移动向量
 * @constructor
 *
 * @param entity 移动的实体
 */
class EntityMoveEvent(entity: Entity, val movement: Vec3, var moveTo: Vec3) : EntityEvent(entity), EventCancelable,
    EventInterruptible {
    override var isCancelled: Boolean = false
    override var isInterrupted: Boolean = false
}