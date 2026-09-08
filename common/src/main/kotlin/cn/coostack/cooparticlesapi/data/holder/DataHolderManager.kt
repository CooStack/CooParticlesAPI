package cn.coostack.cooparticlesapi.data.holder

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.annotations.codec.CodecHelper
import cn.coostack.cooparticlesapi.network.packet.server.PacketDataHolderS2C
import cn.coostack.cooparticlesapi.reflect.CooAPIScanner
import cn.coostack.cooparticlesapi.reflect.SimpleClassInfo
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.entity.Entity
import java.util.concurrent.ConcurrentHashMap

object DataHolderManager {
    val entities = ConcurrentHashMap<Entity, DataHolder>()
    private val registeredTypes = ConcurrentHashMap<String, StreamCodec<*, *>>()

    fun getOrCreate(entity: Entity): DataHolder {
        return entities.getOrPut(entity) { DataHolder(entity) }
    }

    fun remove(entity: Entity) {
        entities.remove(entity)
    }

    fun tick() {
        entities.entries.removeIf {
            it.key.isRemoved
        }
    }

    fun register(randomInstance: Any) {
        val codec = findCodec(randomInstance)
        registeredTypes[randomInstance::class.java.name] = codec
    }

    fun register(type: Class<*>, codec: StreamCodec<*, *>) {
        registeredTypes[type.name] = codec
    }

    fun getCodecFromID(id: String): StreamCodec<*, *>? {
        return registeredTypes[id] ?: CodecHelper.supposedTypes[id]
    }

    fun registerScanner() {
        val start = System.currentTimeMillis()
        CooParticlesConstants.logger.info("正在自动注册 DataHolder")
        CooAPIScanner.getWithAnnotation(CooAutoRegister::class.java)
            .iterator()
            .forEach { target ->
                findListenerHandlers(target)
            }
        val end = System.currentTimeMillis()
        CooParticlesConstants.logger.info("DataHolder 注册完成 耗时 ${end - start} ms")
    }

    private fun findListenerHandlers(target: SimpleClassInfo) {
        val clazz = target.toClass()
        if (CodecHelper.supposedTypes[clazz.name] == null) {
            return
        }
        val instance = clazz.declaredConstructors.find { it.parameterCount == 0 }?.newInstance()
            ?: return
        register(instance)
    }

    private fun findCodec(instance: Any): StreamCodec<*, *> {
        val codec = CodecHelper.supposedTypes[instance::class.java.name]
        return codec
            ?: throw IllegalStateException("DataHolder codec not registered for type: ${instance::class.java.name}")
    }

    internal fun applyClient(entity: Entity, packet: PacketDataHolderS2C) {
        val store = getOrCreate(entity)
        store.cacheAllToggle = packet.cacheAllToggle
        store.applyClientSnapshot(packet.decodeData(), packet.fullSync)
    }

    fun clearClient() {
        entities.values.forEach { it.clearClient() }
    }
}
