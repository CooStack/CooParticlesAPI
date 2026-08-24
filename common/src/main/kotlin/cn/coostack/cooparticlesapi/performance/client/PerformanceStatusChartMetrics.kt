package cn.coostack.cooparticlesapi.performance.client

/** Status 图表同时保留的曲线数量上限，供控制器和 GUI 使用同一份契约。 */
internal const val PERFORMANCE_STATUS_MAX_SELECTED_SERIES = 12

/**
 * 返回一个最多包含 maxPoints 个元素的等距只读视图，不复制原始元素。
 *
 * 索引映射始终保留首尾元素，且 get 为 O(1)。图表因此只处理屏幕能够显示的点数；历史时长增加
 * 不会让每帧曲线遍历成本继续增长。放大时间窗口后会重新从原始逐 tick 历史取样。
 */
internal fun <T> samplePerformanceStatusChartPoints(source: List<T>, maxPoints: Int): List<T> {
    if (maxPoints <= 0 || source.isEmpty()) return emptyList()
    if (source.size <= maxPoints) return source
    val sampledSize = maxPoints
    return object : AbstractList<T>() {
        override val size: Int = sampledSize

        override fun get(index: Int): T {
            if (index !in 0 until size) throw IndexOutOfBoundsException("index=$index, size=$size")
            val sourceIndex = if (size == 1) {
                source.lastIndex
            } else {
                (index.toLong() * source.lastIndex / (size - 1)).toInt()
            }
            return source[sourceIndex]
        }
    }
}

/**
 * 图表图例中数值的显示单位。
 *
 * NUMBER 表示无特殊单位的计数或速率；MILLISECONDS 表示毫秒；BYTES 表示按 IEC 单位格式化的字节数。
 * 该枚举只控制显示格式，不改变采样值或量程计算。
 */
internal enum class PerformanceStatusChartValueKind {
    /** 无特殊单位的数值。 */
    NUMBER,

    /** 以毫秒显示的时间值。 */
    MILLISECONDS,

    /** 以 B、KiB、MiB 或 GiB 显示的字节值。 */
    BYTES,
}

/**
 * 参与比值分析时的指标角色。
 *
 * PERFORMANCE 是除数，表示 FPS、TPS、MSPT、堆内存或网络流量等运行表现；IMPACT 是被除数，
 * 表示粒子、声音、实体、Composition 等可能影响运行表现的工作量。相关性曲线统一计算 IMPACT ÷ PERFORMANCE；
 * 除数为零或缺失的样本不会参与比值统计。
 */
internal enum class PerformanceStatusChartMetricRole {
    /** 可作为比值被除数的性能表现指标。 */
    PERFORMANCE,

    /** 可作为比值除数的工作量或影响因素指标。 */
    IMPACT,
}

/**
 * 可在 Status 实时折线图中使用的单值维度及其比值分析角色。
 *
 * 每个成员都从一行关联样本中提取一个原始值。表格点击会选择原始值折线；相关性控件可以把
 * PERFORMANCE 成员作为除数、IMPACT 成员作为被除数，生成新的实时负载比值折线。成员的 role
 * 决定它能出现在相关性控件的哪一侧，不改变原始表格或 CSV 数据。
 */
