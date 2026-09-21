package cn.coostack.cooparticlesapi.cparticle.force

object CParticleForceResourceRegistry {
    private val bindings = mutableMapOf<CParticleForceResource, Any>()
    fun bind(resource: CParticleForceResource, binding: Any) {
        bindings[resource] = binding
    }
    fun clearResolvedBindings() {
        bindings.clear()
    }
}
