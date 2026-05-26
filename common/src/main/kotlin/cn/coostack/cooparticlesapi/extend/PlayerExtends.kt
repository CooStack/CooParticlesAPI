package cn.coostack.cooparticlesapi.extend

import cn.coostack.cooparticlesapi.network.packet.api.CooClientPacketManager
import cn.coostack.cooparticlesapi.network.packet.api.CooPacket
import cn.coostack.cooparticlesapi.network.packet.api.CooServerPacketManager
import net.minecraft.client.player.LocalPlayer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player

/**
 * 把 [packet] 发出去:
 * - 服务端玩家 ([ServerPlayer]) -> 把包发给该玩家 (S2C)
 * - 客户端玩家               -> 把包发给当前所连服务器 (C2S)
 *
 * @return 是否发送成功 (false = 事件被取消 / 包未注册 / 客户端未连接)
 */
fun Player.sendCooPacket(packet: CooPacket): Boolean = when (this) {
    is ServerPlayer -> CooServerPacketManager.sendTo(this, packet)
    else -> CooClientPacketManager.sendTo(packet)
}

/**
 * 发起一次请求, 等待 [R] 类型响应:
 * - 服务端玩家 ([ServerPlayer]) -> 向该玩家请求 (S2C), [onResponse] 在收到该玩家的响应时触发
 * - 客户端玩家               -> 向当前所连服务器请求 (C2S), [onResponse] 在收到服务器响应时触发
 *
 * 两端 timeout 默认值都是 60 tick (3 秒).
 *
 * @return 关联ID; 0 表示发送失败
 */
inline fun <reified R : CooPacket> Player.requestCooPacket(
    packet: CooPacket,
    timeoutTicks: Int = CooServerPacketManager.DEFAULT_TIMEOUT_TICKS,
    noinline onResponse: (R) -> Unit,
): Long = when (this) {
    is ServerPlayer -> CooServerPacketManager.request(this, packet, timeoutTicks, onResponse)
    else -> CooClientPacketManager.request(packet, timeoutTicks, onResponse)
}


val Player.serverPlayer: ServerPlayer?
    get() = this as? ServerPlayer

val Player.clientPlayer: LocalPlayer?
    get() = this as? LocalPlayer