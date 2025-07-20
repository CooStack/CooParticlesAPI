package cn.coostack.cooparticlesapi.utils.helper

import cn.coostack.cooparticlesapi.utils.RelativeLocation

/**
 * 直线移动工具
 * 需要在 addPreTickAction内执行next方法
 * @param reference 需要移动的对象点
 * @param direction 移动向量
 * @param maxTick 最多移动次数
 */
class MovementDirectionHelper(val reference: RelativeLocation, var direction: RelativeLocation, val maxTick: Int) {
    var tick = 0
    val default = reference.clone()
    fun next() {
        if (tick++ > maxTick) return
        reference.add(direction)
    }

    fun reset() {
        reference.x = default.x
        reference.y = default.y
        reference.z = default.z
        tick = 0
    }


}