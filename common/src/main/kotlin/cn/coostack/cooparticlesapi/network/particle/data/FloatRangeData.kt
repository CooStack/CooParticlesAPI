package cn.coostack.cooparticlesapi.network.particle.data

import kotlin.random.Random

class FloatRangeData(min: Float, max: Float) : RangeData<Float>(min, max) {
    fun random(): Float = Random.nextFloat() * (max - min) + min
}

infix fun Float.isIn(range: FloatRangeData): Boolean {
    return this in range.min..range.max
}

infix fun Float.minRangeTo(max: Float): FloatRangeData {
    return FloatRangeData(this, max)
}

infix fun Float.maxRangeTo(min: Float): FloatRangeData {
    return FloatRangeData(min, this)
}