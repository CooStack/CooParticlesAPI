package cn.coostack.cooparticlesapi.event.events.packet

import cn.coostack.cooparticlesapi.event.api.CooEvent
import cn.coostack.cooparticlesapi.event.api.EventCancelable
import cn.coostack.cooparticlesapi.network.packet.api.CooPacket
import cn.coostack.cooparticlesapi.network.packet.api.CooPacketKind
import net.minecraft.server.level.ServerPlayer

/**
 * 当 [cn.coostack.cooparticlesapi.network.packet.api.CooPacket] 被接收时触发
 *
 * - 在 [CooPacket.onClientReceive] / [CooPacket.onServerReceive] 之前调用
 * - 取消该事件将跳过 onReceive 回调 与 (响应包时) 对应的 request 回调
 *
 * @property packet        已解码的业务包
 * @property kind          包类型 (普通/请求/响应)
 * @property side          接收端
 * @property sender        客户端->服务器 时为发送方玩家；服务器->客户端 时为 null
 * @property correlationId 关联ID, 0 表示普通包
 * @property timeoutTicks  请求模式下的超时tick (来自发送方)
 */
class CooPacketReceiveEvent(
    val packet: CooPacket,
    val kind: CooPacketKind,
    val side: Side,
    val sender: ServerPlayer?,
    val correlationId: Long,
    val timeoutTicks: Int,
) : CooEvent(), EventCancelable {
    override var isCancelled: Boolean = false

    enum class Side {
        CLIENT,
        SERVER
    }
}
