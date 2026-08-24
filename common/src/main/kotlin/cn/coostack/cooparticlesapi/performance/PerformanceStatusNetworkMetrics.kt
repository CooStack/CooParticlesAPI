package cn.coostack.cooparticlesapi.performance

import java.util.concurrent.atomic.AtomicLong

/**
 * 某个端点在当前 JVM 内累计的原版 Packet 数量。
 *
 * 字节数不在此结构中估算；原版 Packet 对象没有稳定的对象大小语义，避免把 JVM 对象大小
 * 冒充网络传输字节数。该结构用于按窗口统计实际收发包数量。
 *
 * @property sentPackets 已提交到 Connection 的原版包数
 * @property receivedPackets 已进入 Connection 入站处理的原版包数
 */
data class PerformanceStatusVanillaPacketTotals(
    val sentPackets: Long,
    val receivedPackets: Long,
) {
    /** 计算从较早累计值到当前累计值的非负增量。 */
    fun deltaFrom(previous: PerformanceStatusVanillaPacketTotals): PerformanceStatusVanillaPacketTotals {
        return PerformanceStatusVanillaPacketTotals(
            sentPackets = (sentPackets - previous.sentPackets).coerceAtLeast(0L),
            receivedPackets = (receivedPackets - previous.receivedPackets).coerceAtLeast(0L),
        )
    }

    /** 合并同一聚合窗口内的两个原版 Packet 增量。 */
    operator fun plus(other: PerformanceStatusVanillaPacketTotals): PerformanceStatusVanillaPacketTotals {
        return PerformanceStatusVanillaPacketTotals(
            sentPackets = sentPackets + other.sentPackets,
            receivedPackets = receivedPackets + other.receivedPackets,
        )
    }
}

/**
 * 标识 CooPacket 指标所属的本地网络端点。
 *
 * 集成服务器会在同一 JVM 内同时存在客户端和服务端端点，因此指标不能只按传输方向聚合。
 * CLIENT 表示本地客户端连接，SERVER 表示本地服务端连接；两个值也用于原版 Packet 计数。
 */
enum class PerformanceStatusNetworkEndpoint {
    /** 客户端连接端点，负责接收服务端包并发送客户端包。 */
    CLIENT,

    /** 服务端连接端点，负责接收客户端包并发送服务端包。 */
    SERVER,
}

/**
 * 某个端点自进程启动以来累计的 CooPacket 业务流量。
 *
 * @property sentPackets 已成功编码并提交发送的业务包数
 * @property sentBytes 已提交发送的业务 payload 字节数，不含 envelope 和底层协议开销
 * @property receivedPackets 已成功收到并进入解码流程的业务包数
 * @property receivedBytes 已收到的业务 payload 字节数，不含 envelope 和底层协议开销
 */
data class PerformanceStatusNetworkTotals(
    val sentPackets: Long,
    val sentBytes: Long,
    val receivedPackets: Long,
    val receivedBytes: Long,
) {
    /** 计算从较早累计值到当前累计值的非负增量。 */
    fun deltaFrom(previous: PerformanceStatusNetworkTotals): PerformanceStatusNetworkTotals {
        return PerformanceStatusNetworkTotals(
            sentPackets = (sentPackets - previous.sentPackets).coerceAtLeast(0L),
            sentBytes = (sentBytes - previous.sentBytes).coerceAtLeast(0L),
            receivedPackets = (receivedPackets - previous.receivedPackets).coerceAtLeast(0L),
            receivedBytes = (receivedBytes - previous.receivedBytes).coerceAtLeast(0L),
        )
    }
}

/**
 * 记录两端 CooPacket 的精确业务包数量和业务 payload 字节数。
 *
 * 该计数器只做短临界区内的整数累加，不进行额外序列化，也不把 envelope、压缩、加密或 TCP 开销计入业务字节。
 */
object PerformanceStatusNetworkMetrics {
    /** 客户端端点累计状态。 */
    private val client = EndpointCounters()

    /** 服务端端点累计状态。 */
    private val server = EndpointCounters()

    /** 客户端端点累计原版 Packet 状态。 */
    private val clientVanillaPackets = VanillaEndpointCounters()