internal enum class PerformanceStatusChartMetric(
    val label: String,
    val minimumMaximum: Double,
    val valueKind: PerformanceStatusChartValueKind,
    val extract: (PerformanceStatusSample) -> Double?,
) {
    CLIENT_FPS("FPS", 60.0, PerformanceStatusChartValueKind.NUMBER, { it.client.fps.toDouble() }),
    CLIENT_FRAME_TIME("帧耗时", 50.0, PerformanceStatusChartValueKind.MILLISECONDS, { it.client.frameTimeMs }),
    CLIENT_TICK_INTERVAL("客户端 tick 间隔", 50.0, PerformanceStatusChartValueKind.MILLISECONDS, {
        it.client.tickIntervalMs
    }),
    CLIENT_TPS("客户端 TPS", 20.0, PerformanceStatusChartValueKind.NUMBER, { it.client.clientTps }),
    CLIENT_PARTICLES("Particles", 1.0, PerformanceStatusChartValueKind.NUMBER, { it.client.particles.toDouble() }),
    CLIENT_CPARTICLES("CParticles", 1.0, PerformanceStatusChartValueKind.NUMBER, { it.client.cParticles.toDouble() }),
    CLIENT_CPARTICLE_SYSTEMS("CParticle 系统", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.client.cParticleSystems.toDouble()
    }),
    CLIENT_SOUND_INSTANCES("SoundInstances", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.client.soundInstances.toDouble()
    }),
    CLIENT_MANAGED_SOUNDS("Coo 声音", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.client.managedSoundInstances.toDouble()
    }),
    CLIENT_SOUND_LOOPS("声音循环", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.client.soundLoops.toDouble()
    }),
    CLIENT_RENDER_ENTITIES("客户端 RenderEntities", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.client.renderEntities.toDouble()
    }),
    CLIENT_DISPLAY_ENTITIES("客户端 DisplayEntities", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.client.displayEntities.toDouble()
    }),
    CLIENT_EMITTERS("客户端 Emitters", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.client.emitters.toDouble()
    }),
    CLIENT_COMPOSITIONS("客户端 Compositions", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.client.compositions.toDouble()
    }),
    CLIENT_COOFX_SCENES("CooFX 场景", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.client.cooFxScenes.toDouble()
    }),
    CLIENT_COOFX_PARTICLES("CooFX 粒子", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.client.cooFxParticles.toDouble()
    }),
    CLIENT_COOFX_MODELS("CooFX 模型", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.client.cooFxModels.toDouble()
    }),
    CLIENT_TERRAIN_GROUPS("地形效果组", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.client.terrainEffectGroups.toDouble()
    }),
    CLIENT_TERRAIN_MAPPINGS("地形映射", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.client.terrainMappings.toDouble()
    }),
    CLIENT_POST_EFFECTS("后处理", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.client.postEffects.toDouble()
    }),
    CLIENT_HEAP_USED("客户端堆内存", 1_048_576.0, PerformanceStatusChartValueKind.BYTES, {
        it.client.heapUsedBytes.toDouble()
    }),
    CLIENT_GC_COUNT("客户端 GC 次数", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.client.gcCollectionCount.toDouble()
    }),
    CLIENT_GC_TIME("客户端 GC 耗时", 1.0, PerformanceStatusChartValueKind.MILLISECONDS, {
        it.client.gcCollectionTimeMs.toDouble()
    }),

    SERVER_TPS("服务端 TPS", 20.0, PerformanceStatusChartValueKind.NUMBER, { it.server?.tps }),
    SERVER_TARGET_TPS("目标 TPS", 20.0, PerformanceStatusChartValueKind.NUMBER, { it.server?.targetTps }),
    SERVER_SNAPSHOT_AGE("快照延迟", 1_000.0, PerformanceStatusChartValueKind.MILLISECONDS, {
        it.serverSnapshotAgeMillis?.toDouble()
    }),
    SERVER_AVERAGE_MSPT("MSPT 平均", 50.0, PerformanceStatusChartValueKind.MILLISECONDS, {
        it.server?.averageMspt
    }),
    SERVER_P95_MSPT("MSPT P95", 50.0, PerformanceStatusChartValueKind.MILLISECONDS, { it.server?.p95Mspt }),
    SERVER_MAX_MSPT("MSPT 最大", 50.0, PerformanceStatusChartValueKind.MILLISECONDS, { it.server?.maxMspt }),
    SERVER_PLAYERS("在线玩家", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.server?.onlinePlayers?.toDouble()
    }),
    SERVER_PARTICLE_GROUPS("ParticleGroups", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.server?.particleGroups?.toDouble()
    }),
    SERVER_RENDER_ENTITIES("服务端 RenderEntities", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.server?.renderEntities?.toDouble()
    }),
    SERVER_DISPLAY_ENTITIES("服务端 DisplayEntities", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.server?.displayEntities?.toDouble()
    }),
    SERVER_EMITTERS("服务端 Emitters", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.server?.emitters?.toDouble()
    }),
    SERVER_COMPOSITIONS("服务端 Compositions", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.server?.compositions?.toDouble()
    }),
    SERVER_TERRAIN_GROUPS("服务端地形效果组", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.server?.terrainEffectGroups?.toDouble()
    }),
    SERVER_TERRAIN_MAPPINGS("服务端地形映射", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.server?.terrainMappings?.toDouble()
    }),
    SERVER_SOUND_INSTANCES("服务端声音", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.server?.soundInstances?.toDouble()
    }),
    SERVER_SOUND_LOOPS("服务端声音循环", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.server?.soundLoops?.toDouble()
    }),
    SERVER_BARRAGES("Barrages", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.server?.barrages?.toDouble()
    }),
    SERVER_COOFX_SCENES("服务端 CooFX 场景", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.server?.cooFxScenes?.toDouble()
    }),
    SERVER_HEAP_USED("服务端堆内存", 1_048_576.0, PerformanceStatusChartValueKind.BYTES, {
        it.server?.heapUsedBytes?.toDouble()
    }),
    SERVER_GC_COUNT("服务端 GC 次数", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.server?.gcCollectionCount?.toDouble()
    }),
    SERVER_GC_TIME("服务端 GC 耗时", 1.0, PerformanceStatusChartValueKind.MILLISECONDS, {
        it.server?.gcCollectionTimeMs?.toDouble()
    }),

    CLIENT_PACKETS_SENT("CooPacket 上传包/tick", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.clientNetworkDelta.sentPackets.toDouble()
    }),
    CLIENT_BYTES_SENT("CooPacket 上传字节/tick", 1.0, PerformanceStatusChartValueKind.BYTES, {
        it.clientNetworkDelta.sentBytes.toDouble()
    }),
    CLIENT_PACKETS_RECEIVED("CooPacket 下载包/tick", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.clientNetworkDelta.receivedPackets.toDouble()
    }),
    CLIENT_BYTES_RECEIVED("CooPacket 下载字节/tick", 1.0, PerformanceStatusChartValueKind.BYTES, {
        it.clientNetworkDelta.receivedBytes.toDouble()
    }),
    SERVER_PACKETS_SENT("服务端 CooPacket 上传包/快照", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.serverNetworkDelta?.sentPackets?.toDouble()
    }),
    SERVER_BYTES_SENT("服务端 CooPacket 上传字节/快照", 1.0, PerformanceStatusChartValueKind.BYTES, {
        it.serverNetworkDelta?.sentBytes?.toDouble()
    }),
    SERVER_PACKETS_RECEIVED("服务端 CooPacket 下载包/快照", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.serverNetworkDelta?.receivedPackets?.toDouble()
    }),
    SERVER_BYTES_RECEIVED("服务端 CooPacket 下载字节/快照", 1.0, PerformanceStatusChartValueKind.BYTES, {
        it.serverNetworkDelta?.receivedBytes?.toDouble()
    }),
    CLIENT_VANILLA_PACKETS_SENT("原版上传包/聚合窗口", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.clientVanillaPacketDelta?.sentPackets?.toDouble()
    }),
    CLIENT_VANILLA_PACKETS_RECEIVED("原版下载包/聚合窗口", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.clientVanillaPacketDelta?.receivedPackets?.toDouble()
    }),
    SERVER_VANILLA_PACKETS_SENT("服务端原版上传包/快照", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.serverVanillaPacketDelta?.sentPackets?.toDouble()
    }),
    SERVER_VANILLA_PACKETS_RECEIVED("服务端原版下载包/快照", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.serverVanillaPacketDelta?.receivedPackets?.toDouble()
    });

    /** 返回该原始指标在相关性计算中的角色。 */
    val role: PerformanceStatusChartMetricRole
        get() = when (this) {
            CLIENT_FPS,
            CLIENT_FRAME_TIME,
            CLIENT_TICK_INTERVAL,
            CLIENT_TPS,
            CLIENT_HEAP_USED,
            CLIENT_GC_COUNT,
            CLIENT_GC_TIME,
            SERVER_TPS,
            SERVER_TARGET_TPS,
            SERVER_SNAPSHOT_AGE,
            SERVER_AVERAGE_MSPT,
            SERVER_P95_MSPT,
            SERVER_MAX_MSPT,
            SERVER_HEAP_USED,
            SERVER_GC_COUNT,
            SERVER_GC_TIME,
            CLIENT_PACKETS_SENT,
            CLIENT_BYTES_SENT,
            CLIENT_PACKETS_RECEIVED,
            CLIENT_BYTES_RECEIVED,
            SERVER_PACKETS_SENT,
            SERVER_BYTES_SENT,
            SERVER_PACKETS_RECEIVED,
            SERVER_BYTES_RECEIVED,
            CLIENT_VANILLA_PACKETS_SENT,
            CLIENT_VANILLA_PACKETS_RECEIVED,
            SERVER_VANILLA_PACKETS_SENT,
            SERVER_VANILLA_PACKETS_RECEIVED -> PerformanceStatusChartMetricRole.PERFORMANCE
            else -> PerformanceStatusChartMetricRole.IMPACT
        }
}

