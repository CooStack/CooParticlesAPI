package cn.coostack.cooparticlesapi.network.particle.emitters.event

object ParticleEventHandlerManager {
    private val registerHandlers = HashMap<String, ParticleEventHandler>()

    fun getHandlerById(id: String): ParticleEventHandler? {
        return registerHandlers[id]
    }

    fun register(event: ParticleEventHandler) {
        registerHandlers[event.getHandlerID()] = event
    }

    fun hasRegister(id: String): Boolean = getHandlerById(id) != null

}