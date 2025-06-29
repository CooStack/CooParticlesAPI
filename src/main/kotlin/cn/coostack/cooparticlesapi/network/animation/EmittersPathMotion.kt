package cn.coostack.cooparticlesapi.network.animation

import cn.coostack.cooparticlesapi.network.animation.api.AbstractPathMotion
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import net.minecraft.util.math.Vec3d

abstract class EmittersPathMotion(origin: Vec3d, val targetEmitters: ParticleEmitters) : AbstractPathMotion(origin) {
    override fun apply(actualPos: Vec3d) {
        targetEmitters.teleportTo(actualPos)
    }

    override fun checkValid(): Boolean {
        return !targetEmitters.cancelled
    }
}