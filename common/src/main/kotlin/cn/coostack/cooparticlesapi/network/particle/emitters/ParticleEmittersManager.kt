package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.CooParticlesAPI
import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.annotations.emitter.handle.ParticleEmittersRegistryHelper
import cn.coostack.cooparticlesapi.cparticle.compat.CParticleEmitterBridge
import cn.coostack.cooparticlesapi.event.CooEventBus
import cn.coostack.cooparticlesapi.event.events.particle.emitter.EmitterRemoveEvent
import cn.coostack.cooparticlesapi.event.events.particle.emitter.EmitterSpawnEvent
import cn.coostack.cooparticlesapi.reflect.CooAPIScanner
import cn.coostack.cooparticlesapi.network.packet.server.PacketParticleEmittersS2C
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import cn.coostack.cooparticlesapi.reflect.SimpleClassInfo
import io.netty.buffer.Unpooled
import net.minecraft.client.Minecraft
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.HashSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ParticleEmittersManager {
    // 已经start的 emitters
    val emittersCodec = HashMap<String, StreamCodec<RegistryFriendlyByteBuf, ParticleEmitters>>()

    /**
     * 服务器拥有
     */
    val serverEmitters = HashMap<UUID, ParticleEmitters>()
    internal val visible = ConcurrentHashMap<UUID, MutableSet<ParticleEmitters>>()

    /**
     * 客户端可视
     */
    val clientEmitters = ConcurrentHashMap<UUID, ParticleEmitters>()

    fun getCodecFromID(id: String): StreamCodec<RegistryFriendlyByteBuf, ParticleEmitters>? {
        return emittersCodec[id]
    }


    /**
     * 在客户端执行
     */
    @JvmStatic
    fun register(
        id: String,
        codec: StreamCodec<RegistryFriendlyByteBuf, ParticleEmitters>
    ): StreamCodec<RegistryFriendlyByteBuf, ParticleEmitters> {
        emittersCodec[id] = codec
        return codec
    }

    @JvmStatic
    fun register(randomInstance: ParticleEmitters) {
        val codec = randomInstance.getCodec()
        val id = randomInstance.getEmittersID()
        register(id, codec)
    }


    @JvmStatic
    fun addEmitters(emitters: ParticleEmitters) {
        if (emitters.world == null) return
        if (!emitters.world!!.isClientSide) return
        clientEmitters[emitters.uuid] = emitters
        emitters.start()
        CooEventBus.call(EmitterSpawnEvent(emitters, true))
    }

    @JvmStatic
    fun spawnEmitters(emitters: ParticleEmitters) {
        if (emitters.world == null) return
        if (emitters.world!!.isClientSide) return
        serverEmitters[emitters.uuid] = emitters
        emitters.start()
        updateClientVisible(emitters)
    }

    fun createOrChangeClient(emitters: ParticleEmitters, viewWorld: Level) {
        emitters.world = viewWorld
        if (emitters.canceled) {
            clientEmitters.remove(emitters.uuid)
            finishClientSystems(emitters)
            return
        }
        if (clientEmitters.containsKey(emitters.uuid)) {
            clientEmitters[emitters.uuid]!!.apply {
                update(emitters)
                world = viewWorld
            }
        } else {
            clientEmitters[emitters.uuid] = emitters
            CooEventBus.call(EmitterSpawnEvent(emitters, true))
        }

    }

    fun doTickServer() {
        val iterator = serverEmitters.iterator()
        while (iterator.hasNext()) {
            val emitter = iterator.next()
            val emitters = emitter.value
            updateClientVisible(emitter.value)
            emitters.tick()
            if (emitter.value.canceled) {
                filterVisiblePlayer(emitters).forEach {
                    val player = emitters.world!!.getPlayerByUUID(it) ?: return@forEach
                    removeView(player as ServerPlayer, emitters)
                    visible[it]?.remove(emitters)
                }
                iterator.remove()
            }
        }
    }

    fun doTickClient() {
        val player = Minecraft.getInstance().player ?: return
        if (player.isDeadOrDying) {
            clearAllVisible()
            return
        }
        val iterator = clientEmitters.iterator()
        while (iterator.hasNext()) {
            val emitters = iterator.next().value
            emitters.tick()
            if (emitters.canceled) {
                iterator.remove()
                finishClientSystems(emitters)
                CooEventBus.call(EmitterRemoveEvent(emitters, true))
            }
        }
    }

    fun filterVisiblePlayer(group: ParticleEmitters): Set<UUID> {
        val set = HashSet<UUID>()
        visible.forEach {
            if (group in it.value) {
                set.add(it.key)
            }
        }
        return set
    }

    fun updateClientVisible(emitters: ParticleEmitters) {
        CooEventBus.call(EmitterSpawnEvent(emitters, false))
        val server = CooParticlesAPI.serverOrNull ?: return
        server.playerList.players.forEach { p ->
            val visibleSet = visible.getOrPut(p.uuid) { HashSet() }
            if (p.level().dimension() != emitters.world?.dimension()) {
                // 世界转换
                if (emitters in visibleSet) {
                    removeView(p, emitters)
                    visibleSet!!.remove(emitters)
                }
                return@forEach
            }
            if (p.isDeadOrDying) {
                if (emitters in visibleSet) {
                    removeView(p, emitters)
                    visibleSet!!.remove(emitters)
                }
                return@forEach
            }
            if (emitters in visibleSet) {
                return@forEach
            }
            addView(p, emitters)
        }
    }

    fun updateEmitters(emitters: ParticleEmitters) {
        filterVisiblePlayer(emitters).forEach {
            val player = emitters.world!!.getPlayerByUUID(it) ?: return@forEach

            val data = encodeEmittersToArray(emitters)
            val packet = PacketParticleEmittersS2C(
                emitters.getEmittersID(),
                data,
                PacketParticleEmittersS2C.PacketType.CHANGE_OR_CREATE
            )
            CooParticlesServices.SERVER_NETWORK.send(packet, player as ServerPlayer)
        }
    }

    fun sendChange(emitters: ParticleEmitters, to: ServerPlayer) {
        val data = encodeEmittersToArray(emitters)
        val packet = PacketParticleEmittersS2C(
            emitters.getEmittersID(),
            data,
            PacketParticleEmittersS2C.PacketType.CHANGE_OR_CREATE
        )
        CooParticlesServices.SERVER_NETWORK.send(packet, to)
    }

    private fun addView(player: ServerPlayer, emitters: ParticleEmitters) {
        val data = encodeEmittersToArray(emitters)

        val packet = PacketParticleEmittersS2C(
            emitters.getEmittersID(),
            data,
            PacketParticleEmittersS2C.PacketType.CHANGE_OR_CREATE
        )
        CooParticlesServices.SERVER_NETWORK.send(packet, player)
    }

    fun clearAllVisible() {
        clientEmitters.values.forEach {
            it.remove()
            finishClientSystems(it)
            CooEventBus.call(EmitterRemoveEvent(it, true))
        }
        clientEmitters.clear()
    }

    fun clearServer() {
        serverEmitters.onEach { it.value.canceled = true }.clear()
        visible.clear()
    }

    private fun removeView(player: ServerPlayer, emitters: ParticleEmitters) {
        val data = encodeEmittersToArray(emitters)
        val packet = PacketParticleEmittersS2C(
            emitters.getEmittersID(),
            data,
            PacketParticleEmittersS2C.PacketType.REMOVE
        )
        CooParticlesServices.SERVER_NETWORK.send(packet, player)
        CooEventBus.call(EmitterRemoveEvent(emitters, false))
    }


    private fun encodeEmittersToArray(emitters: ParticleEmitters): ByteArray {
        val codec = emitters.getCodec()
        val registryAccess = emitters.world?.registryAccess() ?: CooParticlesAPI.registryAccessOrNull ?: return ByteArray(0)
        val buf = RegistryFriendlyByteBuf(
            Unpooled.buffer(),
            registryAccess
        )
        codec.encode(buf, emitters)

        val data = ByteArray(buf.readableBytes())
        buf.readBytes(data) // 只读 writerIndex 之前的内容
        return data
    }

    internal fun init() {
    }

    private var handled = false
    fun registerScanner() {
        if (handled) {
            return
        }
        val start = System.currentTimeMillis()
        handled = true
        CooParticlesConstants.logger.info("正在自动注册 Emitters")
        CooAPIScanner.getWithAnnotation(
            CooAutoRegister::class.java
        ).forEach {
            findListenerHandlers(it)
        }
        val end = System.currentTimeMillis()
        CooParticlesConstants.logger.info("Emitters 注册完成 耗时 ${end - start} ms")
    }

    private fun findListenerHandlers(target: SimpleClassInfo) {
        val clazz = target.toClass()
        if (!ParticleEmitters::class.java.isAssignableFrom(clazz)) {
            return
        }
        if (AutoParticleEmitters::class.java.isAssignableFrom(clazz)) {
            @Suppress("UNCHECKED_CAST")
            register(
                clazz.name,
                ParticleEmittersRegistryHelper.generateClassParticleCodec(clazz as Class<out ClassParticleEmitters>)
            )
            return
        }
        if (AutoTransformableCParticleEmitter::class.java.isAssignableFrom(clazz)) {
            @Suppress("UNCHECKED_CAST")
            register(
                clazz.name,
                ParticleEmittersRegistryHelper.generateTransformableCParticleEmitterCodec(
                    clazz as Class<out TransformableCParticleEmitter>,
                ),
            )
            return
        }
        if (AutoEmitters::class.java.isAssignableFrom(clazz)) {
            @Suppress("UNCHECKED_CAST")
            register(
                clazz.name,
                ParticleEmittersRegistryHelper.generateClassEmittersCodec(clazz as Class<out ClassEmitters>)
            )
            return
        }
        // 获取instance
        val instance =
            clazz.declaredConstructors.find {
                it.parameterCount == 0
            }?.newInstance() ?: clazz.getDeclaredConstructor(Vec3::class.java, Level::class.java)
                .newInstance(Vec3.ZERO, null)
        register(instance as ParticleEmitters)
    }

    private fun finishClientSystems(emitter: ParticleEmitters) {
        if (emitter is TransformableCParticleEmitter) {
            emitter.finishClientSystems()
        } else {
            CParticleEmitterBridge.finishEmitter(emitter.uuid)
        }
    }
}
