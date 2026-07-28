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
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set
import kotlin.jvm.java

object ParticleCompositionManager {
    /**
     * 弱引用保存已进入客户端显示生命周期的 Composition 实例。
     *
     * 示例：顶层 Composition 及其直接显示的子 Composition 会分别占一个条目。
     * 禁止在此处强持有 Composition，否则 Emitter 临时创建的实例无法被回收。
     */
    private val loadedClientCompositions = WeakIdentitySet<ParticleComposition>()

    val clientView = ConcurrentHashMap<UUID, ParticleComposition>()

    val serverView = ConcurrentHashMap<UUID, ParticleComposition>()

    /**
     * 只有玩家可见才会处理发包
     */
    val playerPlayerVisibleSet = ConcurrentHashMap<UUID, HashSet<ParticleComposition>>()

    val registeredTypes = ConcurrentHashMap<String, StreamCodec<FriendlyByteBuf, ParticleComposition>>()

    /**
     * 登记或移除一个客户端活动 Composition。
     *
     * 示例：Composition 首次 `display()` 时传入 `true`，执行 `clear(true)` 时传入 `false`。
     * 禁止为服务端仅用于同步的 Composition 传入 `true`。
     *
     * @param composition 需要更新活动状态的 Composition 实例
     * @param loaded `true` 表示已加载到客户端，`false` 表示已离开客户端生命周期
     */
    internal fun setClientLoaded(composition: ParticleComposition, loaded: Boolean) {
        if (loaded) {
            loadedClientCompositions.add(composition)
        } else {
            loadedClientCompositions.remove(composition)
        }
    }

    /**
     * 返回客户端当前活动的全部 Composition 实例数量。
     *
     * 示例：F3 调试信息用该值统计顶层、嵌套及 Emitter 直接显示的 Composition。
     * 禁止把该值理解为服务端实例数或已注册类型数。
     *
     * @return 当前客户端活动 Composition 数量
     */
    @JvmStatic
    fun loadedClientCount(): Int = loadedClientCompositions.size()

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
        val server = CooParticlesAPI.serverOrNull ?: return
        val registryAccess = CooParticlesAPI.registryAccessOrNull ?: return
        val uuid = composition.controlUUID
        val type = composition::class.java.name
        val buf = RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess)
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
        val server = CooParticlesAPI.serverOrNull ?: return
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
        val server = CooParticlesAPI.serverOrNull ?: return
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

    /**
     * 清理当前客户端世界持有的所有 Composition。
     *
     * 示例：客户端断线或换世界时调用该方法，将顶层与嵌套实例一并移出活动计数。
     * 禁止用该方法清理服务端 [serverView]。
     */
    fun clearClient() {
        // 这里必须走 clear(true) 强制销毁
        // remove() 可能被使用者重写成延迟消散的语义 (例如先 status.disable() 等渐隐结束再真正销毁)
        // 客户端断连/换世界要求立刻干净 否则 composition 会脱离 clientView 变成孤儿粒子
        clientView.values.forEach {
            it.clear(true)
        }
        clientView.clear()
        loadedClientCompositions.clear()
    }


    fun clearServer() {
        // 同 clearClient 不能依赖使用者重写的 remove()
        serverView.onEach {
            it.value.clear(true)
        }.clear()
        playerPlayerVisibleSet.clear()
    }

}

private class WeakIdentitySet<T : Any> {
    private val collectedReferences = ReferenceQueue<T>()
    private val references = HashSet<IdentityWeakReference<T>>()

    @Synchronized
    fun add(value: T) {
        removeCollectedReferences()
        references.add(IdentityWeakReference(value, collectedReferences))
    }

    @Synchronized
    fun remove(value: T) {
        removeCollectedReferences()
        references.remove(IdentityWeakReference(value))
    }

    @Synchronized
    fun size(): Int {
        removeCollectedReferences()
        return references.size
    }

    @Synchronized
    fun clear() {
        references.clear()
        while (collectedReferences.poll() != null) {
            // 清空队列中已经失效的引用。
        }
    }

    private fun removeCollectedReferences() {
        while (true) {
            val reference = collectedReferences.poll() ?: return
            references.remove(reference)
        }
    }
}

private class IdentityWeakReference<T : Any>(
    referent: T,
    queue: ReferenceQueue<T>? = null,
) : WeakReference<T>(referent, queue) {
    private val identityHashCode = System.identityHashCode(referent)

    override fun hashCode(): Int = identityHashCode

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IdentityWeakReference<*>) return false
        return get()?.let { referent -> referent === other.get() } ?: false
    }
}
