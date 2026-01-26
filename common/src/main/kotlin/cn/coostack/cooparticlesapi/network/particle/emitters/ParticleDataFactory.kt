package cn.coostack.cooparticlesapi.network.particle.emitters

fun interface ParticleDataFactory {
    fun create(): ControlableParticleData
}