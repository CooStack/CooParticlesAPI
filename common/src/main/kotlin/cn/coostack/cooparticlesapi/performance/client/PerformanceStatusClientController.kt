package cn.coostack.cooparticlesapi.performance.client

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.coofx.client.CooFXClient
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemManager
import cn.coostack.cooparticlesapi.display.DisplayEntityManager
import cn.coostack.cooparticlesapi.network.packet.api.CooClientPacketManager
import cn.coostack.cooparticlesapi.network.packet.status.PacketPerformanceStatusRequestC2S
import cn.coostack.cooparticlesapi.network.packet.status.PacketPerformanceStatusSnapshotS2C
import cn.coostack.cooparticlesapi.network.particle.composition.manager.ParticleCompositionManager
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.performance.PerformanceStatusControlAction
import cn.coostack.cooparticlesapi.performance.PerformanceStatusJvmMetrics
import cn.coostack.cooparticlesapi.performance.PerformanceStatusNetworkEndpoint
import cn.coostack.cooparticlesapi.performance.PerformanceStatusNetworkMetrics
import cn.coostack.cooparticlesapi.performance.PerformanceStatusNetworkTotals
import cn.coostack.cooparticlesapi.performance.PerformanceStatusServerSnapshot
import cn.coostack.cooparticlesapi.performance.PerformanceStatusVanillaPacketTotals
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderEntityManager
import cn.coostack.cooparticlesapi.renderer.post.CooPostEffects
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramRegistry
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainEffectRegistry
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainMappingRegistry
import cn.coostack.cooparticlesapi.supports.sound.ClientSoundLoopManager
import cn.coostack.cooparticlesapi.supports.sound.ClientSoundManager
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.io.BufferedWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.ArrayList

/**
 * 客户端 Status 会话、按需服务端请求、实时趋势窗口和流式 CSV 的单一所有者。
 *
 * 所有方法都应在 Minecraft 客户端线程调用；每 tick 只写一行缓冲文本并保留有限 GUI 历史。
 */
object PerformanceStatusClientController {
    /** 当前 CSV 输出 writer；非活动会话为 null。 */
    private var writer: BufferedWriter? = null

    /** 当前会话输出路径。 */
    private var currentOutputPath: Path? = null

    /** 最近一次完成会话的输出路径。 */
    private var lastOutputPath: Path? = null

    /** 当前 GUI 是否打开。 */
    private var guiOpen = false

    /** 当前未完成服务端请求的 correlation ID。 */
    private var pendingRequestId = 0L

    /** 本地请求超时倒计时。 */
    private var pendingRequestTicks = 0

    /** 下次允许发送服务端请求前的客户端 tick 数。 */
    private var requestDelayTicks = 0

    /** 服务端首个快照下发的后续请求间隔。 */
    private var serverRefreshIntervalTicks: Int? = null

    /** 当前会话开始的单调时钟纳秒。 */
    private var startedAtNanos = 0L

    /** 上一次客户端采样的单调时钟纳秒。 */
    private var previousTickNanos = 0L

    /** 最近一秒的有效客户端 tick 间隔，用于消除单 tick 调度抖动。 */
    private val recentTickIntervalsMs = ArrayDeque<Double>()

    /** 当前会话下一行样本序号。 */
    private var nextSampleIndex = 0L

    /** 上一次客户端 CooPacket 累计值。 */
    private var previousClientNetworkTotals = PerformanceStatusNetworkTotals(0L, 0L, 0L, 0L)

    /** 上一次客户端原版 Packet 累计值。 */
    private var previousClientVanillaPacketTotals = PerformanceStatusVanillaPacketTotals(0L, 0L)

    /** 上一次收到的服务端 CooPacket 累计值。 */
    private var previousServerNetworkTotals: PerformanceStatusNetworkTotals? = null

    /** 上一次收到的服务端原版 Packet 累计值。 */
    private var previousServerVanillaPacketTotals: PerformanceStatusVanillaPacketTotals? = null

