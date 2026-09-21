package cn.coostack.cooparticlesapi.network.particle.composition.manager

import cn.coostack.cooparticlesapi.CooParticlesAPI
import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.annotations.composition.handler.ParticleCompositionRegistryHelper
import cn.coostack.cooparticlesapi.network.packet.server.PacketParticleCompositionRotateS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketParticleCompositionS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketParticleCompositionStateS2C
import cn.coostack.cooparticlesapi.network.particle.composition.ParticleComposition
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import cn.coostack.cooparticlesapi.reflect.CooAPIScanner
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import io.netty.buffer.Unpooled
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set
import kotlin.jvm.java

object ParticleCompositionManager {
    private val loadedClientCompositions = WeakIdentitySet<ParticleComposition>()

    val clientView = ConcurrentHashMap<UUID, ParticleComposition>()

    val serverView = ConcurrentHashMap<UUID, ParticleComposition>()

    val playerPlayerVisibleSet = ConcurrentHashMap<UUID, HashSet<ParticleComposition>>()

    val registeredTypes = ConcurrentHashMap<String, ForgeStreamCodec<PacketByteBuf, ParticleComposition>>()

    internal fun setClientLoaded(composition: ParticleComposition, loaded: Boolean) {
        if (loaded) {
            loadedClientCompositions.add(composition)
        } else {
            loadedClientCompositions.remove(composition)
        }
    }

    @JvmStatic
    fun loadedClientCount(): Int = loadedClientCompositions.size()

    internal fun debugCompositions(): List<ParticleComposition> = loadedClientCompositions.snapshot()

    fun loadedServerCount(): Int = serverView.size

    fun addClient(composition: ParticleComposition) {
        clientView[composition.controlUUID] = composition
        composition.display()
    }

    fun spawn(composition: ParticleComposition) {
        composition.resetLifecycleForSpawn()
        removeVisibleComposition(composition)
        serverView[composition.controlUUID] = composition
        composition.display()
        sendCreateOrUpdate(composition)
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
            syncVisible(entry.value, false)
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
        syncVisible(composition, true)
    }

    private fun syncVisible(composition: ParticleComposition, forceUpdate: Boolean) {
        val server = CooParticlesAPI.serverOrNull ?: return
        val createTargets = ArrayList<ServerPlayer>()
        val updateTargets = ArrayList<ServerPlayer>()
        val removeTargets = ArrayList<ServerPlayer>()
        server.playerList.players.forEach { player ->
            val compositions = playerPlayerVisibleSet.getOrPut(player.uuid) { HashSet() }
            val shouldView = player.level().dimension() == composition.world?.dimension() &&
                composition.position.distanceTo(player.position()) <= composition.visibleRange
            if (composition in compositions) {
                if (shouldView) {
                    updateTargets.add(player)
                } else {
                    compositions.remove(composition)
                    removeTargets.add(player)
                }
            } else if (shouldView) {
                createTargets.add(player)
            }
        }

        if (removeTargets.isNotEmpty()) {
            val removePacket = PacketParticleCompositionS2C(
                composition.controlUUID,
                composition::class.java.name,
                ByteArray(0)
            ).apply {
                distanceRemove = true
            }
            removeTargets.forEach { CooParticlesServices.SERVER_NETWORK.send(removePacket, it) }
        }

        if (createTargets.isEmpty() && updateTargets.isEmpty()) {
            return
        }
        val fullDirty = composition.hasNetworkFullDirty()
        val stateDirty = composition.hasNetworkStateDirty()
        if (createTargets.isEmpty() && !forceUpdate && !fullDirty) {
            if (stateDirty) {
                val statePacket = PacketParticleCompositionStateS2C(
                    composition.controlUUID,
                    composition.position,
                    composition.visibleRange,
                    composition.scale,
                    composition.status.displayStatus,
                    composition.status.closedInternal,
                    composition.status.current,
                )
                updateTargets.forEach { CooParticlesServices.SERVER_NETWORK.send(statePacket, it) }
                composition.consumeNetworkStateDirty()
            }
            return
        }
        val registryAccess = CooParticlesAPI.registryAccessOrNull ?: return
        val type = composition::class.java.name
        val buf = PacketByteBuf(Unpooled.buffer())
        val data = try {
            registeredTypes[type]!!.encode(buf, composition)
            ByteArray(buf.readableBytes()).also { buf.readBytes(it) }
        } finally {
            buf.release()
        }
        if (createTargets.isNotEmpty()) {
            val createPacket = PacketParticleCompositionS2C(composition.controlUUID, type, data).apply {
                recreate = true
            }
            createTargets.forEach { player ->
                CooParticlesServices.SERVER_NETWORK.send(createPacket, player)
                playerPlayerVisibleSet.getValue(player.uuid).add(composition)
            }
        }
        if (forceUpdate || fullDirty) {
            val updatePacket = PacketParticleCompositionS2C(composition.controlUUID, type, data)
            updateTargets.forEach { CooParticlesServices.SERVER_NETWORK.send(updatePacket, it) }
        } else if (stateDirty) {
            val statePacket = PacketParticleCompositionStateS2C(
                composition.controlUUID,
                composition.position,
                composition.visibleRange,
                composition.scale,
                composition.status.displayStatus,
                composition.status.closedInternal,
                composition.status.current,
            )
            updateTargets.forEach { CooParticlesServices.SERVER_NETWORK.send(statePacket, it) }
        }
        if (forceUpdate || fullDirty || createTargets.isNotEmpty()) {
            composition.consumeNetworkFullDirty()
        }
        if (stateDirty) {
            composition.consumeNetworkStateDirty()
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

    fun clearClient() {
        clientView.values.forEach {
            it.clear(true)
        }
        clientView.clear()
        loadedClientCompositions.clear()
    }

    fun clearServer() {
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
    fun snapshot(): List<T> {
        removeCollectedReferences()
        return references.mapNotNull { it.get() }
    }

    @Synchronized
    fun clear() {
        references.clear()
        while (collectedReferences.poll() != null) {
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