    /** 服务端端点累计原版 Packet 状态。 */
    private val serverVanillaPackets = VanillaEndpointCounters()

    /** 返回指定端点当前累计的原版 Packet 数量。 */
    fun vanillaSnapshot(endpoint: PerformanceStatusNetworkEndpoint): PerformanceStatusVanillaPacketTotals {
        return vanillaCounters(endpoint).snapshot()
    }

    /** 记录一次已经提交到原版 Connection 的发送包。 */
    fun recordVanillaSent(endpoint: PerformanceStatusNetworkEndpoint) {
        vanillaCounters(endpoint).recordSent()
    }

    /** 记录一次进入原版 Connection 入站处理的接收包。 */
    fun recordVanillaReceived(endpoint: PerformanceStatusNetworkEndpoint) {
        vanillaCounters(endpoint).recordReceived()
    }

    /** 在业务包成功编码后记录一次发送。 */
    fun recordSent(endpoint: PerformanceStatusNetworkEndpoint, payloadBytes: Int) {
        counters(endpoint).recordSent(payloadBytes)
    }

    /** 在业务 envelope 到达本地端点时记录一次接收。 */
    fun recordReceived(endpoint: PerformanceStatusNetworkEndpoint, payloadBytes: Int) {
        counters(endpoint).recordReceived(payloadBytes)
    }

    /** 返回指定端点当前的一致累计值快照。 */
    fun snapshot(endpoint: PerformanceStatusNetworkEndpoint): PerformanceStatusNetworkTotals {
        return counters(endpoint).snapshot()
    }

    /** 返回端点对应的一致计数器集合。 */
    private fun counters(endpoint: PerformanceStatusNetworkEndpoint): EndpointCounters {
        return when (endpoint) {
            PerformanceStatusNetworkEndpoint.CLIENT -> client
            PerformanceStatusNetworkEndpoint.SERVER -> server
        }
    }

    /** 返回端点对应的原版 Packet 计数器。 */
    private fun vanillaCounters(endpoint: PerformanceStatusNetworkEndpoint): VanillaEndpointCounters {
        return when (endpoint) {
            PerformanceStatusNetworkEndpoint.CLIENT -> clientVanillaPackets
            PerformanceStatusNetworkEndpoint.SERVER -> serverVanillaPackets
        }
    }

    /** 保存一个端点的原版 Packet 收发累计值。 */
    private class VanillaEndpointCounters {
        private val sentPackets = AtomicLong()
        private val receivedPackets = AtomicLong()

        fun recordSent() {
            sentPackets.incrementAndGet()
        }

        fun recordReceived() {
            receivedPackets.incrementAndGet()
        }

        fun snapshot(): PerformanceStatusVanillaPacketTotals {
            return PerformanceStatusVanillaPacketTotals(sentPackets.get(), receivedPackets.get())
        }
    }

    /** 保存一个端点的四项单调累计值，并在同一锁下更新和发布一致快照。 */
    private class EndpointCounters {
        /** 已发送业务包数。 */
        private var sentPackets = 0L

        /** 已发送业务 payload 字节数。 */
        private var sentBytes = 0L

        /** 已接收业务包数。 */
        private var receivedPackets = 0L

        /** 已接收业务 payload 字节数。 */
        private var receivedBytes = 0L

        /** 原子更新一次发送的包数和字节数。 */
        @Synchronized
        fun recordSent(payloadBytes: Int) {
            sentPackets++
            sentBytes += payloadBytes.coerceAtLeast(0).toLong()
        }

        /** 原子更新一次接收的包数和字节数。 */
        @Synchronized
        fun recordReceived(payloadBytes: Int) {
            receivedPackets++
            receivedBytes += payloadBytes.coerceAtLeast(0).toLong()
        }

        /** 在同一临界区读取四项累计值，避免撕裂样本。 */
        @Synchronized
        fun snapshot(): PerformanceStatusNetworkTotals {
            return PerformanceStatusNetworkTotals(
                sentPackets = sentPackets,
                sentBytes = sentBytes,
                receivedPackets = receivedPackets,
                receivedBytes = receivedBytes,
            )
        }
    }
}
