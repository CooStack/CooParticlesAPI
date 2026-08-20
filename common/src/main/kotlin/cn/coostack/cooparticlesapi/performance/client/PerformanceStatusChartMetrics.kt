package cn.coostack.cooparticlesapi.performance.client

/** 图表图例中数值的显示单位。 */
internal enum class PerformanceStatusChartValueKind {
    NUMBER,
    MILLISECONDS,
    BYTES,
}

/**
 * 可在 Status 实时折线图中选择的单值维度。
 *
 * @property label 图例与指标表使用的短名称
 * @property minimumMaximum 自动量程的最小上界，防止细小波动被过度放大
 * @property valueKind 图例数值格式
 * @property extract 从一行关联样本中取得当前值
 */
internal enum class PerformanceStatusChartMetric(
    val label: String,
    val minimumMaximum: Double,
    val valueKind: PerformanceStatusChartValueKind,
    val extract: (PerformanceStatusSample) -> Double?,
) {
    CLIENT_FPS("FPS", 60.0, PerformanceStatusChartValueKind.NUMBER, { it.client.fps.toDouble() }),
    CLIENT_FRAME_TIME("Frame ms", 50.0, PerformanceStatusChartValueKind.MILLISECONDS, { it.client.frameTimeMs }),
    CLIENT_TICK_INTERVAL("Client tick ms", 50.0, PerformanceStatusChartValueKind.MILLISECONDS, {
        it.client.tickIntervalMs
    }),
    CLIENT_TPS("Client TPS", 20.0, PerformanceStatusChartValueKind.NUMBER, { it.client.clientTps }),
    CLIENT_PARTICLES("Particles", 1.0, PerformanceStatusChartValueKind.NUMBER, { it.client.particles.toDouble() }),
    CLIENT_CPARTICLES("CParticles", 1.0, PerformanceStatusChartValueKind.NUMBER, { it.client.cParticles.toDouble() }),
    CLIENT_CPARTICLE_SYSTEMS("CParticle systems", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.client.cParticleSystems.toDouble()
    }),
    CLIENT_SOUND_INSTANCES("SoundInstances", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.client.soundInstances.toDouble()
    }),
    CLIENT_MANAGED_SOUNDS("Coo sounds", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.client.managedSoundInstances.toDouble()
    }),
    CLIENT_SOUND_LOOPS("Sound loops", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.client.soundLoops.toDouble()
    }),
    CLIENT_RENDER_ENTITIES("Client RenderEntities", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.client.renderEntities.toDouble()
    }),
    CLIENT_DISPLAY_ENTITIES("Client DisplayEntities", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.client.displayEntities.toDouble()
    }),
    CLIENT_EMITTERS("Client Emitters", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.client.emitters.toDouble()
    }),
    CLIENT_COMPOSITIONS("Client Compositions", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.client.compositions.toDouble()
    }),
    CLIENT_COOFX_SCENES("CooFX scenes", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.client.cooFxScenes.toDouble()
    }),
    CLIENT_COOFX_PARTICLES("CooFX particles", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.client.cooFxParticles.toDouble()
    }),
    CLIENT_COOFX_MODELS("CooFX models", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.client.cooFxModels.toDouble()
    }),
    CLIENT_TERRAIN_GROUPS("Terrain groups", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.client.terrainEffectGroups.toDouble()
    }),
    CLIENT_TERRAIN_MAPPINGS("Terrain mappings", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.client.terrainMappings.toDouble()
    }),
    CLIENT_POST_EFFECTS("Post effects", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.client.postEffects.toDouble()
    }),
    CLIENT_HEAP_USED("Client heap", 1_048_576.0, PerformanceStatusChartValueKind.BYTES, {
        it.client.heapUsedBytes.toDouble()
    }),

    SERVER_TPS("Server TPS", 20.0, PerformanceStatusChartValueKind.NUMBER, { it.server?.tps }),
    SERVER_TARGET_TPS("Target TPS", 20.0, PerformanceStatusChartValueKind.NUMBER, { it.server?.targetTps }),
    SERVER_SNAPSHOT_AGE("Snapshot age", 1_000.0, PerformanceStatusChartValueKind.MILLISECONDS, {
        it.serverSnapshotAgeMillis?.toDouble()
    }),
    SERVER_AVERAGE_MSPT("MSPT avg", 50.0, PerformanceStatusChartValueKind.MILLISECONDS, {
        it.server?.averageMspt
    }),
    SERVER_P95_MSPT("MSPT P95", 50.0, PerformanceStatusChartValueKind.MILLISECONDS, { it.server?.p95Mspt }),
    SERVER_MAX_MSPT("MSPT max", 50.0, PerformanceStatusChartValueKind.MILLISECONDS, { it.server?.maxMspt }),
    SERVER_PLAYERS("Players", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.server?.onlinePlayers?.toDouble()
    }),
    SERVER_PARTICLE_GROUPS("ParticleGroups", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.server?.particleGroups?.toDouble()
    }),
    SERVER_RENDER_ENTITIES("Server RenderEntities", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.server?.renderEntities?.toDouble()
    }),
    SERVER_DISPLAY_ENTITIES("Server DisplayEntities", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.server?.displayEntities?.toDouble()
    }),
    SERVER_EMITTERS("Server Emitters", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.server?.emitters?.toDouble()
    }),
    SERVER_COMPOSITIONS("Server Compositions", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.server?.compositions?.toDouble()
    }),
    SERVER_TERRAIN_GROUPS("Server terrain groups", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.server?.terrainEffectGroups?.toDouble()
    }),
    SERVER_TERRAIN_MAPPINGS("Server terrain mappings", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.server?.terrainMappings?.toDouble()
    }),
    SERVER_SOUND_INSTANCES("Server sounds", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.server?.soundInstances?.toDouble()
    }),
    SERVER_SOUND_LOOPS("Server sound loops", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.server?.soundLoops?.toDouble()
    }),
    SERVER_BARRAGES("Barrages", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.server?.barrages?.toDouble()
    }),
    SERVER_COOFX_SCENES("Server CooFX scenes", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.server?.cooFxScenes?.toDouble()
    }),
    SERVER_HEAP_USED("Server heap", 1_048_576.0, PerformanceStatusChartValueKind.BYTES, {
        it.server?.heapUsedBytes?.toDouble()
    }),

    CLIENT_PACKETS_SENT("Upload packets/tick", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.clientNetworkDelta.sentPackets.toDouble()
    }),
    CLIENT_BYTES_SENT("Upload bytes/tick", 1.0, PerformanceStatusChartValueKind.BYTES, {
        it.clientNetworkDelta.sentBytes.toDouble()
    }),
    CLIENT_PACKETS_RECEIVED("Download packets/tick", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.clientNetworkDelta.receivedPackets.toDouble()
    }),
    CLIENT_BYTES_RECEIVED("Download bytes/tick", 1.0, PerformanceStatusChartValueKind.BYTES, {
        it.clientNetworkDelta.receivedBytes.toDouble()
    }),
    SERVER_PACKETS_SENT("Server packets/interval", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.serverNetworkDelta?.sentPackets?.toDouble()
    }),
    SERVER_BYTES_SENT("Server bytes/interval", 1.0, PerformanceStatusChartValueKind.BYTES, {
        it.serverNetworkDelta?.sentBytes?.toDouble()
    }),
    SERVER_PACKETS_RECEIVED("Server received/interval", 1.0, PerformanceStatusChartValueKind.NUMBER, {
        it.serverNetworkDelta?.receivedPackets?.toDouble()
    }),
    SERVER_BYTES_RECEIVED("Server received bytes/interval", 1.0, PerformanceStatusChartValueKind.BYTES, {
        it.serverNetworkDelta?.receivedBytes?.toDouble()
    }),
}
