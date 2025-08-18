package cn.coostack.cooparticlesapi.network.animation

import cn.coostack.cooparticlesapi.network.animation.api.AbstractPathMotion
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import net.minecraft.world.phys.Vec3

abstract class EmittersPathMotion(origin: Vec3, val targetEmitters: ParticleEmitters) : AbstractPathMotion(origin) {
    override fun apply(actualPos: Vec3) {
        targetEmitters.teleportTo(actualPos)
    }

    override fun checkValid(): Boolean {
        return !targetEmitters.cancelled
    }
}