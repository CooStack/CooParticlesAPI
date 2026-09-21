package cn.coostack.cooparticlesapi.cparticle.force

class CParticleFluidResource(id: String) : CParticleForceResource(id), CParticleFluidBinding {
    override fun sampleFluid(x: Double, y: Double, z: Double, out: FloatArray) {}
    fun bindCompute(index: Int) {}
    fun resetCompute() {}
}
