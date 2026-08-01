package cn.coostack.cooparticlesapi.extend

import cn.coostack.cooparticlesapi.network.packet.api.CooPacket
import cn.coostack.cooparticlesapi.network.packet.api.CooServerPacketManager
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level

/**
 * 把 [packet] 广播到当前世界的所有在线玩家。
 *
 * 等价于 `CooServerPacketManager.sendWorlds(this, packet)`，但写成扩展更顺手：
 * `level.sendCooPacket(MyPacket(...))`
 */
fun ServerLevel.sendCooPacket(packet: CooPacket) {
    CooServerPacketManager.sendWorlds(this, packet)
}

/**
 * 给当前世界的每个玩家发起独立的 [packet] 请求，等待 [R] 类型响应。
 *
 * 每个玩家有各自的 correlationId 与计时；任意玩家超时都会触发独立的
 * `CooPacketRequestTimeoutEvent`。
 *
 * @return 全部 correlationId 的列表（与 `players()` 顺序一致）
 */
inline fun <reified R : CooPacket> ServerLevel.requestCooPacket(
    packet: CooPacket,
    timeoutTicks: Int = CooServerPacketManager.DEFAULT_TIMEOUT_TICKS,
    noinline onResponse: (ServerPlayer, R) -> Unit,
): List<Long> = CooServerPacketManager.requestWorlds(listOf(this), packet, timeoutTicks, onResponse)


val Level.serverLevel: ServerLevel?
    get() = this as? ServerLevel

val Level.serverWorld: ServerLevel?
    get() = serverLevel

val Level.clientWorld: ClientLevel?
    get() = clientLevel

val Level.clientLevel: ClientLevel?
    get() = this as? ClientLevel