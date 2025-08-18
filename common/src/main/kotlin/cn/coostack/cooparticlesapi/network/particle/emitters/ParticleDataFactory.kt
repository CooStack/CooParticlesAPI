package cn.coostack.cooparticlesapi.network.particle.emitters

interface ParticleDataFactory {
    fun create(): ControlableParticleData
}