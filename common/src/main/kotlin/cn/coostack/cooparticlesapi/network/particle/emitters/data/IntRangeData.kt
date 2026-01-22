package cn.coostack.cooparticlesapi.network.particle.emitters.data

class IntRangeData(min: Int, max: Int) : RangeData<Int>(min, max)

infix fun Int.minRangeTo(max: Int): IntRangeData {
    return IntRangeData(this, max)
}

infix fun Int.maxRangeTo(min: Int): IntRangeData {
    return IntRangeData(min, this)
}