package cn.coostack.cooparticlesapi.utils.interpolator.data

import cn.coostack.cooparticlesapi.utils.GraphMathHelper

/**
 * @author CooStack
 * @date 2025/11/30
 *
 * 数字插值器
 * 提供progress 自动进行插值
 */
class NumberDataInterpolator(private var first: Double) : DataInterpolator<Double> {
    private var second: Double = 0.0
    private var onlyFirst = true
    override fun get(progress: Double): Double {
        return GraphMathHelper.lerp(progress, first, second)
    }

    override fun uploadValue(value: Double) {
        first = second
        second = value
        onlyFirst = false
    }

    override fun current(): Double {
        return if (onlyFirst) first else second
    }
}