package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.CooParticleAPI
import cn.coostack.cooparticlesapi.network.packet.PacketParticleEmittersS2C
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.DefendClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.ExampleClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.ExplodeClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.FireClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.LightningClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.PhysicsParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.PresetTestEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.SimpleParticleEmitters
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.world.World
import java.util.HashSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ParticleEmittersManager {
    // 已经start的 emitters
    val emittersCodec = HashMap<String, PacketCodec<RegistryByteBuf, ParticleEmitters>>()

    /**
     * 服务器拥有
     */
    val serverEmitters = HashMap<UUID, ParticleEmitters>()
    internal val visible = ConcurrentHashMap<UUID, MutableSet<ParticleEmitters>>()

    /**
     * 客户端可视
     */
    val clientEmitters = ConcurrentHashMap<UUID, ParticleEmitters>()

    internal fun getCodecFromID(id: String): PacketCodec<RegistryByteBuf, ParticleEmitters>? {
        return emittersCodec[id]
    }


    /**
     * 在客户端执行
     */
    fun register(
        id: String,
        codec: PacketCodec<RegistryByteBuf, ParticleEmitters>
    ): PacketCodec<RegistryByteBuf, ParticleEmitters> {
        emittersCodec[id] = codec
        return codec
    }


    fun spawnEmitters(emitters: ParticleEmitters) {
        if (emitters.world == null) return
        if (emitters.world!!.isClient) return
        serverEmitters[emitters.uuid] = emitters
        emitters.start()
        updateClientVisible(emitters)
    }

    fun createOrChangeClient(emitters: ParticleEmitters, viewWorld: World) {
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
            emitters.tick()
            updateClientVisible(emitter.value)
            if (emitter.value.cancelled) {
                filterVisiblePlayer(emitters).forEach {
                    val player = emitters.world!!.getPlayerByUuid(it) ?: return@forEach
                    removeView(player as ServerPlayerEntity, emitters)
                    visible[it]?.remove(emitters)
                }
                iterator.remove()
            }
        }
    }

    fun doTickClient() {
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
        CooParticleAPI.server.playerManager.playerList.forEach { p ->
            val visibleSet = visible.getOrPut(p.uuid) { HashSet() }
            if (p.world != emitters.world) {
                // 世界转换
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
            val player = emitters.world!!.getPlayerByUuid(it) ?: return@forEach
            val packet = PacketParticleEmittersS2C(
                emitters,
                emitters.getEmittersID(),
                PacketParticleEmittersS2C.PacketType.CHANGE_OR_CREATE
            )
            ServerPlayNetworking.send(player as ServerPlayerEntity, packet)
        }
    }

    fun sendChange(emitters: ParticleEmitters, to: ServerPlayerEntity) {
        val packet = PacketParticleEmittersS2C(
            emitters,
            emitters.getEmittersID(),
            PacketParticleEmittersS2C.PacketType.CHANGE_OR_CREATE
        )
        ServerPlayNetworking.send(to, packet)
    }

    private fun addView(player: ServerPlayerEntity, emitters: ParticleEmitters) {
        val packet = PacketParticleEmittersS2C(
            emitters,
            emitters.getEmittersID(),
            PacketParticleEmittersS2C.PacketType.CHANGE_OR_CREATE
        )
        ServerPlayNetworking.send(player, packet)
    }

    private fun removeView(player: ServerPlayerEntity, emitters: ParticleEmitters) {
        val packet = PacketParticleEmittersS2C(
            emitters,
            emitters.getEmittersID(),
            PacketParticleEmittersS2C.PacketType.REMOVE
        )
        ServerPlayNetworking.send(player, packet)
    }


    internal fun init() {
        register(PhysicsParticleEmitters.ID, PhysicsParticleEmitters.CODEC)
        register(SimpleParticleEmitters.ID, SimpleParticleEmitters.CODEC)
        register(ExampleClassParticleEmitters.ID, ExampleClassParticleEmitters.CODEC)
        register(FireClassParticleEmitters.ID, FireClassParticleEmitters.CODEC)
        register(ExplodeClassParticleEmitters.ID, ExplodeClassParticleEmitters.CODEC)
        register(LightningClassParticleEmitters.ID, LightningClassParticleEmitters.CODEC)
        register(DefendClassParticleEmitters.ID, DefendClassParticleEmitters.CODEC)
        register(PresetTestEmitters.ID, PresetTestEmitters.CODEC)
    }

}