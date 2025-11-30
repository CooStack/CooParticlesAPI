package cn.coostack.cooparticlesapi.utils.interpolator.data

import cn.coostack.cooparticlesapi.utils.GraphMathHelper

/**
 * @author CooStack
 * @date 2025/11/30
 */
class NumberDataInterpolator(private var first: Double) : DataInterpolator<Double> {
    private var second: Double = 0.0
    override fun get(progress: Double): Double {
        return GraphMathHelper.lerp(progress, first, second)
    }

    override fun uploadValue(value: Double) {
        first = second
        second = value
    }

    override fun current(): Double {
        return second
    }
}