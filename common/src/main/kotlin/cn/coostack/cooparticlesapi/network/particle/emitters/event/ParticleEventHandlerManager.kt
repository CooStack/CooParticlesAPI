package cn.coostack.cooparticlesapi.network.particle.emitters.event

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.emitter.EmitterEventAutoRegister
import cn.coostack.cooparticlesapi.reflect.CooAPIScanner
import cn.coostack.cooparticlesapi.reflect.SimpleClassInfo
import java.lang.reflect.Modifier

object ParticleEventHandlerManager {
    private val registerHandlers = HashMap<String, ParticleEventHandler>()

    fun getHandlerById(id: String): ParticleEventHandler? {
        return registerHandlers[id]
    }

    fun register(event: ParticleEventHandler) {
        registerHandlers[event.getHandlerID()] = event
    }

    fun hasRegister(id: String): Boolean = getHandlerById(id) != null

    private var handled = false
    fun registerScanner() {
        if (handled) {
            return
        }
        CooAPIScanner.getWithAnnotation(
            EmitterEventAutoRegister::class.java
        ).forEach {
            findListenerHandlers(it)
        }
    }

    private fun findListenerHandlers(target: SimpleClassInfo) {
        val clazz = target.toClass()
        // 获取instance
        val instance =
            clazz.declaredFields.find { it.name == "INSTANCE" && Modifier.isStatic(it.modifiers) }?.get(null)
                ?: clazz.getDeclaredConstructor()
                    .apply { isAccessible = true }
                    .newInstance()

        register(instance as ParticleEventHandler)
        CooParticlesConstants.logger.info("自动注册: ${clazz.name} 成功！")
    }

}