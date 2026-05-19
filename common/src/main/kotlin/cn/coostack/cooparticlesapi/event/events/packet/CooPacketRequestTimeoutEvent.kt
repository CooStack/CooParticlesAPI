package cn.coostack.cooparticlesapi.event.events.packet

import cn.coostack.cooparticlesapi.event.api.CooEvent
import cn.coostack.cooparticlesapi.network.packet.api.CooPacket
import net.minecraft.server.level.ServerPlayer

/**
 * 当一次请求 ([cn.coostack.cooparticlesapi.network.packet.api.CooServerPacketManager.request] /
 * [cn.coostack.cooparticlesapi.network.packet.api.CooClientPacketManager.request]) 超时时触发
 *
 * - 不可取消, 仅作为通知/日志钩子
 *
 * @property requestPacket 发出的请求包
 * @property side          请求发出方
 * @property targetPlayer  服务端->客户端 请求时, 是目标玩家; 客户端->服务端 请求时为 null
 * @property correlationId 该次请求的关联ID
 * @property timeoutTicks  设置过的超时tick
 */
class CooPacketRequestTimeoutEvent(
    val requestPacket: CooPacket,
    val side: Side,
    val targetPlayer: ServerPlayer?,
    val correlationId: Long,
    val timeoutTicks: Int,
) : CooEvent() {

    enum class Side {
        SERVER_TO_CLIENT,
        CLIENT_TO_SERVER
    }
}
