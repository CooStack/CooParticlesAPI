package cn.coostack.cooparticlesapi.network.particle.emitters.event

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.reflect.CooAPIScanner
import cn.coostack.cooparticlesapi.reflect.SimpleClassInfo
import cn.coostack.cooparticlesapi.utils.ReflectUtil
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
        val before = registerHandlers.size
        CooParticlesConstants.logger.info("正在自动注册 ParticleEventHandler")
        var count = 0
        val start = System.currentTimeMillis()
        CooAPIScanner.getWithAnnotation(
            CooAutoRegister::class.java
        ).forEach {
            count++
            findListenerHandlers(it)
        }
        val end = System.currentTimeMillis()
        CooParticlesConstants.logger.info("EmittersEvents 注册完成 耗时 ${end - start} ms 扫描了 $count 个类 实际注册 :${registerHandlers.size - before}")
    }

    private fun findListenerHandlers(target: SimpleClassInfo) {
        val clazz = target.toClass()
        if (!ParticleEvent::class.java.isAssignableFrom(clazz)) {
            return
        }
        // 获取instance
        val instance =
            clazz.declaredFields.find { it.name == "INSTANCE" && Modifier.isStatic(it.modifiers) }?.get(null)
                ?: clazz.getDeclaredConstructor()
                    .apply { isAccessible = true }
                    .newInstance()
        register(instance as ParticleEventHandler)
    }

}