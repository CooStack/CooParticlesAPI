package cn.coostack.cooparticlesapi.network.animation.api

import net.minecraft.world.phys.Vec3


abstract class AbstractPathMotion(override var origin: Vec3) : PathMotion {
    override var currentTick: Int = 0

    override fun next(): Vec3 {
        val value = pathFunction()
        currentTick++
        return value
    }

    abstract fun pathFunction(): Vec3


}