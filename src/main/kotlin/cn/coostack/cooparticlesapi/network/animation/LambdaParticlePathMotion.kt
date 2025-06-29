package cn.coostack.cooparticlesapi.network.animation

import cn.coostack.cooparticlesapi.particles.ControlableParticle
import net.minecraft.util.math.Vec3d

class LambdaParticlePathMotion(origin: Vec3d, particle: ControlableParticle, val path: (tick: Int) -> Vec3d) :
    ParticlePathMotion(origin, particle) {
    override fun pathFunction(): Vec3d {
        return path(currentTick)
    }
}