package cn.coostack.cooparticlesapi.performance.client

import cn.coostack.cooparticlesapi.performance.PerformanceStatusNetworkTotals
import cn.coostack.cooparticlesapi.performance.PerformanceStatusServerSnapshot
import cn.coostack.cooparticlesapi.performance.PerformanceStatusVanillaPacketTotals

/**
 * 单个客户端 tick 采集的客户端性能状态。
 *
 * @property fps Minecraft 当前 FPS
 * @property frameTimeMs Minecraft 当前帧耗时毫秒
 * @property tickIntervalMs 两次 Status 客户端采样之间的真实毫秒数
 * @property clientTps 根据最近一秒真实采样间隔平均值计算并限制到 20 的客户端 tick 频率
 * @property particles 原版 ParticleEngine 活动粒子数
 * @property cParticles GPU CParticle 存活数
 * @property cParticleSystems GPU CParticle system 数
 * @property soundInstances 原版 SoundEngine 实际声音声道数
 * @property managedSoundInstances Coo 管理的 SoundInstance 数
 * @property soundLoops Coo 管理的循环声音数
 * @property renderEntities 客户端 RenderEntity 数
 * @property displayEntities 客户端 DisplayEntity 数
 * @property emitters 客户端 Emitter 数
 * @property compositions 客户端活动 Composition 数
 * @property cooFxScenes 客户端活动 CooFX scene 数
 * @property cooFxParticles 客户端活动 CooFX mesh particle 数
 * @property cooFxModels 客户端活动 CooFX model instance 数
 * @property terrainEffectGroups 客户端活动 Terrain effect group 数
 * @property terrainMappings 客户端活动 Terrain mapping 数
 * @property postEffects 客户端活动后处理实例数
 * @property graphicsShaders Coo 图形 shader program 数
 * @property computeShaders Coo compute shader program 数
 * @property gcCollectionCount 客户端 JVM 累计 GC 次数
 * @property gcCollectionTimeMs 客户端 JVM 累计 GC 耗时毫秒
 * @property heapUsedBytes JVM 已使用堆字节数
 * @property heapMaxBytes JVM 最大堆字节数
 * @property cooPackets 客户端端点累计 CooPacket 业务流量
 * @property vanillaPackets 客户端原版 Connection 累计收发包数量
 */
data class PerformanceStatusClientSnapshot(
    val fps: Int,
    val frameTimeMs: Double,
    val tickIntervalMs: Double,
    val clientTps: Double,
    val particles: Int,
    val cParticles: Int,
    val cParticleSystems: Int,
    val soundInstances: Int,
    val managedSoundInstances: Int,
    val soundLoops: Int,
    val renderEntities: Int,
    val displayEntities: Int,
    val emitters: Int,
    val compositions: Int,
    val cooFxScenes: Int,
    val cooFxParticles: Int,
    val cooFxModels: Int,
    val terrainEffectGroups: Int,
    val terrainMappings: Int,
    val postEffects: Int,
    val graphicsShaders: Int,
    val computeShaders: Int,
    val gcCollectionCount: Long = 0L,
    val gcCollectionTimeMs: Long = 0L,
    val heapUsedBytes: Long,
    val heapMaxBytes: Long,
    val cooPackets: PerformanceStatusNetworkTotals,
    val vanillaPackets: PerformanceStatusVanillaPacketTotals = PerformanceStatusVanillaPacketTotals(0L, 0L),
)

/**
 * 一行可导出 Status 样本，把客户端 tick 与最近一次按需服务端快照关联起来。
 *
 * @property sampleIndex 会话内从零开始的样本序号
 * @property capturedAtEpochMillis 客户端采集行的 epoch 毫秒
 * @property elapsedMillis 相对会话开始的真实毫秒数
 * @property client 客户端当前 tick 快照
 * @property clientNetworkDelta 客户端 CooPacket 自上一行以来的增量
 * @property clientVanillaPacketDelta 客户端原版 Packet 当前完整聚合窗口增量；窗口未完成时为 null
 * @property vanillaPacketAggregationTicks 原版 Packet 增量聚合窗口包含的客户端 tick 数
 * @property server 最近一次服务端快照；尚未响应时为 null
 * @property serverSnapshotAgeMillis 服务端快照相对本行的年龄；无快照时为 null
 * @property serverNetworkDelta 服务端累计 CooPacket 自上一份新快照以来的增量；没有新快照时为 null
 * @property serverVanillaPacketDelta 服务端原版 Packet 自上一份新快照以来的增量；没有新快照时为 null
 */
