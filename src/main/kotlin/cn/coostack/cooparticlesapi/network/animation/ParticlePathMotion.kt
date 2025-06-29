package cn.coostack.cooparticlesapi.network.animation

import cn.coostack.cooparticlesapi.network.animation.api.AbstractPathMotion
import cn.coostack.cooparticlesapi.particles.ControlableParticle
import net.minecraft.util.math.Vec3d

/**
 * 用于对粒子发射器内的粒子设置路径运动的
 */
abstract class ParticlePathMotion(origin: Vec3d, val particle: ControlableParticle) : AbstractPathMotion(origin) {
    override fun apply(actualPos: Vec3d) {
        particle.teleportTo(actualPos)
    }

    override fun checkValid(): Boolean {
        return particle.isAlive
    }
}