/** 可持久化到当前客户端会话的原始曲线或性能/影响项比值曲线。 */
internal sealed interface PerformanceStatusChartSelection {
    /** 图例中显示的曲线名称。 */
    val label: String

    /** 曲线自动量程的最小上界。 */
    val minimumMaximum: Double

    /** 图例数值格式。 */
    val valueKind: PerformanceStatusChartValueKind

    /** 从关联样本提取曲线值；缺失或不可计算时返回 null。 */
    fun extract(sample: PerformanceStatusSample): Double?

    /** 一个原始指标曲线。 */
    data class Metric(val metric: PerformanceStatusChartMetric) : PerformanceStatusChartSelection {
        override val label: String = metric.label
        override val minimumMaximum: Double = metric.minimumMaximum
        override val valueKind: PerformanceStatusChartValueKind = metric.valueKind

        override fun extract(sample: PerformanceStatusSample): Double? = metric.extract(sample)
    }

    /** 一个影响项除以性能项的实时负载比值曲线。 */
    data class Ratio(
        val impact: PerformanceStatusChartMetric,
        val performance: PerformanceStatusChartMetric,
    ) : PerformanceStatusChartSelection {
        override val label: String = "${impact.label} / ${performance.label}"
        override val minimumMaximum: Double = 1.0
        override val valueKind: PerformanceStatusChartValueKind = PerformanceStatusChartValueKind.NUMBER

        override fun extract(sample: PerformanceStatusSample): Double? {
            val impactValue = impact.extract(sample) ?: return null
            val performanceValue = performance.extract(sample) ?: return null
            if (!impactValue.isFinite() || !performanceValue.isFinite() || performanceValue <= 0.0) return null
            return (impactValue / performanceValue).takeIf(Double::isFinite)
        }
    }
}
