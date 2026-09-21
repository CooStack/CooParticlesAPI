package cn.coostack.cooparticlesapi.network.particle.data

import kotlin.random.Random

abstract class RangeData<T : Comparable<T>>(var min: T, var max: T) {
    init {
        require(min <= max) { "min must be <= $max" }
    }
}
