package cn.coostack.cooparticlesapi.network.particle.data

import kotlin.random.Random

class IntRangeData(min: Int, max: Int) : RangeData<Int>(min, max) {
    fun random(): Int = Random.nextInt(min, max)
}


infix fun Int.isIn(range: IntRangeData): Boolean {
    return this in range.min..range.max
}

infix fun Int.minRangeTo(max: Int): IntRangeData {
    return IntRangeData(this, max)
}

infix fun Int.maxRangeTo(min: Int): IntRangeData {
    return IntRangeData(min, this)
}