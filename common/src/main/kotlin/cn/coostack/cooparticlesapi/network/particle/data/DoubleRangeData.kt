package cn.coostack.cooparticlesapi.network.particle.data

import kotlin.random.Random

class DoubleRangeData(min: Double, max: Double) : RangeData<Double>(min, max) {

    fun random(): Double {
        return Random.nextDouble(min, max)
    }

}

infix fun Double.isIn(range: DoubleRangeData): Boolean {
    return this in range.min..range.max
}

infix fun Double.minRangeTo(max: Double): DoubleRangeData {
    return DoubleRangeData(this, max)
}

infix fun Double.maxRangeTo(min: Double): DoubleRangeData {
    return DoubleRangeData(min, this)
}