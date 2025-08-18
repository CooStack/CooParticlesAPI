package cn.coostack.cooparticlesapi.utils.helper

import cn.coostack.cooparticlesapi.utils.RelativeLocation

/**
 * 直线移动工具
 * 用于在Style/Group 内部进行单个粒子组/粒子的位置操控
 * 需要在 addPreTickAction内执行next方法
 * @param reference 需要移动的对象点
 * @param target 移动到目标位置
 * @param maxTick 移动到目标位置所需要的时间
 */
class MovementTargetHelper(val reference: RelativeLocation, var target: RelativeLocation, val maxTick: Int) {
    val default = reference.clone()
    fun next() {
        val direction = (target - default).multiply(1.0 / maxTick)
        reference.add(direction)
    }

    fun reset() {
        reference.x = default.x
        reference.y = default.y
        reference.z = default.z
    }
}