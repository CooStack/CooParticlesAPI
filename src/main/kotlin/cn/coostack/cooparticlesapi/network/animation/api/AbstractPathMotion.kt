package cn.coostack.cooparticlesapi.network.animation.api

import net.minecraft.util.math.Vec3d

abstract class AbstractPathMotion(override var origin: Vec3d) : PathMotion {
    override var currentTick: Int = 0

    override fun next(): Vec3d {
        val value = pathFunction()
        currentTick++
        return value
    }

    abstract fun pathFunction(): Vec3d


}