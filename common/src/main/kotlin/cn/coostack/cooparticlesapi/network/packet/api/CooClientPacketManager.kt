package cn.coostack.cooparticlesapi.network.packet.api

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.packet.CooPacketRegistry
import cn.coostack.cooparticlesapi.event.CooEventBus
import cn.coostack.cooparticlesapi.event.events.packet.CooPacketReceiveEvent
import cn.coostack.cooparticlesapi.event.events.packet.CooPacketRequestTimeoutEvent
import cn.coostack.cooparticlesapi.event.events.packet.CooPacketSendEvent
import cn.coostack.cooparticlesapi.network.packet.api.envelope.CooPacketEnvelopeC2S
import cn.coostack.cooparticlesapi.network.packet.api.envelope.CooPacketEnvelopeS2C
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import net.minecraft.client.Minecraft
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * # 客户端 CooPacket 管理器
 *
 * 提供:
 * - [sendTo]    发送普通包到服务器
 * - [request]   向服务器发起请求, 等待指定类型响应或超时
 * - [cancelRequest]  在响应/超时前取消等待
 *
 * 同时负责 envelope 包的解码、回调分发、超时扫描 (由 [tick] 推进)
 *
 * 默认超时为 [DEFAULT_TIMEOUT_TICKS] (60 tick = 3 秒)
 */
object CooClientPacketManager {
    const val DEFAULT_TIMEOUT_TICKS = 60

    private val correlationCounter = AtomicLong(1L)

    private class Pending(
        val expectType: Class<out CooPacket>,
        val callback: (CooPacket) -> Unit,
        val requestPacket: CooPacket,
        var remainingTicks: Int,
        val totalTicks: Int,
    )

    private val pending = ConcurrentHashMap<Long, Pending>()

    /**
     * 发送一个普通包到服务器
     *
     * @param packet 待发送的业务包
     * @return 是否发送成功 (false = 事件被取消 或 包未注册)
     */
    @JvmStatic
    fun sendTo(packet: CooPacket): Boolean {
        return sendInternal(packet, CooPacketKind.NORMAL, 0L, 0)
    }

    /**
     * 向服务器发起一个请求, 等待 [R] 类型的响应
     *
     * @param packet         请求包
     * @param timeoutTicks   超时 tick 数, 默认 [DEFAULT_TIMEOUT_TICKS]
     * @param responseType   响应包类型 (用于运行时校验, kotlin 里可用 reified 重载)
     * @param onResponse     收到响应包时的回调 (主线程)
     * @return 关联ID, 可用 [cancelRequest] 取消; 若发送失败返回 0
     */
    @JvmStatic
    @JvmOverloads
    fun <R : CooPacket> request(
        packet: CooPacket,
        responseType: Class<R>,
        timeoutTicks: Int = DEFAULT_TIMEOUT_TICKS,
        onResponse: (R) -> Unit,
    ): Long {
        val correlationId = correlationCounter.getAndIncrement()
        @Suppress("UNCHECKED_CAST")
        pending[correlationId] = Pending(
            expectType = responseType,
            callback = onResponse as (CooPacket) -> Unit,
            requestPacket = packet,
            remainingTicks = timeoutTicks,
            totalTicks = timeoutTicks,
        )
        val ok = sendInternal(packet, CooPacketKind.REQUEST, correlationId, timeoutTicks)
        if (!ok) {
            pending.remove(correlationId)
            return 0L
        }
        return correlationId
    }

    /**
     * Kotlin 友好的 reified 版本
     */
    @JvmSynthetic
    inline fun <reified R : CooPacket> request(
        packet: CooPacket,
        timeoutTicks: Int = DEFAULT_TIMEOUT_TICKS,
        noinline onResponse: (R) -> Unit,
    ): Long = request(packet, R::class.java, timeoutTicks, onResponse)

    /**
     * 取消一个未完成的请求 (响应不会再被触发, 也不会再触发超时事件)
     */
    @JvmStatic
    fun cancelRequest(correlationId: Long): Boolean {
        return pending.remove(correlationId) != null
    }