    /** 等待写入下一行的服务端网络增量；没有新快照时为 null。 */
    private var pendingServerNetworkDelta: PerformanceStatusNetworkTotals? = null

    /** 等待纳入当前原版 Packet 聚合窗口的服务端增量。 */
    private var pendingServerVanillaPacketDelta: PerformanceStatusVanillaPacketTotals? = null

    /** 当前原版 Packet 聚合窗口的客户端累计增量。 */
    private var clientVanillaPacketBucket = PerformanceStatusVanillaPacketTotals(0L, 0L)

    /** 当前聚合窗口已经采集的客户端 tick 数。 */
    private var vanillaPacketAggregationTickCount = 0

    /** 当前正在使用的原版 Packet 聚合窗口大小。 */
    private var vanillaPacketAggregationTicks = 1

    /** 最近一次有效服务端快照。 */
    private var latestServerSnapshot: PerformanceStatusServerSnapshot? = null

    /** 客户端收到最近一次服务端快照的单调时钟纳秒。 */
    private var latestServerReceivedAtNanos = 0L

    /** 最近一行客户端/服务端关联样本。 */
    private var latestSample: PerformanceStatusSample? = null

    /** GUI 趋势历史最大保留时长，单位为秒；由 Status GUI 输入框调整。 */
    private var historyDurationSeconds = 60L

    /** 返回 GUI 趋势历史最大保留时长，单位为秒。 */
    fun historyDurationSeconds(): Long = historyDurationSeconds

    /** 更新 GUI 趋势历史最大保留时长；最小值为 1 秒。 */
    fun updateHistoryDurationSeconds(seconds: Long): Boolean {
        if (seconds < 1L) return false
        historyDurationSeconds = seconds
        trimHistory()
        return true
    }

    /** GUI 使用的按时长裁剪、可随机访问趋势历史。 */
    private val history = ArrayList<PerformanceStatusSample>()

    /** 趋势历史中第一个有效样本的位置，避免每 tick 移动整个数组。 */
    private var historyStartIndex = 0

    /** 返回当前是否正在持续记录。 */
    fun isRecording(): Boolean = writer != null

    /** 返回当前是否需要采样和按需请求服务端指标。 */
    private fun isActive(): Boolean = isRecording() || guiOpen

    /** 跨 GUI 打开周期保留的原始指标和比值曲线选择。 */
    private val selectedChartSelections = linkedSetOf<PerformanceStatusChartSelection>(
        PerformanceStatusChartSelection.Metric(PerformanceStatusChartMetric.CLIENT_FPS),
        PerformanceStatusChartSelection.Metric(PerformanceStatusChartMetric.SERVER_TPS),
        PerformanceStatusChartSelection.Metric(PerformanceStatusChartMetric.CLIENT_CPARTICLES),
    )

    /** 返回按选择顺序排列的图表曲线副本。 */
    internal fun selectedChartSelections(): List<PerformanceStatusChartSelection> = selectedChartSelections.toList()

    /** 保存当前 Screen 的原始指标和比值曲线选择，最多保留 12 项。 */
    internal fun updateSelectedChartSelections(selections: Collection<PerformanceStatusChartSelection>) {
        selectedChartSelections.clear()
        selectedChartSelections.addAll(selections.take(PERFORMANCE_STATUS_MAX_SELECTED_SERIES))
    }

    /** 返回当前仍被选中的原始指标，供兼容性测试和旧调用方读取。 */
    internal fun selectedChartMetrics(): List<PerformanceStatusChartMetric> {
        return selectedChartSelections.mapNotNull { selection ->
            (selection as? PerformanceStatusChartSelection.Metric)?.metric
        }
    }

    /** 保存原始指标选择；旧调用方不会清除已存在的比值曲线。 */
    internal fun updateSelectedChartMetrics(metrics: Collection<PerformanceStatusChartMetric>) {
        val rawSelections = metrics.map(PerformanceStatusChartSelection::Metric)
        val ratios = selectedChartSelections.filterIsInstance<PerformanceStatusChartSelection.Ratio>()
        updateSelectedChartSelections(rawSelections + ratios)
    }

