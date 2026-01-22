package cn.coostack.cooparticlesapi.network.particle.emitters.data

class DoubleRangeData(min: Double, max: Double) : RangeData<Double>(min, max)


infix fun Double.minRangeTo(max: Double): DoubleRangeData {
    return DoubleRangeData(this, max)
}

infix fun Double.maxRangeTo(min: Double): DoubleRangeData {
    return DoubleRangeData(min, this)
}