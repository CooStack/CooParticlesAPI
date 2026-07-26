package cn.coostack.cooparticlesapi.cparticle

enum class CParticleRenderPass {
    ALL,
    OPAQUE,
    TRANSLUCENT,
    NONE;

    fun accepts(layer: CParticleRenderLayer): Boolean = when (this) {
        ALL -> true
        OPAQUE -> layer == CParticleRenderLayer.OPAQUE
        TRANSLUCENT -> layer != CParticleRenderLayer.OPAQUE
        NONE -> false
    }

    companion object {
        @JvmStatic
        fun fromCoverage(opaque: Boolean, translucent: Boolean): CParticleRenderPass = when {
            opaque && translucent -> ALL
            opaque -> OPAQUE
            translucent -> TRANSLUCENT
            else -> NONE
        }
    }
}
