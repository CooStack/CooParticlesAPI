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
import net.minecraft.world.entity.player.Player
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
    private val dirtyEmitters = ConcurrentHashMap.newKeySet<UUID>()

    /**
     * 客户端可视
     */
    val clientEmitters = ConcurrentHashMap<UUID, ParticleEmitters>()

    /** 返回客户端当前可见 Emitter 数。 */
    fun clientEmitterCount(): Int = clientEmitters.size

    /** 返回服务端当前 Emitter 数。 */
    fun serverEmitterCount(): Int = serverEmitters.size

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
        dirtyEmitters.remove(emitters.uuid)
    }

    fun createClient(emitters: ParticleEmitters, viewWorld: Level) {
        emitters.world = viewWorld
        clientEmitters.remove(emitters.uuid)?.let { previous ->
            previous.canceled = true
            finishClientSystems(previous)
            CooEventBus.call(EmitterRemoveEvent(previous, true))
        }
        emitters.canceled = false
        emitters.playing = false
        clientEmitters[emitters.uuid] = emitters
        emitters.start()
        CooEventBus.call(EmitterSpawnEvent(emitters, true))
    }

    fun changeClient(emitters: ParticleEmitters, viewWorld: Level) {
        val current = clientEmitters[emitters.uuid] ?: return
        if (current.canceled) return
        emitters.world = viewWorld
        current.update(emitters)
        current.world = viewWorld
    }

    fun removeClient(uuid: UUID) {
        val emitters = clientEmitters.remove(uuid) ?: return
        emitters.canceled = true
        finishClientSystems(emitters)
        CooEventBus.call(EmitterRemoveEvent(emitters, true))
    }

    fun createOrChangeClient(emitters: ParticleEmitters, viewWorld: Level) {
        if (clientEmitters.containsKey(emitters.uuid)) {
            changeClient(emitters, viewWorld)
        } else {
            createClient(emitters, viewWorld)
        }
    }

    fun doTickServer() {
        val iterator = serverEmitters.iterator()
        while (iterator.hasNext()) {
            val emitter = iterator.next()
            val emitters = emitter.value
            if (emitters.canceled) {
                dirtyEmitters.remove(emitters.uuid)
                val players = filterVisiblePlayer(emitters)
                if (players.isEmpty()) {
                    iterator.remove()
                    continue
                }
                val packet = createRemovePacket(emitters)
                players.forEach {
                    val player = emitters.world!!.getPlayerByUUID(it) ?: return@forEach
                    CooParticlesServices.SERVER_NETWORK.send(packet, player as ServerPlayer)
                    visible[it]?.remove(emitters)
                }
                if (players.isNotEmpty()) {
                    CooEventBus.call(EmitterRemoveEvent(emitters, false))
                }
                iterator.remove()
                continue
            }
            updateClientVisible(emitters)
            emitters.tick()
            if (dirtyEmitters.remove(emitters.uuid)) {
                sendUpdate(emitters)
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

    /**
     * 玩家离开服务器时清理其 Emitter 可见缓存，使重连后重新发送创建包。
     *
     * @param player 已断开连接的服务端玩家
     */
    fun clearVisibleFor(player: Player) {
        visible.remove(player.uuid)
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
            val shouldView = p.position().distanceTo(emitters.pos) <= 256.0
            if (!shouldView) {
                if (emitters in visibleSet) {
                    removeView(p, emitters)
                    visibleSet.remove(emitters)
                }
                return@forEach
            }
            if (emitters in visibleSet) {
                return@forEach
            }
            addView(p, emitters)
            visibleSet.add(emitters)
        }
    }

    internal fun enqueueDirty(emitters: ParticleEmitters) {
        if (serverEmitters[emitters.uuid] === emitters && !emitters.canceled) {
            dirtyEmitters.add(emitters.uuid)
        }
    }

    private fun sendUpdate(emitters: ParticleEmitters) {
        if (emitters.canceled) {
            return
        }
        val players = filterVisiblePlayer(emitters)
        if (players.isEmpty()) {
            return
        }
        val data = encodeEmittersToArray(emitters)
        val packet = PacketParticleEmittersS2C(
            emitters.getEmittersID(),
            emitters.uuid,
            data,
            PacketParticleEmittersS2C.PacketType.CHANGE
        )
        players.forEach {
            val player = emitters.world!!.getPlayerByUUID(it) ?: return@forEach
            CooParticlesServices.SERVER_NETWORK.send(packet, player as ServerPlayer)
        }
    }

    fun sendChange(emitters: ParticleEmitters, to: ServerPlayer) {
        val data = encodeEmittersToArray(emitters)
        val packet = PacketParticleEmittersS2C(
            emitters.getEmittersID(),
            emitters.uuid,
            data,
            PacketParticleEmittersS2C.PacketType.CHANGE
        )
        CooParticlesServices.SERVER_NETWORK.send(packet, to)
    }

    private fun addView(player: ServerPlayer, emitters: ParticleEmitters) {
        val data = encodeEmittersToArray(emitters)

        val packet = PacketParticleEmittersS2C(
            emitters.getEmittersID(),
            emitters.uuid,
            data,
            PacketParticleEmittersS2C.PacketType.CREATE
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
        dirtyEmitters.clear()
    }

    private fun removeView(player: ServerPlayer, emitters: ParticleEmitters) {
        CooParticlesServices.SERVER_NETWORK.send(createRemovePacket(emitters), player)
        CooEventBus.call(EmitterRemoveEvent(emitters, false))
    }

    private fun createRemovePacket(emitters: ParticleEmitters): PacketParticleEmittersS2C {
        return PacketParticleEmittersS2C(
            emitters.getEmittersID(),
            emitters.uuid,
            ByteArray(0),
            PacketParticleEmittersS2C.PacketType.REMOVE
        )
    }


    private fun encodeEmittersToArray(emitters: ParticleEmitters): ByteArray {
        val codec = emitters.getCodec()
        val registryAccess = emitters.world?.registryAccess() ?: CooParticlesAPI.registryAccessOrNull ?: return ByteArray(0)
        val buf = RegistryFriendlyByteBuf(
            Unpooled.buffer(),
            registryAccess
        )
        return try {
            codec.encode(buf, emitters)
            ByteArray(buf.readableBytes()).also { buf.readBytes(it) }
        } finally {
            buf.release()
        }
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
