package cn.coostack.cooparticlesapi.network.animation

import cn.coostack.cooparticlesapi.network.animation.api.AbstractPathMotion
import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import net.minecraft.util.math.Vec3d

abstract class StylePathMotion(origin: Vec3d, val targetStyle: ParticleGroupStyle) : AbstractPathMotion(origin) {
    override fun apply(actualPos: Vec3d) {
        targetStyle.teleportTo(actualPos)
    }

    override fun checkValid(): Boolean {
        return targetStyle.valid
    }
}