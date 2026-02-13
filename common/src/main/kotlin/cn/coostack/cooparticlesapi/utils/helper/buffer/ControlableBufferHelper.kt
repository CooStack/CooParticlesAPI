package cn.coostack.cooparticlesapi.utils.helper.buffer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffers
import cn.coostack.cooparticlesapi.api.controler.Controlable
import java.lang.reflect.Modifier

object ControlableBufferHelper {
    fun getPairs(
        buf: Controlable<*>
    ): Map<String, ParticleControlerDataBuffer<*>> {
        val res = mutableMapOf<String, ParticleControlerDataBuffer<*>>()
        val clazz = buf::class.java
        clazz.declaredFields.filter { it.isAnnotationPresent(ControlableBuffer::class.java) }
            .forEach {
                it.isAccessible = true
                val anno = it.getAnnotation(ControlableBuffer::class.java) ?: return@forEach
                val value = it.get(buf)
                val type = value::class.java
                val buffer = ParticleControlerDataBuffers.fromBufferType(value, type) ?: return@forEach
                res[anno.name] = buffer
            }
        return res
    }

    fun setPairs(buf: Controlable<*>, args: Map<String, ParticleControlerDataBuffer<*>>) {
        val clazz = buf::class.java
        clazz.declaredFields.filter { it.isAnnotationPresent(ControlableBuffer::class.java) }
            .forEach {
                it.isAccessible = true
                val anno = it.getAnnotation(ControlableBuffer::class.java) ?: return@forEach
                val value = args[anno.name] ?: return@forEach
                if (it.modifiers == Modifier.FINAL) {
                    CooParticlesConstants.logger
                        .warn("无法设置final属性 ${it.name} 为 $value")
                    return@forEach
                }
                it.set(buf, value.loadedValue)
            }
    }

}