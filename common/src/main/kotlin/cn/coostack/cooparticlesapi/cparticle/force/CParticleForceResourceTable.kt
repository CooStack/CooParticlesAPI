package cn.coostack.cooparticlesapi.cparticle.force

interface CParticleTextureBinding {
    fun sampleTexture(x: Double, y: Double, z: Double, out: FloatArray)
}

interface CParticleFluidBinding {
    fun sampleFluid(x: Double, y: Double, z: Double, out: FloatArray)
}

class CParticleForceResourceTable {
    companion object {
        const val MAX_TEXTURE_RESOURCES: Int = 16
    }
    fun resolve(resource: CParticleForceResource): Any? = null
    fun slotFor(resource: CParticleTextureResource): Int = -1
    fun slotFor(resource: CParticleFluidResource): Int = -1
    fun textureBinding(slot: Int): CParticleTextureBinding = NullTextureBinding
    fun fluidBinding(slot: Int): CParticleFluidBinding = NullFluidBinding
    fun textureBindings(): List<CParticleTextureResource> = emptyList()
    fun fluidBindings(): List<CParticleFluidResource> = emptyList()
    fun clear() {}
}

private object NullTextureBinding : CParticleTextureBinding {
    override fun sampleTexture(x: Double, y: Double, z: Double, out: FloatArray) {}
}

private object NullFluidBinding : CParticleFluidBinding {
    override fun sampleFluid(x: Double, y: Double, z: Double, out: FloatArray) {}
}
