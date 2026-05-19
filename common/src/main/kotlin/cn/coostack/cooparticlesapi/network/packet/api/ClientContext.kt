package cn.coostack.cooparticlesapi.network.packet.api

import net.minecraft.client.Minecraft

/**
 * 客户端收到 [CooPacket] 时的上下文
 *
 * @property packet 已解码的业务包
 * @property kind   包类型 (普通/请求/响应)
 * @property correlationId 关联ID, 0 表示普通包
 * @property timeoutTicks  请求模式下的超时tick (来自发送端)
 */
class ClientContext(
    val packet: CooPacket,
    val kind: CooPacketKind,
    val correlationId: Long,
    val timeoutTicks: Int,
) {
    val client: Minecraft = Minecraft.getInstance()

    /**
     * 在请求模式下，向服务端回复一个响应包
     *
     * - 必须在 [kind] == [CooPacketKind.REQUEST] 时调用
     * - 使用本上下文携带的 [correlationId]，服务端 request 注册的回调将匹配执行
     */
    fun reply(response: CooPacket) {
        if (kind != CooPacketKind.REQUEST) {
            throw IllegalStateException("CooPacket reply 只能在 REQUEST 上下文中使用, 当前: $kind")
        }
        CooClientPacketManager.replyInternal(response, correlationId)
    }
}
