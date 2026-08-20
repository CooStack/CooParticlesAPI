package cn.coostack.cooparticlesapi.performance

import cn.coostack.cooparticlesapi.barrages.BarrageManager
import cn.coostack.cooparticlesapi.coofx.server.CooFxSceneManager
import cn.coostack.cooparticlesapi.display.DisplayEntityManager
import cn.coostack.cooparticlesapi.network.particle.ServerParticleGroupManager
import cn.coostack.cooparticlesapi.network.particle.composition.manager.ParticleCompositionManager
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.performance.PerformanceStatusNetworkEndpoint.SERVER
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import cn.coostack.cooparticlesapi.renderer.server.ServerRenderEntityManager
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainEffectManager
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainMappingManager
import cn.coostack.cooparticlesapi.supports.sound.ServerSoundLoopManager
import cn.coostack.cooparticlesapi.supports.sound.ServerSoundManager
import net.minecraft.server.MinecraftServer
import kotlin.math.ceil
import kotlin.math.min

/**
 * 客户端按需取得的服务端性能快照。
 *
 * @property capturedAtEpochMillis 服务端生成快照的 UTC epoch 毫秒
 * @property serverTick 服务端当前 tick 序号
 * @property refreshIntervalTicks 服务端配置的 Status 快照刷新间隔
 * @property targetTps 当前 TickRateManager 的目标 TPS
 * @property tps 根据目标 TPS 与平均 MSPT 推导的可维持 TPS
 * @property averageMspt 原版服务器 tick 历史的平均毫秒数
 * @property p95Mspt 原版服务器 tick 历史的 P95 毫秒数
 * @property maxMspt 原版服务器 tick 历史的最大毫秒数
 * @property onlinePlayers 当前在线玩家数
 * @property particleGroups 服务端旧 ParticleGroup 实例数
 * @property renderEntities 服务端 RenderEntity 实例数
 * @property displayEntities 服务端 DisplayEntity 实例数
 * @property emitters 服务端 ParticleEmitter 实例数
 * @property compositions 服务端 ParticleComposition 实例数
 * @property terrainEffectGroups 服务端 Terrain effect group 数
 * @property terrainMappings 服务端程序化 Terrain mapping 实例数
 * @property soundInstances 服务端管理的声音实例数
 * @property soundLoops 服务端管理的循环声音数
 * @property barrages 服务端 Barrage 实例数
 * @property cooFxScenes 服务端 CooFX scene 数
 * @property heapUsedBytes JVM 已使用堆字节数
 * @property heapMaxBytes JVM 最大堆字节数
 * @property cooPackets CooPacket 服务端端点累计业务流量
 */
data class PerformanceStatusServerSnapshot(
    val capturedAtEpochMillis: Long,
    val serverTick: Long,
    val refreshIntervalTicks: Int,
    val targetTps: Double,
    val tps: Double,
    val averageMspt: Double,
    val p95Mspt: Double,
    val maxMspt: Double,
    val onlinePlayers: Int,
    val particleGroups: Int,
    val renderEntities: Int,
    val displayEntities: Int,
    val emitters: Int,
    val compositions: Int,
    val terrainEffectGroups: Int,
    val terrainMappings: Int,
    val soundInstances: Int,
    val soundLoops: Int,
    val barrages: Int,
    val cooFxScenes: Int,
    val heapUsedBytes: Long,
    val heapMaxBytes: Long,
    val cooPackets: PerformanceStatusNetworkTotals,
)

/** 构造固定大小的服务端 Status 快照，不访问任何客户端或渲染类。 */
object PerformanceStatusServerSnapshotFactory {
    /** 从 MinecraftServer 和各服务端 manager 的 O(1) 计数入口构造快照。 */
    fun create(server: MinecraftServer): PerformanceStatusServerSnapshot {
        val tickTimes = server.getTickTimesNanos().filter { tickTime -> tickTime > 0L }.toLongArray()
        val averageMspt = server.getAverageTickTimeNanos().toDouble() / 1_000_000.0
        val sortedTickTimes = tickTimes.sortedArray()
        val p95Mspt = percentileNanos(sortedTickTimes, 0.95) / 1_000_000.0
        val maxMspt = (sortedTickTimes.lastOrNull() ?: 0L) / 1_000_000.0
        val targetTps = server.tickRateManager().tickrate().toDouble()
        val sustainableTps = if (averageMspt > 0.0) 1_000.0 / averageMspt else targetTps
        val runtime = Runtime.getRuntime()
        return PerformanceStatusServerSnapshot(
            capturedAtEpochMillis = System.currentTimeMillis(),
            serverTick = server.tickCount.toLong(),
            refreshIntervalTicks = CooParticlesServices.API_CONFIG_MANAGER.getConfig()
                .statusServerRefreshIntervalTicks,
            targetTps = targetTps,
            tps = min(targetTps, sustainableTps),
            averageMspt = averageMspt,
            p95Mspt = p95Mspt,
            maxMspt = maxMspt,
            onlinePlayers = server.playerCount,
            particleGroups = ServerParticleGroupManager.groupCount(),
            renderEntities = ServerRenderEntityManager.loadedEntityCount(),
            displayEntities = DisplayEntityManager.serverEntityCount(),
            emitters = ParticleEmittersManager.serverEmitterCount(),
            compositions = ParticleCompositionManager.loadedServerCount(),
            terrainEffectGroups = CooTerrainEffectManager.serverGroupCount(),
            terrainMappings = CooTerrainMappingManager.serverInstanceCount(),
            soundInstances = ServerSoundManager.activeSoundCount(),
            soundLoops = ServerSoundLoopManager.activeLoopCount(),
            barrages = BarrageManager.count(),
            cooFxScenes = CooFxSceneManager.size(),
            heapUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
            heapMaxBytes = runtime.maxMemory(),
            cooPackets = PerformanceStatusNetworkMetrics.snapshot(SERVER),
        )
    }

    /** 从已排序的纳秒样本计算 nearest-rank 百分位。 */
    internal fun percentileNanos(sortedValues: LongArray, percentile: Double): Long {
        if (sortedValues.isEmpty()) return 0L
        val rank = ceil(percentile.coerceIn(0.0, 1.0) * sortedValues.size).toInt().coerceAtLeast(1)
        return sortedValues[rank - 1]
    }
}
