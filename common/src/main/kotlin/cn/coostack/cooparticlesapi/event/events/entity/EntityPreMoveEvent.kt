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
class EntityPreMoveEvent(entity: Entity, var movement: Vec3) : EntityEvent(entity), EventCancelable,
    EventInterruptible {
    override var isCancelled: Boolean = false
    override var isInterrupted: Boolean = false

    /**
     * 如果实体没有物理 那么最终移动到的位置就是这里
     *
     * @return
     */
    fun getFinalPos(): Vec3 {
        return entity.position() + movement
    }
}