data class PerformanceStatusSample(
    val sampleIndex: Long,
    val capturedAtEpochMillis: Long,
    val elapsedMillis: Long,
    val client: PerformanceStatusClientSnapshot,
    val clientNetworkDelta: PerformanceStatusNetworkTotals,
    val clientVanillaPacketDelta: PerformanceStatusVanillaPacketTotals? = null,
    val vanillaPacketAggregationTicks: Int = 1,
    val server: PerformanceStatusServerSnapshot?,
    val serverSnapshotAgeMillis: Long?,
    val serverNetworkDelta: PerformanceStatusNetworkTotals?,
    val serverVanillaPacketDelta: PerformanceStatusVanillaPacketTotals? = null,
)

/** 根据最近有效客户端 tick 间隔计算平均 TPS，并限制为原版 20 TPS 上限。 */
internal fun clientTpsFromTickIntervals(tickIntervalsMs: Iterable<Double>): Double {
    var totalIntervalMs = 0.0
    var sampleCount = 0
    tickIntervalsMs.forEach { intervalMs ->
        if (intervalMs.isFinite() && intervalMs > 0.0) {
            totalIntervalMs += intervalMs
            sampleCount++
        }
    }
    if (sampleCount == 0) return 0.0
    return (1_000.0 / (totalIntervalMs / sampleCount)).coerceAtMost(20.0)
}

/** 把 Status 样本编码为适合 Excel、Pandas、R 和其他数据软件读取的 CSV。 */
object PerformanceStatusCsv {
    /** 返回稳定列顺序的 CSV 表头。 */
    fun header(): String {
        return csvLine(
            listOf(
                "schema_version",
                "sample_index",
                "captured_at_epoch_ms",
                "elapsed_ms",
                "client_fps",
                "client_frame_time_ms",
                "client_tick_interval_ms",
                "client_tps",
                "client_particles",
                "client_cparticles",
                "client_cparticle_systems",
                "client_sound_instances",
                "client_managed_sound_instances",
                "client_sound_loops",
                "client_render_entities",
                "client_display_entities",
                "client_emitters",
                "client_compositions",
                "client_coofx_scenes",
                "client_coofx_particles",
                "client_coofx_models",
                "client_terrain_effect_groups",
                "client_terrain_mappings",
                "client_post_effects",
                "client_graphics_shaders",
                "client_compute_shaders",
                "client_gc_collection_count",
                "client_gc_collection_time_ms",
                "client_heap_used_bytes",
                "client_heap_max_bytes",
                "client_vanilla_packets_sent_window",
                "client_vanilla_packets_received_window",
                "vanilla_packet_aggregation_ticks",
                "client_coopackets_sent_tick",
                "client_coopacket_bytes_sent_tick",
                "client_coopackets_received_tick",
                "client_coopacket_bytes_received_tick",
                "client_coopackets_sent_total",
                "client_coopacket_bytes_sent_total",
                "client_coopackets_received_total",
                "client_coopacket_bytes_received_total",
                "client_vanilla_packets_sent_total",
                "client_vanilla_packets_received_total",
                "server_captured_at_epoch_ms",
                "server_snapshot_age_ms",
                "server_tick",
                "server_refresh_interval_ticks",
                "server_target_tps",
                "server_tps",
                "server_average_mspt",
                "server_p95_mspt",
                "server_max_mspt",
                "server_online_players",
                "server_particle_groups",
                "server_render_entities",
                "server_display_entities",
                "server_emitters",
                "server_compositions",
                "server_terrain_effect_groups",
                "server_terrain_mappings",
                "server_sound_instances",
                "server_sound_loops",
                "server_barrages",
                "server_coofx_scenes",
                "server_gc_collection_count",
                "server_gc_collection_time_ms",
                "server_heap_used_bytes",
                "server_heap_max_bytes",
                "server_coopackets_sent_interval",
                "server_coopacket_bytes_sent_interval",
                "server_coopackets_received_interval",
                "server_coopacket_bytes_received_interval",
                "server_coopackets_sent_total",
                "server_coopacket_bytes_sent_total",
                "server_coopackets_received_total",
                "server_coopacket_bytes_received_total",
                "server_vanilla_packets_sent_interval",
                "server_vanilla_packets_received_interval",
                "server_vanilla_packets_sent_total",
                "server_vanilla_packets_received_total",
            )
        )
    }