    /**
     * 客户端 tick 推进, 由 CooParticlesAPIClient 主循环调用
     */
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
        expired.forEach { (id, p) ->
            CooEventBus.call(
                CooPacketRequestTimeoutEvent(
                    requestPacket = p.requestPacket,
                    side = CooPacketRequestTimeoutEvent.Side.CLIENT_TO_SERVER,
                    targetPlayer = null,
                    correlationId = id,
                    timeoutTicks = p.totalTicks,
                )
            )
        }
    }

    /**
     * 处理接收自服务端的信封 (客户端线程)
     *
     * 由平台 (Fabric / NeoForge) 在收到 [CooPacketEnvelopeS2C] 时调用
     */
    @JvmStatic
    fun handleS2C(envelope: CooPacketEnvelopeS2C) {
        val client = Minecraft.getInstance()
        client.execute {
            handleS2CInternal(envelope)
        }
    }

    private fun handleS2CInternal(envelope: CooPacketEnvelopeS2C) {
        val kind = CooPacketKind.fromId(envelope.kindId)
        val packet = CooPacketRegistry.decode(envelope.packetId, envelope.data)
        if (packet == null) {
            CooParticlesConstants.logger.warn("收到未知 CooPacket: ${envelope.packetId} (kind=$kind)")
            return
        }
        val event = CooEventBus.call(
            CooPacketReceiveEvent(
                packet = packet,
                kind = kind,
                side = CooPacketReceiveEvent.Side.CLIENT,
                sender = null,
                correlationId = envelope.correlationId,
                timeoutTicks = envelope.timeoutTicks,
            )
        )
        if (event.isCancelled) return

        val ctx = ClientContext(packet, kind, envelope.correlationId, envelope.timeoutTicks)
        try {
            packet.onClientReceive(ctx)
        } catch (e: Throwable) {
            CooParticlesConstants.logger.error("CooPacket onClientReceive 异常: ${envelope.packetId}", e)
        }

        if (kind == CooPacketKind.RESPONSE) {
            val pendingEntry = pending.remove(envelope.correlationId) ?: return
            if (!pendingEntry.expectType.isInstance(packet)) {
                CooParticlesConstants.logger.warn(
                    "CooPacket 响应类型不匹配: 期望 ${pendingEntry.expectType.name}, 实际 ${packet::class.java.name}"
                )
                return
            }
            try {
                pendingEntry.callback(packet)
            } catch (e: Throwable) {
                CooParticlesConstants.logger.error("CooPacket request 回调异常 (correlationId=${envelope.correlationId})", e)
            }
        }
    }

    /**
     * 内部 reply: 由 [ClientContext.reply] 调用，构造响应包发送给服务端
     */
    internal fun replyInternal(response: CooPacket, correlationId: Long) {
        sendInternal(response, CooPacketKind.RESPONSE, correlationId, 0)
    }

    private fun sendInternal(
        packet: CooPacket,
        kind: CooPacketKind,
        correlationId: Long,
        timeoutTicks: Int,
    ): Boolean {
        if (!CooPacketRegistry.isRegistered(packet::class.java)) {
            CooParticlesConstants.logger.error(
                "CooPacket 未注册, 无法发送: ${packet::class.java.name} (id=${packet.id()})"
            )
            return false
        }
        val event = CooEventBus.call(
            CooPacketSendEvent(
                packet = packet,
                kind = kind,
                side = CooPacketSendEvent.Side.CLIENT_TO_SERVER,
                targetPlayer = null,
                correlationId = correlationId,
                timeoutTicks = timeoutTicks,
            )
        )
        if (event.isCancelled) return false
        val data = try {
            CooPacketRegistry.encode(packet)
        } catch (e: Throwable) {
            CooParticlesConstants.logger.error("CooPacket 编码失败: ${packet::class.java.name}", e)
            return false
        }
        val envelope = CooPacketEnvelopeC2S(
            kindId = kind.id,
            packetId = packet.id(),
            correlationId = correlationId,
            timeoutTicks = timeoutTicks,
            data = data,
        )
        CooParticlesServices.CLIENT_NETWORK.send(envelope)
        return true
    }
}
