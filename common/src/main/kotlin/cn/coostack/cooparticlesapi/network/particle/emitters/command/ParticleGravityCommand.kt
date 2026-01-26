package cn.coostack.cooparticlesapi.network.particle.emitters.command

import cn.coostack.cooparticlesapi.network.particle.emitters.ClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.particles.ControlableParticle

/**
 * 在emitter内部使用updatePhysics 或者使用这个
 *
 * @property emitter 对应粒子发射器
 */
class ParticleGravityCommand(val emitter: ClassParticleEmitters) : ParticleCommand {
    override fun execute(
        data: ControlableParticleData,
        particle: ControlableParticle
    ) {
        emitter.updatePhysics(particle.loc, data, particle)
    }
}