    /** 返回当前原版 Packet 聚合窗口，单位为客户端 tick。 */
    fun vanillaPacketAggregationTicks(): Int {
        return CooParticlesServices.API_CONFIG_MANAGER.getConfig().statusVanillaPacketAggregationTicks
    }

    /** 返回最近一行样本。 */
    fun latestSample(): PerformanceStatusSample? = latestSample

    /** 返回当前客户端线程内可随机访问的趋势窗口，不复制完整历史。 */
    fun historySnapshot(): List<PerformanceStatusSample> {
        if (historyStartIndex >= history.size) return emptyList()
        return history.subList(historyStartIndex, history.size)
    }

    /** 返回当前或最近一次完成会话的 CSV 路径。 */
    fun outputPath(): Path? = currentOutputPath ?: lastOutputPath

    /** 接收服务端命令下发的客户端会话动作。 */
    fun handleControl(action: PerformanceStatusControlAction) {
        when (action) {
            PerformanceStatusControlAction.START -> start()
            PerformanceStatusControlAction.STOP -> stop(closeScreen = true)
            PerformanceStatusControlAction.GUI -> openGui()
        }
    }

    /** 开始新的 CSV 记录会话；已有记录会话不切分文件。 */
    fun start(): Path? {
        if (isRecording()) return currentOutputPath
        val client = Minecraft.getInstance()
        val outputDirectory = client.gameDirectory.toPath().resolve("cooparticlesapi-status")
        return runCatching {
            Files.createDirectories(outputDirectory)
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"))
            val outputPath = outputDirectory.resolve("status-$timestamp.csv")
            val openedWriter = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)
            openedWriter.write(PerformanceStatusCsv.header())
            openedWriter.newLine()
            writer = openedWriter
            currentOutputPath = outputPath
            resetSamplingState()
            client.player?.displayClientMessage(
                Component.literal("CooParticles Status 已开始记录：$outputPath"),
                false,
            )
            outputPath
        }.onFailure { failure ->
            CooParticlesConstants.logger.error("无法创建 CooParticles Status CSV", failure)
            writer = null
            currentOutputPath = null
            client.player?.displayClientMessage(
                Component.literal("CooParticles Status 无法创建 CSV：${failure.message}"),
                false,
            )
        }.getOrNull()
    }

    /** 停止采样、关闭 writer，并可关闭当前 Status GUI。 */
    fun stop(closeScreen: Boolean = false): Path? {
        cancelPendingRequest()
        val completedPath = currentOutputPath
        writer?.let { activeWriter ->
            runCatching {
                activeWriter.flush()
                activeWriter.close()
            }.onFailure { failure ->
                CooParticlesConstants.logger.error("关闭 CooParticles Status CSV 失败", failure)
            }
        }
        writer = null
        currentOutputPath = null
        completedPath?.let { lastOutputPath = it }
        guiOpen = false
        latestServerSnapshot = null
        latestServerReceivedAtNanos = 0L
        previousServerNetworkTotals = null
        previousServerVanillaPacketTotals = null
        pendingServerNetworkDelta = null
        pendingServerVanillaPacketDelta = null
        serverRefreshIntervalTicks = null
        if (closeScreen) {
            val client = Minecraft.getInstance()
            if (client.screen is PerformanceStatusScreen) client.setScreen(null)
        }
        completedPath?.let { path ->
            Minecraft.getInstance().player?.displayClientMessage(
                Component.literal("CooParticles Status 已导出：$path"),
                false,
            )
        }
        return completedPath
    }

    /** 打开纯实时 GUI；没有 CSV 会话时只创建内存趋势窗口。 */
    fun openGui() {
        val client = Minecraft.getInstance()
        if (client.screen is PerformanceStatusScreen) return
        if (!isActive()) resetSamplingState()
        guiOpen = true
        client.setScreen(PerformanceStatusScreen())
    }

    /** GUI 被关闭时停止纯查看采样，但不结束独立的 CSV 记录会话。 */
    fun onScreenClosed() {
        guiOpen = false
        if (!isRecording()) cancelPendingRequest()
    }

    /** 客户端每个 END_CLIENT_TICK 调用一次，推进活动查看或记录会话。 */
    fun onClientTick(client: Minecraft) {
        if (!isActive()) return
        updatePendingRequest()
        if (client.connection != null && client.level != null) {
            requestServerSnapshotIfDue()
        }
        appendSample(client)
    }

    /** 断线时结束会话并确保已经采集的数据落盘。 */
    fun onDisconnect() {
        stop(closeScreen = true)
    }

    /** 换维度时取消旧世界请求，但保留显式会话与同一个 CSV。 */
    fun onWorldChanged() {
        cancelPendingRequest()
        latestServerSnapshot = null
        latestServerReceivedAtNanos = 0L
        previousServerNetworkTotals = null
        previousServerVanillaPacketTotals = null
        pendingServerNetworkDelta = null
        pendingServerVanillaPacketDelta = null
        requestDelayTicks = 0
        serverRefreshIntervalTicks = null
    }

    /** 客户端关闭前结束会话并刷新输出。 */
    @JvmStatic
    fun shutdown() {
        stop(closeScreen = false)
    }

    /** 为新的查看或记录会话重置有限历史、网络基线和请求节奏。 */
    private fun resetSamplingState() {
        startedAtNanos = System.nanoTime()
        previousTickNanos = 0L
        recentTickIntervalsMs.clear()
        nextSampleIndex = 0L
        previousClientNetworkTotals = PerformanceStatusNetworkMetrics.snapshot(
            PerformanceStatusNetworkEndpoint.CLIENT
        )
        previousClientVanillaPacketTotals = PerformanceStatusNetworkMetrics.vanillaSnapshot(
            PerformanceStatusNetworkEndpoint.CLIENT
        )
        previousServerNetworkTotals = null
        previousServerVanillaPacketTotals = null
        pendingServerNetworkDelta = null
        pendingServerVanillaPacketDelta = null
        clientVanillaPacketBucket = PerformanceStatusVanillaPacketTotals(0L, 0L)
        vanillaPacketAggregationTickCount = 0
        vanillaPacketAggregationTicks = vanillaPacketAggregationTicks()
        latestServerSnapshot = null
        latestServerReceivedAtNanos = 0L
        latestSample = null
        history.clear()
        historyStartIndex = 0
        requestDelayTicks = 0
        serverRefreshIntervalTicks = null
        cancelPendingRequest()
    }

    /** 推进本地请求超时，避免外部请求管理器超时后控制器永久等待。 */
    private fun updatePendingRequest() {
        if (pendingRequestId == 0L) return
        pendingRequestTicks--
        if (pendingRequestTicks <= 0) cancelPendingRequest()
    }

    /** 根据配置的客户端 tick 间隔发送至多一个并发服务端请求。 */
    private fun requestServerSnapshotIfDue() {
        if (requestDelayTicks > 0) requestDelayTicks--
        if (pendingRequestId != 0L || requestDelayTicks > 0) return
        val interval = serverRefreshIntervalTicks
            ?: CooParticlesServices.API_CONFIG_MANAGER.getConfig().statusServerRefreshIntervalTicks
        val correlationId = CooClientPacketManager.request(
            packet = PacketPerformanceStatusRequestC2S(),
            responseType = PacketPerformanceStatusSnapshotS2C::class.java,
            timeoutTicks = 60,
        ) { packet ->
            pendingRequestId = 0L
            pendingRequestTicks = 0
            acceptServerSnapshot(packet.toSnapshot())
        }
        if (correlationId != 0L) {
            pendingRequestId = correlationId
            pendingRequestTicks = 60
        }
        requestDelayTicks = interval
    }

    /** 接受未乱序的服务端快照，并计算快照间 CooPacket 增量。 */
    private fun acceptServerSnapshot(snapshot: PerformanceStatusServerSnapshot) {
        val previousSnapshot = latestServerSnapshot
        if (previousSnapshot != null && snapshot.serverTick < previousSnapshot.serverTick) return
        val previousNetwork = previousServerNetworkTotals
        pendingServerNetworkDelta = previousNetwork?.let(snapshot.cooPackets::deltaFrom)
        previousServerNetworkTotals = snapshot.cooPackets
        val previousVanillaPackets = previousServerVanillaPacketTotals
        pendingServerVanillaPacketDelta = previousVanillaPackets?.let(snapshot.vanillaPackets::deltaFrom)
        previousServerVanillaPacketTotals = snapshot.vanillaPackets
        latestServerSnapshot = snapshot
        latestServerReceivedAtNanos = System.nanoTime()
        serverRefreshIntervalTicks = snapshot.refreshIntervalTicks
        requestDelayTicks = snapshot.refreshIntervalTicks
    }

    /** 采集客户端 O(1) 指标并把关联行追加到 CSV 和趋势窗口。 */
    private fun appendSample(client: Minecraft) {
        val nowNanos = System.nanoTime()
        val nowMillis = System.currentTimeMillis()
        val tickIntervalMs = if (previousTickNanos == 0L) {
            0.0
        } else {
            (nowNanos - previousTickNanos).coerceAtLeast(0L) / 1_000_000.0
        }
        previousTickNanos = nowNanos
        if (tickIntervalMs > 0.0) {
            recentTickIntervalsMs.addLast(tickIntervalMs)
            while (recentTickIntervalsMs.size > 20) recentTickIntervalsMs.removeFirst()
        }
        val clientNetwork = PerformanceStatusNetworkMetrics.snapshot(PerformanceStatusNetworkEndpoint.CLIENT)
        val clientNetworkDelta = clientNetwork.deltaFrom(previousClientNetworkTotals)
        previousClientNetworkTotals = clientNetwork
        val clientVanillaPackets = PerformanceStatusNetworkMetrics.vanillaSnapshot(
            PerformanceStatusNetworkEndpoint.CLIENT
        )
        clientVanillaPacketBucket += clientVanillaPackets.deltaFrom(previousClientVanillaPacketTotals)
        previousClientVanillaPacketTotals = clientVanillaPackets
        val configuredAggregationTicks = vanillaPacketAggregationTicks()
        if (configuredAggregationTicks != vanillaPacketAggregationTicks) {
            vanillaPacketAggregationTicks = configuredAggregationTicks
            vanillaPacketAggregationTickCount = 0
            clientVanillaPacketBucket = PerformanceStatusVanillaPacketTotals(0L, 0L)
        }
        vanillaPacketAggregationTickCount++
        val aggregationWindowComplete = vanillaPacketAggregationTickCount >= vanillaPacketAggregationTicks
        val clientVanillaPacketDelta = clientVanillaPacketBucket.takeIf { aggregationWindowComplete }
        if (aggregationWindowComplete) {
            vanillaPacketAggregationTickCount = 0
            clientVanillaPacketBucket = PerformanceStatusVanillaPacketTotals(0L, 0L)
        }
        val runtime = Runtime.getRuntime()
        val gc = PerformanceStatusJvmMetrics.snapshot()
        val fps = client.fps
        val clientSnapshot = PerformanceStatusClientSnapshot(
            fps = fps,
            frameTimeMs = client.frameTimeNs / 1_000_000.0,
            tickIntervalMs = tickIntervalMs,
            clientTps = clientTpsFromTickIntervals(recentTickIntervalsMs),
            particles = client.particleEngine.countParticles().toIntOrNull() ?: 0,
            cParticles = CParticleSystemManager.totalAlive(),
            cParticleSystems = CParticleSystemManager.systemCount(),
            soundInstances = ClientSoundManager.vanillaSoundInstanceCount(),
            managedSoundInstances = ClientSoundManager.activeSoundCount(),
            soundLoops = ClientSoundLoopManager.activeLoopCount(),
            renderEntities = ClientRenderEntityManager.loadedEntityCount(),
            displayEntities = DisplayEntityManager.clientEntityCount(),
            emitters = ParticleEmittersManager.clientEmitterCount(),
            compositions = ParticleCompositionManager.loadedClientCount(),
            cooFxScenes = CooFXClient.activeSceneCount(),
            cooFxParticles = CooFXClient.activeParticleCount(),
            cooFxModels = CooFXClient.activeModelCount(),
            terrainEffectGroups = CooTerrainEffectRegistry.activeGroupCount(),
            terrainMappings = CooTerrainMappingRegistry.activeMappingCount(),
            postEffects = CooPostEffects.client.activeCount(),
            graphicsShaders = ShaderProgramRegistry.graphicsCount(),
            computeShaders = ShaderProgramRegistry.computeCount(),
            gcCollectionCount = gc.collectionCount,
            gcCollectionTimeMs = gc.collectionTimeMs,
            heapUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
            heapMaxBytes = runtime.maxMemory(),
            cooPackets = clientNetwork,
            vanillaPackets = clientVanillaPackets,
        )
        val serverSnapshot = latestServerSnapshot
        val sample = PerformanceStatusSample(
            sampleIndex = nextSampleIndex++,
            capturedAtEpochMillis = nowMillis,
            elapsedMillis = (nowNanos - startedAtNanos).coerceAtLeast(0L) / 1_000_000L,
            client = clientSnapshot,
            clientNetworkDelta = clientNetworkDelta,
            clientVanillaPacketDelta = clientVanillaPacketDelta,
            vanillaPacketAggregationTicks = vanillaPacketAggregationTicks,
            server = serverSnapshot,
            serverSnapshotAgeMillis = serverSnapshot?.let {
                (nowNanos - latestServerReceivedAtNanos).coerceAtLeast(0L) / 1_000_000L
            },
            serverNetworkDelta = pendingServerNetworkDelta,
            serverVanillaPacketDelta = pendingServerVanillaPacketDelta,
        )
        pendingServerNetworkDelta = null
        pendingServerVanillaPacketDelta = null
        latestSample = sample
        history.add(sample)
        trimHistory()
        writeSample(sample)
    }

    /** 按最新样本的 elapsedMillis 移动有效起点，并分批压缩失效前缀。 */
    private fun trimHistory() {
        val newestElapsedMillis = history.lastOrNull()?.elapsedMillis ?: return
        val maximumDurationMillis = historyDurationSeconds.toDouble() * 1_000.0
        while (historyStartIndex < history.lastIndex) {
            val oldest = history[historyStartIndex]
            if ((newestElapsedMillis - oldest.elapsedMillis).toDouble() <= maximumDurationMillis) break
            historyStartIndex++
        }
        val compactThreshold = 2_048
        if (historyStartIndex >= compactThreshold) {
            history.subList(0, historyStartIndex).clear()
            historyStartIndex = 0
        }
    }

    /** 写入一行样本，并按固定小批次刷新缓冲区。 */
    private fun writeSample(sample: PerformanceStatusSample) {
        val activeWriter = writer ?: return
        runCatching {
            activeWriter.write(PerformanceStatusCsv.row(sample))
            activeWriter.newLine()
            if (sample.sampleIndex % 20L == 0L) activeWriter.flush()
        }.onFailure { failure ->
            CooParticlesConstants.logger.error("写入 CooParticles Status CSV 失败", failure)
            stop(closeScreen = true)
        }
    }

    /** 取消当前请求并重置本地 correlation 状态。 */
    private fun cancelPendingRequest() {
        if (pendingRequestId != 0L) CooClientPacketManager.cancelRequest(pendingRequestId)
        pendingRequestId = 0L
        pendingRequestTicks = 0
    }
}
