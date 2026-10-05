package cn.coostack.cooparticlesapi.entities.structure.client

import kotlin.math.round

/**
 * 数值控件的有限步进计算，不依赖客户端，供箭头、拖动和键盘共用。
 * @property minimum 最小合法值，包含边界
 * @property maximum 最大合法值，包含边界
 * @property step 一次普通增减对应的数值变化量
 */
class ModelNumberStepper(val minimum: Double, val maximum: Double, val step: Double) {
    init {
        require(minimum.isFinite() && maximum.isFinite() && minimum <= maximum)
        require(step.isFinite() && step > 0.0)
    }

    /**
     * 计算步进结果，最小步长为 0.01，结果量化到两位小数，防止拖动产生过细数值。
     * 示例：`stepper.adjust(1.0, 1.0, false, false)`。
     * @param value 当前有限数值
     * @param steps 增减步数；拖动可以提供小数步数
     * @param fine 是否使用十分之一的精细步长，但不低于 0.01，优先于快速步长
     * @param coarse 是否使用十倍快速步长
     * @return 限定在合法范围内的新数值
     */
    fun adjust(value: Double, steps: Double, fine: Boolean, coarse: Boolean): Double {
        require(value.isFinite() && steps.isFinite())
        val multiplier = if (fine) 0.1 else if (coarse) 10.0 else 1.0
        val effectiveStep = (step * multiplier).coerceAtLeast(0.01)
        val result = (value + steps * effectiveStep).coerceIn(minimum, maximum)
        return (round(result * 100.0) / 100.0).coerceIn(minimum, maximum)
    }
}
