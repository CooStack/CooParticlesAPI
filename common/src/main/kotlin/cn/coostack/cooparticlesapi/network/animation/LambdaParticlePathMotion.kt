package cn.coostack.cooparticlesapi.network.animation

import cn.coostack.cooparticlesapi.particles.ControlableParticle
import net.minecraft.world.phys.Vec3

class LambdaParticlePathMotion(origin: Vec3, particle: ControlableParticle, val path: (tick: Int) -> Vec3) :
    ParticlePathMotion(origin, particle) {
    override fun pathFunction(): Vec3 {
        return path(currentTick)
    }
}