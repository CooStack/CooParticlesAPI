package cn.coostack.cooparticlesapi.performance

/**
 * 标识 CooPacket 指标所属的本地网络端点。
 *
 * 集成服务器会在同一 JVM 内同时存在客户端和服务端端点，因此指标不能只按传输方向聚合。
 */
enum class PerformanceStatusNetworkEndpoint {
    CLIENT,
    SERVER
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
