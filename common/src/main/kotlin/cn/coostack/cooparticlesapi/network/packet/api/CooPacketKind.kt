package cn.coostack.cooparticlesapi.network.packet.api

/**
 * 数据包类型
 *
 * - [NORMAL]: 普通包，无需响应
 * - [REQUEST]: 请求包，等待对端响应
 * - [RESPONSE]: 响应包，对应某个 REQUEST 的回执
 */
enum class CooPacketKind {
    NORMAL,
    REQUEST,
    RESPONSE;

    companion object {
        private val values = entries.toTypedArray()

        fun fromId(id: Int): CooPacketKind {
            return values.getOrNull(id) ?: NORMAL
        }
    }

    val id: Int get() = ordinal
}
