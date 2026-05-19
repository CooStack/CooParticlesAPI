package cn.coostack.cooparticlesapi.event.events.packet

import cn.coostack.cooparticlesapi.event.api.CooEvent
import cn.coostack.cooparticlesapi.event.api.EventCancelable
import cn.coostack.cooparticlesapi.network.packet.api.CooPacket
import cn.coostack.cooparticlesapi.network.packet.api.CooPacketKind
import net.minecraft.server.level.ServerPlayer

/**
 * 当 [cn.coostack.cooparticlesapi.network.packet.api.CooPacket] 被发出时触发
 *
 * - [side] 标记本次发送由谁触发
 * - 取消事件将阻止该次具体的发送
 *   (对 sendAll / requestAll 而言，每位玩家是独立事件，可单独取消)
 *
 * @property packet         即将被发出的业务包
 * @property kind           包类型 (普通/请求/响应)
 * @property side           发送方
 * @property targetPlayer   目标玩家 (服务器 -> 客户端时不为空; 客户端 -> 服务器时为 null)
 * @property correlationId  关联ID, 0 表示普通包
 * @property timeoutTicks   请求模式下的超时tick
 */
class CooPacketSendEvent(
    val packet: CooPacket,
    val kind: CooPacketKind,
    val side: Side,
    val targetPlayer: ServerPlayer?,
    val correlationId: Long,
    val timeoutTicks: Int,
) : CooEvent(), EventCancelable {
    override var isCancelled: Boolean = false

    enum class Side {
        SERVER_TO_CLIENT,
        CLIENT_TO_SERVER
    }
}
