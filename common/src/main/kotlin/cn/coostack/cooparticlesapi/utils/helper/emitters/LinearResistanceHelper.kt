package cn.coostack.cooparticlesapi.utils.helper.emitters

import net.minecraft.world.phys.Vec3


object LinearResistanceHelper {
    fun setPercentageVelocity(enter: Vec3, precent: Double): Vec3 {
        return enter.scale(precent)
    }
}