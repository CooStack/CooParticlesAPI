package cn.coostack.cooparticlesapi.network.particle.composition.manager

import cn.coostack.cooparticlesapi.CooParticlesAPI
import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.network.packet.PacketParticleCompositionS2C
import cn.coostack.cooparticlesapi.network.particle.composition.ParticleComposition
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import cn.coostack.cooparticlesapi.reflect.CooAPIScanner
import io.netty.buffer.Unpooled
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
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
        serverView[composition.controlUUID] = composition
        sendCreateOrUpdate(composition)
        composition.display()
    }


    fun register(randomInstance: ParticleComposition) {
        val id = randomInstance::class.java.name
        val codec = randomInstance.getCodec()
        registeredTypes[id] = codec
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
                // 获取instance
                val instance =
                    clazz.declaredConstructors.find {
                        it.parameterCount == 0
                    }?.newInstance() ?: clazz.getDeclaredConstructor(
                        Vec3::class.java,
                        Level::class.java
                    )
                        .newInstance(Vec3.ZERO, null)
                register(instance as ParticleComposition)
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
                sendCreateOrUpdate(entry.value)
                continue
            }
            sendCreateOrUpdate(entry.value)
            entry.value.tick()
        }
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
            if (it.level() != composition.world) {
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

    fun clearClient() {
        clientView.clear()
    }


    fun clearServer() {
        serverView.onEach {
            it.value.remove()
        }.clear()
    }

}