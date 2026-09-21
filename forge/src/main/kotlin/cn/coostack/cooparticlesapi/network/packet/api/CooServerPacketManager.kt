package cn.coostack.cooparticlesapi.network.packet.api

import cn.coostack.cooparticlesapi.CooParticlesAPI
import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.packet.CooPacketRegistry
import cn.coostack.cooparticlesapi.event.CooEventBus
import cn.coostack.cooparticlesapi.event.events.packet.CooPacketReceiveEvent
import cn.coostack.cooparticlesapi.event.events.packet.CooPacketRequestTimeoutEvent
import cn.coostack.cooparticlesapi.event.events.packet.CooPacketSendEvent
import cn.coostack.cooparticlesapi.network.packet.api.envelope.CooPacketEnvelopeC2S
import cn.coostack.cooparticlesapi.network.packet.api.envelope.CooPacketEnvelopeS2C
import cn.coostack.cooparticlesapi.performance.PerformanceStatusNetworkEndpoint
import cn.coostack.cooparticlesapi.performance.PerformanceStatusNetworkMetrics
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object CooServerPacketManager {
    const val DEFAULT_TIMEOUT_TICKS = 60

    private val correlationCounter = AtomicLong(1L)

    private class Pending(
        val expectType: Class<out CooPacket>,
        val callback: (ServerPlayer, CooPacket) -> Unit,
        val targetPlayerUUID: UUID?,
        val requestPacket: CooPacket,
        var remainingTicks: Int,
        val totalTicks: Int,
    )

    private val pending = ConcurrentHashMap<Long, Pending>()

    @JvmStatic
    fun sendTo(player: ServerPlayer, packet: CooPacket): Boolean {
        return sendInternal(player, packet, CooPacketKind.NORMAL, 0L, 0)
    }

    @JvmStatic
    fun sendAll(packet: CooPacket) {
        val server = CooParticlesAPI.serverOrNull ?: return
        server.playerList.players.forEach { sendTo(it, packet) }
    }

    @JvmStatic
    fun sendWorlds(worlds: Iterable<ServerLevel>, packet: CooPacket) {
        worlds.forEach { world ->
            world.players().forEach { sendTo(it, packet) }
        }
    }

    @JvmStatic
    fun sendWorlds(world: ServerLevel, packet: CooPacket) {
        sendWorlds(listOf(world), packet)
    }

    @JvmStatic
    @JvmOverloads
    fun <R : CooPacket> request(
        player: ServerPlayer,
        packet: CooPacket,
        responseType: Class<R>,
        timeoutTicks: Int = DEFAULT_TIMEOUT_TICKS,
        onResponse: (R) -> Unit,
    ): Long {
        return requestWithSender(player, packet, responseType, timeoutTicks) { _, resp -> onResponse(resp) }
    }

    @JvmSynthetic
    inline fun <reified R : CooPacket> request(
        player: ServerPlayer,
        packet: CooPacket,
        timeoutTicks: Int = DEFAULT_TIMEOUT_TICKS,
        noinline onResponse: (R) -> Unit,
    ): Long = request(player, packet, R::class.java, timeoutTicks, onResponse)

    @JvmStatic
    fun <R : CooPacket> requestWithSender(
        player: ServerPlayer,
        packet: CooPacket,
        responseType: Class<R>,
        timeoutTicks: Int,
        onResponse: (ServerPlayer, R) -> Unit,
    ): Long {
        val correlationId = correlationCounter.getAndIncrement()
        @Suppress("UNCHECKED_CAST")
        pending[correlationId] = Pending(
            expectType = responseType,
            callback = { sender, p -> onResponse(sender, p as R) },
            targetPlayerUUID = player.uuid,
            requestPacket = packet,
            remainingTicks = timeoutTicks,
            totalTicks = timeoutTicks,
        )
        val ok = sendInternal(player, packet, CooPacketKind.REQUEST, correlationId, timeoutTicks)
        if (!ok) {
            pending.remove(correlationId)
            return 0L
        }
        return correlationId
    }

    @JvmStatic
    @JvmOverloads
    fun <R : CooPacket> requestAll(
        packet: CooPacket,
        responseType: Class<R>,
        timeoutTicks: Int = DEFAULT_TIMEOUT_TICKS,
        onResponse: (ServerPlayer, R) -> Unit,
    ): List<Long> {
        val server = CooParticlesAPI.serverOrNull ?: return emptyList()
        return server.playerList.players.map { player ->
            requestWithSender(player, packet, responseType, timeoutTicks, onResponse)
        }
    }

    @JvmSynthetic
    inline fun <reified R : CooPacket> requestAll(
        packet: CooPacket,
        timeoutTicks: Int = DEFAULT_TIMEOUT_TICKS,
        noinline onResponse: (ServerPlayer, R) -> Unit,
    ): List<Long> = requestAll(packet, R::class.java, timeoutTicks, onResponse)

    @JvmStatic
    @JvmOverloads
    fun <R : CooPacket> requestWorlds(
        worlds: Iterable<ServerLevel>,
        packet: CooPacket,
        responseType: Class<R>,
        timeoutTicks: Int = DEFAULT_TIMEOUT_TICKS,
        onResponse: (ServerPlayer, R) -> Unit,
    ): List<Long> {
        val ids = ArrayList<Long>()
        worlds.forEach { world ->
            world.players().forEach { player ->
                ids.add(requestWithSender(player, packet, responseType, timeoutTicks, onResponse))
            }
        }
        return ids
    }

    @JvmSynthetic
    inline fun <reified R : CooPacket> requestWorlds(
        worlds: Iterable<ServerLevel>,
        packet: CooPacket,
        timeoutTicks: Int = DEFAULT_TIMEOUT_TICKS,
        noinline onResponse: (ServerPlayer, R) -> Unit,
    ): List<Long> = requestWorlds(worlds, R::class.java, timeoutTicks, onResponse)

    @JvmStatic
    fun cancelRequest(correlationId: Long): Boolean {
        return pending.remove(correlationId) != null
    }

    @JvmStatic
    fun tick() {
        if (pending.isEmpty()) return
        val expired = ArrayList<Pair<Long, Pending>>()
        val it = pending.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            val p = entry.value
            p.remainingTicks--
            if (p.remainingTicks <= 0) {
                expired.add(entry.key to p)
                it.remove()
            }
        }
        if (expired.isEmpty()) return
        val server = CooParticlesAPI.serverOrNull
        expired.forEach { (id, p) ->
            val target = if (server != null && p.targetPlayerUUID != null) {
                server.playerList.getPlayer(p.targetPlayerUUID)
            } else null
            CooEventBus.call(
                CooPacketRequestTimeoutEvent(
                    requestPacket = p.requestPacket,
                    side = CooPacketRequestTimeoutEvent.Side.SERVER_TO_CLIENT,
                    targetPlayer = target,
                    correlationId = id,
                    timeoutTicks = p.totalTicks,
                )
            )
        }
    }

    @JvmStatic
    fun handleC2S(envelope: CooPacketEnvelopeC2S, sender: ServerPlayer) {
        PerformanceStatusNetworkMetrics.recordReceived(
            PerformanceStatusNetworkEndpoint.SERVER,
            envelope.data.size,
        )
        val server = sender.server
        server.execute {
            handleC2SInternal(envelope, sender)
        }
    }

    private fun handleC2SInternal(envelope: CooPacketEnvelopeC2S, sender: ServerPlayer) {
        val kind = CooPacketKind.fromId(envelope.kindId)
        val packet = CooPacketRegistry.decode(envelope.packetId, envelope.data)
        if (packet == null) {
            CooParticlesConstants.logger.warn(
                "Received unknown CooPacket: ${envelope.packetId} (kind=$kind, sender=${sender.gameProfile.name})"
            )
            return
        }
        val event = CooEventBus.call(
            CooPacketReceiveEvent(
                packet = packet,
                kind = kind,
                side = CooPacketReceiveEvent.Side.SERVER,
                sender = sender,
                correlationId = envelope.correlationId,
                timeoutTicks = envelope.timeoutTicks,
            )
        )
        if (event.isCancelled) return

        val ctx = ServerContext(sender, packet, kind, envelope.correlationId, envelope.timeoutTicks)
        try {
            packet.onServerReceive(ctx)
        } catch (e: Throwable) {
            CooParticlesConstants.logger.error("CooPacket onServerReceive exception: ${envelope.packetId}", e)
        }

        if (kind == CooPacketKind.RESPONSE) {
            val pendingEntry = pending.remove(envelope.correlationId) ?: return
            if (!pendingEntry.expectType.isInstance(packet)) {
                CooParticlesConstants.logger.warn(
                    "CooPacket response type mismatch: expected ${pendingEntry.expectType.name}, got ${packet::class.java.name}"
                )
                return
            }
            try {
                pendingEntry.callback(sender, packet)
            } catch (e: Throwable) {
                CooParticlesConstants.logger.error(
                    "CooPacket request callback exception (correlationId=${envelope.correlationId})",
                    e
                )
            }
        }
    }

    internal fun replyInternal(target: ServerPlayer, response: CooPacket, correlationId: Long) {
        sendInternal(target, response, CooPacketKind.RESPONSE, correlationId, 0)
    }

    private fun sendInternal(
        player: ServerPlayer,
        packet: CooPacket,
        kind: CooPacketKind,
        correlationId: Long,
        timeoutTicks: Int,
    ): Boolean {
        if (!CooPacketRegistry.isRegistered(packet::class.java)) {
            CooParticlesConstants.logger.error(
                "CooPacket not registered, cannot send: ${packet::class.java.name} (id=${packet.id()})"
            )
            return false
        }
        val event = CooEventBus.call(
            CooPacketSendEvent(
                packet = packet,
                kind = kind,
                side = CooPacketSendEvent.Side.SERVER_TO_CLIENT,
                targetPlayer = player,
                correlationId = correlationId,
                timeoutTicks = timeoutTicks,
            )
        )
        if (event.isCancelled) return false
        val data = try {
            CooPacketRegistry.encode(packet)
        } catch (e: Throwable) {
            CooParticlesConstants.logger.error("CooPacket encode failed: ${packet::class.java.name}", e)
            return false
        }
        val envelope = CooPacketEnvelopeS2C(
            kindId = kind.id,
            packetId = packet.id(),
            correlationId = correlationId,
            timeoutTicks = timeoutTicks,
            data = data,
        )
        CooParticlesServices.SERVER_NETWORK.send(envelope, player)
        PerformanceStatusNetworkMetrics.recordSent(
            PerformanceStatusNetworkEndpoint.SERVER,
            data.size,
        )
        return true
    }
}
