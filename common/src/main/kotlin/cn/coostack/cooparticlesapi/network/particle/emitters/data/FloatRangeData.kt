package cn.coostack.cooparticlesapi.network.particle.emitters.data

class FloatRangeData(min: Float, max: Float) : RangeData<Float>(min, max)


infix fun Float.minRangeTo(max: Float): FloatRangeData {
    return FloatRangeData(this, max)
}

infix fun Float.maxRangeTo(min: Float): FloatRangeData {
    return FloatRangeData(min, this)
}