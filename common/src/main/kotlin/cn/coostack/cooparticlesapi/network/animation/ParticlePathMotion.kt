package cn.coostack.cooparticlesapi.network.animation

import cn.coostack.cooparticlesapi.network.animation.api.AbstractPathMotion
import cn.coostack.cooparticlesapi.particles.ControlableParticle
import net.minecraft.world.phys.Vec3

/**
 * 用于对粒子发射器内的粒子设置路径运动的
 */
abstract class ParticlePathMotion(origin: Vec3, val particle: ControlableParticle) : AbstractPathMotion(origin) {
    override fun apply(actualPos: Vec3) {
        particle.teleportTo(actualPos)
    }

    override fun checkValid(): Boolean {
        return particle.isAlive
    }
}