    /** 按 [header] 的固定顺序编码一行样本。 */
    fun row(sample: PerformanceStatusSample): String {
        val client = sample.client
        val server = sample.server
        return csvLine(
            listOf(
                2,
                sample.sampleIndex,
                sample.capturedAtEpochMillis,
                sample.elapsedMillis,
                client.fps,
                client.frameTimeMs,
                client.tickIntervalMs,
                client.clientTps,
                client.particles,
                client.cParticles,
                client.cParticleSystems,
                client.soundInstances,
                client.managedSoundInstances,
                client.soundLoops,
                client.renderEntities,
                client.displayEntities,
                client.emitters,
                client.compositions,
                client.cooFxScenes,
                client.cooFxParticles,
                client.cooFxModels,
                client.terrainEffectGroups,
                client.terrainMappings,
                client.postEffects,
                client.graphicsShaders,
                client.computeShaders,
                client.gcCollectionCount,
                client.gcCollectionTimeMs,
                client.heapUsedBytes,
                client.heapMaxBytes,
                sample.clientVanillaPacketDelta?.sentPackets,
                sample.clientVanillaPacketDelta?.receivedPackets,
                sample.vanillaPacketAggregationTicks,
                sample.clientNetworkDelta.sentPackets,
                sample.clientNetworkDelta.sentBytes,
                sample.clientNetworkDelta.receivedPackets,
                sample.clientNetworkDelta.receivedBytes,
                client.cooPackets.sentPackets,
                client.cooPackets.sentBytes,
                client.cooPackets.receivedPackets,
                client.cooPackets.receivedBytes,
                client.vanillaPackets.sentPackets,
                client.vanillaPackets.receivedPackets,
                server?.capturedAtEpochMillis,
                sample.serverSnapshotAgeMillis,
                server?.serverTick,
                server?.refreshIntervalTicks,
                server?.targetTps,
                server?.tps,
                server?.averageMspt,
                server?.p95Mspt,
                server?.maxMspt,
                server?.onlinePlayers,
                server?.particleGroups,
                server?.renderEntities,
                server?.displayEntities,
                server?.emitters,
                server?.compositions,
                server?.terrainEffectGroups,
                server?.terrainMappings,
                server?.soundInstances,
                server?.soundLoops,
                server?.barrages,
                server?.cooFxScenes,
                server?.gcCollectionCount,
                server?.gcCollectionTimeMs,
                server?.heapUsedBytes,
                server?.heapMaxBytes,
                sample.serverNetworkDelta?.sentPackets,
                sample.serverNetworkDelta?.sentBytes,
                sample.serverNetworkDelta?.receivedPackets,
                sample.serverNetworkDelta?.receivedBytes,
                server?.cooPackets?.sentPackets,
                server?.cooPackets?.sentBytes,
                server?.cooPackets?.receivedPackets,
                server?.cooPackets?.receivedBytes,
                sample.serverVanillaPacketDelta?.sentPackets,
                sample.serverVanillaPacketDelta?.receivedPackets,
                server?.vanillaPackets?.sentPackets,
                server?.vanillaPackets?.receivedPackets,
            )
        )
    }

    /** 对一组单元格执行 RFC 4180 兼容转义。 */
    internal fun csvLine(values: List<Any?>): String {
        return values.joinToString(",") { value -> escape(value?.toString().orEmpty()) }
    }

    /** 为包含逗号、引号或换行的单元格增加双引号并转义内部引号。 */
    internal fun escape(value: String): String {
        if (value.none { character -> character == ',' || character == '"' || character == '\n' || character == '\r' }) {
            return value
        }
        return "\"${value.replace("\"", "\"\"")}\""
    }
}
