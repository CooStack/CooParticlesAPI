package cn.coostack.cooparticlesapi.cparticle.force

open class CParticleForceResource(val id: String) {
    override fun equals(other: Any?): Boolean = other is CParticleForceResource && other.id == id
    override fun hashCode(): Int = id.hashCode()
}
