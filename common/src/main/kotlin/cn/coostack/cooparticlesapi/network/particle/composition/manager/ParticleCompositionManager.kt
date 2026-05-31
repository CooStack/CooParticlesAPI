package cn.coostack.cooparticlesapi.network.particle.composition.manager

import cn.coostack.cooparticlesapi.CooParticlesAPI
import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.annotations.composition.handler.ParticleCompositionRegistryHelper
import cn.coostack.cooparticlesapi.network.packet.server.PacketParticleCompositionRotateS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketParticleCompositionS2C
import cn.coostack.cooparticlesapi.network.particle.composition.ParticleComposition
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import cn.coostack.cooparticlesapi.reflect.CooAPIScanner
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import io.netty.buffer.Unpooled
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.entity.player.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set
import kotlin.jvm.java

object ParticleCompositionManager {
    val clientView = ConcurrentHashMap<UUID, ParticleComposition>()

    val serverView = ConcurrentHashMap<UUID, ParticleComposition>()

    /**
     * 只有玩家可见才会处理发包
     */
    val playerPlayerVisibleSet = ConcurrentHashMap<UUID, HashSet<ParticleComposition>>()

    val registeredTypes = ConcurrentHashMap<String, StreamCodec<FriendlyByteBuf, ParticleComposition>>()

    fun addClient(composition: ParticleComposition) {
        clientView[composition.controlUUID] = composition
        composition.display()
    }

    fun spawn(composition: ParticleComposition) {
        composition.resetLifecycleForSpawn()
        removeVisibleComposition(composition)
        serverView[composition.controlUUID] = composition
        sendCreateOrUpdate(composition)
        composition.display()
    }


    fun register(randomInstance: ParticleComposition) {
        val id = randomInstance::class.java.name
        val codec = randomInstance.getCodec()
        registeredTypes[id] = codec
    }

    fun register(type: Class<out ParticleComposition>) {
        registeredTypes[type.name] = ParticleCompositionRegistryHelper.generateCodec(type)
    }

    fun registerScanner() {
        val start = System.currentTimeMillis()
        CooParticlesConstants.logger.info("正在自动注册 Compositions")
        CooAPIScanner.getWithAnnotation(CooAutoRegister::class.java)
            .iterator()
            .forEach {
                val clazz = it.toClass()
                if (!ParticleComposition::class.java.isAssignableFrom(clazz)) {
                    return@forEach
                }
                @Suppress("UNCHECKED_CAST")
                register(clazz as Class<out ParticleComposition>)
            }
        val end = System.currentTimeMillis()
        CooParticlesConstants.logger.info("Compositions 注册完成 耗时 ${end - start} ms")
    }


    fun tickClient() {
        val iterator = clientView.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.canceled) {
                iterator.remove()
                entry.value.remove()
                continue
            }
            entry.value.tick()
        }
    }

    fun tickServer() {
        val iterator = serverView.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.canceled) {
                iterator.remove()
                sendRemove(entry.value)
                continue
            }
            sendCreateOrUpdate(entry.value)
            entry.value.tick()
        }
    }

    fun removeVisibleComposition(composition: ParticleComposition) {
        playerPlayerVisibleSet.values.forEach { compositions ->
            compositions.remove(composition)
        }
    }

    fun clearVisibleFor(player: Player) {
        playerPlayerVisibleSet.remove(player.uuid)
    }

    fun sendCreateOrUpdate(composition: ParticleComposition) {
        val server = CooParticlesAPI.server
        val uuid = composition.controlUUID
        val type = composition::class.java.name
        val buf = RegistryFriendlyByteBuf(Unpooled.buffer(), CooParticlesAPI.registryAccess)
        registeredTypes[composition::class.java.name]!!
            .encode(buf, composition)
        val data = ByteArray(buf.readableBytes()).apply {
            buf.readBytes(this)
        }
        val packet = PacketParticleCompositionS2C(uuid, type, data)
        server.playerList.players.forEach {
            if (it.level().dimension() != composition.world?.dimension()) {
                return@forEach
            }

            val compositions = playerPlayerVisibleSet.getOrPut(it.uuid) { HashSet() }
            val shouldJoinOrUpdate = composition.position.distanceTo(it.position()) <= composition.visibleRange
            if (compositions.contains(composition)) {
                if (shouldJoinOrUpdate) {
                    CooParticlesServices.SERVER_NETWORK.send(packet, it)
                } else {
                    // remove
                    compositions.remove(composition)
                    packet.distanceRemove = true
                    CooParticlesServices.SERVER_NETWORK.send(packet, it)
                }
            } else if (shouldJoinOrUpdate) {
                // join
                compositions.add(composition)
                // 发包
                CooParticlesServices.SERVER_NETWORK.send(packet, it)
            }
        }
    }

    fun sendRemove(composition: ParticleComposition) {
        val server = CooParticlesAPI.server
        val uuid = composition.controlUUID
        val type = composition::class.java.name
        val packet = PacketParticleCompositionS2C(uuid, type, ByteArray(0)).apply {
            distanceRemove = true
        }
        server.playerList.players.forEach { player ->
            val compositions = playerPlayerVisibleSet[player.uuid] ?: return@forEach
            if (compositions.remove(composition)) {
                CooParticlesServices.SERVER_NETWORK.send(packet, player)
            }
        }
    }

    fun sendRotate(composition: ParticleComposition, direction: RelativeLocation?, rollDelta: Double) {
        if (!composition.displayed || composition.canceled) {
            return
        }
        val server = CooParticlesAPI.server
        val packet = PacketParticleCompositionRotateS2C(
            composition.controlUUID,
            direction?.toVector(),
            rollDelta
        )
        server.playerList.players.forEach {
            if (it.level().dimension() != composition.world?.dimension()) {
                return@forEach
            }
            val compositions = playerPlayerVisibleSet[it.uuid] ?: return@forEach
            if (composition in compositions) {
                CooParticlesServices.SERVER_NETWORK.send(packet, it)
            }
        }
    }

    fun clearClient() {
        // 这里必须走 clear(true) 强制销毁
        // remove() 可能被使用者重写成延迟消散的语义 (例如先 status.disable() 等渐隐结束再真正销毁)
        // 客户端断连/换世界要求立刻干净 否则 composition 会脱离 clientView 变成孤儿粒子
        clientView.values.forEach {
            it.clear(true)
        }
        clientView.clear()
    }


    fun clearServer() {
        // 同 clearClient 不能依赖使用者重写的 remove()
        serverView.onEach {
            it.value.clear(true)
        }.clear()
        playerPlayerVisibleSet.clear()
    }

}
