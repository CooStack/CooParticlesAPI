package cn.coostack.cooparticlesapi.performance

import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 服务端 Status 请求的权限与每玩家速率限制器。
 *
 * 该防线独立于 Brigadier 命令权限，避免修改客户端绕过命令直接滥用 C2S 快照端点。
 */
object PerformanceStatusServerRequestGate {
    /** 每名玩家最近一次获准生成快照的服务端单调时钟纳秒。 */
    private val lastAcceptedNanos = ConcurrentHashMap<UUID, Long>()

    /**
     * 验证权限，并按服务端配置的刷新间隔限制同一玩家的请求频率。
     *
     * 使用墙钟对应的 tick 时长而不是服务端 tick 序号，避免服务器低 TPS 时合法客户端连续超时。
     */
    fun tryAcquire(player: ServerPlayer, refreshIntervalTicks: Int): Boolean {
        if (!player.createCommandSourceStack().hasPermission(2)) return false
        val nowNanos = System.nanoTime()
        val minimumIntervalNanos = (refreshIntervalTicks - 1).coerceAtLeast(1) * 50_000_000L
        var accepted = false
        lastAcceptedNanos.compute(player.uuid) { _, previousNanos ->
            if (previousNanos == null || nowNanos - previousNanos >= minimumIntervalNanos) {
                accepted = true
                nowNanos
            } else {
                previousNanos
            }
        }
        return accepted
    }

    /** 玩家断开时移除限流状态，避免同 UUID 重连等待旧间隔。 */
    fun forget(playerId: UUID) {
        lastAcceptedNanos.remove(playerId)
    }

    /** 服务器停止时清理跨世界运行态。 */
    fun clear() {
        lastAcceptedNanos.clear()
    }
}
