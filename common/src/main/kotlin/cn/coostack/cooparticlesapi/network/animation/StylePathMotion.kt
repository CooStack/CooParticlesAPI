package cn.coostack.cooparticlesapi.network.animation

import cn.coostack.cooparticlesapi.network.animation.api.AbstractPathMotion
import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import net.minecraft.world.phys.Vec3

abstract class StylePathMotion(origin: Vec3, val targetStyle: ParticleGroupStyle) : AbstractPathMotion(origin) {
    override fun apply(actualPos: Vec3) {
        targetStyle.teleportTo(actualPos)
    }

    override fun checkValid(): Boolean {
        return targetStyle.valid
    }
}