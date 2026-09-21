package cn.coostack.cooparticlesapi.cparticle.force

class CParticleTextureResource(id: String) : CParticleForceResource(id), CParticleTextureBinding {
    override fun sampleTexture(x: Double, y: Double, z: Double, out: FloatArray) {}
    fun bindCompute(index: Int) {}
    fun resetCompute() {}
}
