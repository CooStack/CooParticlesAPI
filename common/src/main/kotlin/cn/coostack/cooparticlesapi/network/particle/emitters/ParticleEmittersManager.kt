package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.CooParticlesAPI
import cn.coostack.cooparticlesapi.network.packet.PacketParticleEmittersS2C
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.DefendClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.ExampleClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.ExplodeClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.FireClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.LightningClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.PhysicsParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.PresetLaserEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.PresetTestEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.SimpleParticleEmitters
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import cn.coostack.cooparticlesapi.test.particle.emitter.TestEmitter
import cn.coostack.cooparticlesapi.test.particle.emitter.TestEventEmitter
import io.netty.buffer.Unpooled
import net.minecraft.client.Minecraft
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import java.util.HashSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ParticleEmittersManager {
    // 已经start的 emitters
    val emittersCodec = HashMap<String, StreamCodec<FriendlyByteBuf, ParticleEmitters>>()

    /**
     * 服务器拥有
     */
    val serverEmitters = HashMap<UUID, ParticleEmitters>()
    internal val visible = ConcurrentHashMap<UUID, MutableSet<ParticleEmitters>>()

    /**
     * 客户端可视
     */
    val clientEmitters = ConcurrentHashMap<UUID, ParticleEmitters>()

    fun getCodecFromID(id: String): StreamCodec<FriendlyByteBuf, ParticleEmitters>? {
        return emittersCodec[id]
    }


    /**
     * 在客户端执行
     */
    fun register(
        id: String,
        codec: StreamCodec<FriendlyByteBuf, ParticleEmitters>
    ): StreamCodec<FriendlyByteBuf, ParticleEmitters> {
        emittersCodec[id] = codec
        return codec
    }


    fun addEmitters(emitters: ParticleEmitters) {
        if (emitters.world == null) return
        if (!emitters.world!!.isClientSide) return
        clientEmitters[emitters.uuid] = emitters
        emitters.start()
    }

    fun spawnEmitters(emitters: ParticleEmitters) {
        if (emitters.world == null) return
        if (emitters.world!!.isClientSide) return
        serverEmitters[emitters.uuid] = emitters
        emitters.start()
        updateClientVisible(emitters)
    }

    fun createOrChangeClient(emitters: ParticleEmitters, viewWorld: Level) {
        if (emitters.cancelled) {
            clientEmitters.remove(emitters.uuid)
            return
        }
        if (clientEmitters.containsKey(emitters.uuid)) {
            clientEmitters[emitters.uuid]!!.apply {
                update(emitters)
                world = viewWorld
            }
        } else {
            clientEmitters[emitters.uuid] = emitters
        }

    }

    fun doTickServer() {
        val iterator = serverEmitters.iterator()
        while (iterator.hasNext()) {
            val emitter = iterator.next()
            val emitters = emitter.value
            updateClientVisible(emitter.value)
            emitters.tick()
            if (emitter.value.cancelled) {
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
            clientEmitters.clear()
            return
        }
        val iterator = clientEmitters.iterator()
        while (iterator.hasNext()) {
            val emitters = iterator.next().value
            emitters.tick()
            if (emitters.cancelled) {
                iterator.remove()
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
        CooParticlesAPI.server.playerList.players.forEach { p ->
            val visibleSet = visible.getOrPut(p.uuid) { HashSet() }
            if (p.level() != emitters.world) {
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
            val buf = FriendlyByteBuf(Unpooled.buffer())
            emitters.getCodec().encode(buf, emitters)
            val packet = PacketParticleEmittersS2C(
                buf,
                emitters.getEmittersID(),
                PacketParticleEmittersS2C.PacketType.CHANGE_OR_CREATE
            )
            CooParticlesServices.SERVER_NETWORK.send(packet, player as ServerPlayer)
        }
    }

    fun sendChange(emitters: ParticleEmitters, to: ServerPlayer) {
        val buf = FriendlyByteBuf(Unpooled.buffer())
        emitters.getCodec().encode(buf, emitters)
        val packet = PacketParticleEmittersS2C(
            buf,
            emitters.getEmittersID(),
            PacketParticleEmittersS2C.PacketType.CHANGE_OR_CREATE
        )
        CooParticlesServices.SERVER_NETWORK.send(packet, to)
    }

    private fun addView(player: ServerPlayer, emitters: ParticleEmitters) {
        val buf = FriendlyByteBuf(Unpooled.buffer())
        emitters.getCodec().encode(buf, emitters)
        val packet = PacketParticleEmittersS2C(
            buf,
            emitters.getEmittersID(),
            PacketParticleEmittersS2C.PacketType.CHANGE_OR_CREATE
        )
        CooParticlesServices.SERVER_NETWORK.send(packet, player)
    }

    fun clearAllVisible() {
        clientEmitters.onEach { it.value.cancelled = true }
            .clear()
    }

    private fun removeView(player: ServerPlayer, emitters: ParticleEmitters) {
        val buf = FriendlyByteBuf(Unpooled.buffer())
        emitters.getCodec().encode(buf, emitters)
        val packet = PacketParticleEmittersS2C(
            buf,
            emitters.getEmittersID(),
            PacketParticleEmittersS2C.PacketType.REMOVE
        )
        CooParticlesServices.SERVER_NETWORK.send(packet, player)
    }


    internal fun init() {
        register(PhysicsParticleEmitters.ID, PhysicsParticleEmitters.CODEC)
        register(SimpleParticleEmitters.ID, SimpleParticleEmitters.CODEC)
        register(ExampleClassParticleEmitters.ID, ExampleClassParticleEmitters.CODEC)
        register(ExplodeClassParticleEmitters.ID, ExplodeClassParticleEmitters.CODEC)
        register(LightningClassParticleEmitters.ID, LightningClassParticleEmitters.CODEC)
        register(DefendClassParticleEmitters.ID, DefendClassParticleEmitters.CODEC)
        register(PresetTestEmitters.ID, PresetTestEmitters.CODEC)
        register(FireClassParticleEmitters.ID, FireClassParticleEmitters.CODEC)
        register(PresetLaserEmitters.ID, PresetLaserEmitters.CODEC)
        register(TestEventEmitter.ID, TestEventEmitter.CODEC)
        register(TestEmitter.ID, TestEmitter.CODEC)
    }

}