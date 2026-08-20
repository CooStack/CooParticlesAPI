package cn.coostack.cooparticlesapi.performance.client

import com.mojang.math.Axis
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * 实时展示客户端、服务端与 CooPacket 当前指标及用户选择的多维趋势折线。
 */
class PerformanceStatusScreen : Screen(Component.literal("CooParticles Status")) {
    /** 当前表格视图。 */
    private var view = View.CLIENT

    /** 客户端视图分段按钮。 */
    private lateinit var clientTab: Button

    /** 服务端视图分段按钮。 */
    private lateinit var serverTab: Button

    /** 网络视图分段按钮。 */
    private lateinit var networkTab: Button

    /** 当前表格首行在完整指标列表中的偏移。 */
    private var rowOffset = 0

    /** 当前视图允许的最大滚动偏移。 */
    private var maxRowOffset = 0

    /** 跨分段保留的图表维度，迭代顺序同时决定曲线颜色。 */
    private val selectedMetrics = linkedSetOf(
        PerformanceStatusChartMetric.CLIENT_FPS,
        PerformanceStatusChartMetric.SERVER_TPS,
        PerformanceStatusChartMetric.CLIENT_CPARTICLES,
    )

    /** 最近一帧实际显示的可点击指标行。 */
    private var visibleMetricRows: List<MetricRow> = emptyList()

    /** 最近一帧指标表的可点击上边界。 */
    private var visibleRowsTop = 0

    /** 最近一帧指标表的可点击下边界。 */
    private var visibleRowsBottom = 0

    /** 创建视图分段按钮、结束按钮和关闭按钮。 */
    override fun init() {
        val tabWidth = ((width - 48) / 3).coerceIn(72, 120)
        val tabsLeft = (width - tabWidth * 3) / 2
        clientTab = Button.builder(Component.literal("客户端")) {
            selectView(View.CLIENT)
        }.bounds(tabsLeft, 28, tabWidth, 20).build()
        serverTab = Button.builder(Component.literal("服务器")) {
            selectView(View.SERVER)
        }.bounds(tabsLeft + tabWidth, 28, tabWidth, 20).build()
        networkTab = Button.builder(Component.literal("网络")) {
            selectView(View.NETWORK)
        }.bounds(tabsLeft + tabWidth * 2, 28, tabWidth, 20).build()
        addRenderableWidget(clientTab)
        addRenderableWidget(serverTab)
        addRenderableWidget(networkTab)
        selectView(view)
        val buttonY = (height - 28).coerceAtLeast(52)
        val actionWidth = ((width - 24) / 2).coerceIn(80, 120)
        addRenderableWidget(Button.builder(Component.literal("关闭")) {
            onClose()
        }.bounds((width - actionWidth) / 2, buttonY, actionWidth, 20).build())
    }

    /** Status 界面不暂停单人世界，保证采样和服务端请求继续推进。 */
    override fun isPauseScreen(): Boolean = false

    /** 绘制当前表格、趋势图和输出文件名。 */
    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics, mouseX, mouseY, partialTick)
        graphics.fill(8, 8, width - 8, height - 8, 0xD8101216.toInt())
        graphics.fill(8, 8, width - 8, 10, 0xFF4EA1D3.toInt())
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF)
        super.render(graphics, mouseX, mouseY, partialTick)
        val sample = PerformanceStatusClientController.latestSample()
        val tableTop = 56
        val chartHeight = (height / 3).coerceIn(96, 180)
        val chartBottom = (height - 50).coerceAtLeast(tableTop + chartHeight)
        val chartTop = chartBottom - chartHeight
        renderTable(graphics, sample, tableTop, chartTop - 6)
        renderChart(graphics, chartTop, chartBottom)
        val outputName = if (PerformanceStatusClientController.isRecording()) {
            PerformanceStatusClientController.outputPath()?.fileName?.toString().orEmpty()
        } else {
            ""
        }
        if (outputName.isNotEmpty()) {
            graphics.drawString(font, outputName, 14, height - 43, 0xFF9DA7B3.toInt(), false)
        }
    }

    /** 点击指标表中的方框可跨分段增加或移除图表维度。 */
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && mouseX >= 14.0 && mouseX <= width - 14.0 &&
            mouseY >= visibleRowsTop && mouseY < visibleRowsBottom
        ) {
            val rowIndex = ((mouseY - visibleRowsTop) / 12.0).toInt()
            visibleMetricRows.getOrNull(rowIndex)?.metric?.let { metric ->
                toggleMetric(metric)
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    /** 在指标表区域使用鼠标滚轮查看当前分段的全部行。 */
    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        scrollX: Double,
        scrollY: Double,
    ): Boolean {
        if (scrollY != 0.0 && mouseY >= visibleRowsTop && mouseY < visibleRowsBottom) {
            val step = if (scrollY > 0.0) -1 else 1
            rowOffset = (rowOffset + step).coerceIn(0, maxRowOffset)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    /** 界面关闭时仅结束实时查看，不触碰独立的 CSV 记录会话。 */
    override fun removed() {
        super.removed()
        PerformanceStatusClientController.onScreenClosed()
    }

    /** 切换分段视图，并把当前分段显示为不可重复点击的选中状态。 */
    private fun selectView(selected: View) {
        view = selected
        rowOffset = 0
        maxRowOffset = 0
        clientTab.active = selected != View.CLIENT
        serverTab.active = selected != View.SERVER
        networkTab.active = selected != View.NETWORK
    }

    /** 切换一项图表维度；超过六项时移除最早选择的维度。 */
    private fun toggleMetric(metric: PerformanceStatusChartMetric) {
        if (selectedMetrics.remove(metric)) return
        if (selectedMetrics.size >= 6) {
            selectedMetrics.remove(selectedMetrics.first())
        }
        selectedMetrics.add(metric)
    }

    /** 绘制当前分段视图的紧凑指标表。 */
    private fun renderTable(
        graphics: GuiGraphics,
        sample: PerformanceStatusSample?,
        top: Int,
        bottom: Int,
    ) {
        val rows = rows(sample)
        val rowHeight = 12
        val visibleRows = ((bottom - top) / rowHeight).coerceAtLeast(1)
        maxRowOffset = (rows.size - visibleRows).coerceAtLeast(0)
        rowOffset = rowOffset.coerceIn(0, maxRowOffset)
        visibleMetricRows = rows.drop(rowOffset).take(visibleRows)
        visibleRowsTop = top
        visibleRowsBottom = top + visibleMetricRows.size * rowHeight
        val left = 14
        val right = width - 14
        graphics.fill(left, top - 3, right, top + visibleMetricRows.size * rowHeight + 2, 0xA8171A20.toInt())
        visibleMetricRows.forEachIndexed { index, row ->
            val y = top + index * rowHeight
            if ((rowOffset + index) % 2 == 1) {
                graphics.fill(left, y - 1, right, y + rowHeight - 1, 0x45262B33)
            }
            val metricIndex = row.metric?.let(selectedMetrics::indexOf) ?: -1
            val labelX = if (row.metric == null) {
                left + 5
            } else {
                graphics.fill(left + 5, y + 2, left + 12, y + 9, 0xFF59616C.toInt())
                graphics.fill(
                    left + 6,
                    y + 3,
                    left + 11,
                    y + 8,
                    if (metricIndex >= 0) seriesColor(metricIndex) else 0xFF171A20.toInt(),
                )
                left + 16
            }
            graphics.drawString(font, row.label, labelX, y + 1, 0xFFBBC4CE.toInt(), false)
            graphics.drawString(font, row.value, right - 9 - font.width(row.value), y + 1, 0xFFF2F5F7.toInt(), false)
        }
        if (maxRowOffset > 0) {
            val trackTop = top
            val trackBottom = top + visibleRows * rowHeight
            val thumbHeight = ((trackBottom - trackTop) * visibleRows / rows.size).coerceAtLeast(8)
            val thumbTravel = trackBottom - trackTop - thumbHeight
            val thumbTop = trackTop + thumbTravel * rowOffset / maxRowOffset
            graphics.fill(right - 4, trackTop, right - 2, trackBottom, 0xFF343A43.toInt())
            graphics.fill(right - 4, thumbTop, right - 2, thumbTop + thumbHeight, 0xFF7B8794.toInt())
        }
    }

    /** 根据当前分段生成显示行，并关联可选图表维度。 */
    private fun rows(sample: PerformanceStatusSample?): List<MetricRow> {
        val client = sample?.client
        val server = sample?.server
        return when (view) {
            View.CLIENT -> listOf(
                MetricRow("FPS", format(client?.fps), PerformanceStatusChartMetric.CLIENT_FPS),
                MetricRow("Frame time", format(client?.frameTimeMs, " ms"), PerformanceStatusChartMetric.CLIENT_FRAME_TIME),
                MetricRow("Client tick interval", format(client?.tickIntervalMs, " ms"), PerformanceStatusChartMetric.CLIENT_TICK_INTERVAL),
                MetricRow("Client TPS", format(client?.clientTps), PerformanceStatusChartMetric.CLIENT_TPS),
                MetricRow("Particles", format(client?.particles), PerformanceStatusChartMetric.CLIENT_PARTICLES),
                MetricRow("CParticles", format(client?.cParticles), PerformanceStatusChartMetric.CLIENT_CPARTICLES),
                MetricRow("CParticle systems", format(client?.cParticleSystems), PerformanceStatusChartMetric.CLIENT_CPARTICLE_SYSTEMS),
                MetricRow("SoundInstances", format(client?.soundInstances), PerformanceStatusChartMetric.CLIENT_SOUND_INSTANCES),
                MetricRow("Coo sounds", format(client?.managedSoundInstances), PerformanceStatusChartMetric.CLIENT_MANAGED_SOUNDS),
                MetricRow("Sound loops", format(client?.soundLoops), PerformanceStatusChartMetric.CLIENT_SOUND_LOOPS),
                MetricRow("RenderEntities", format(client?.renderEntities), PerformanceStatusChartMetric.CLIENT_RENDER_ENTITIES),
                MetricRow("DisplayEntities", format(client?.displayEntities), PerformanceStatusChartMetric.CLIENT_DISPLAY_ENTITIES),
                MetricRow("Emitters", format(client?.emitters), PerformanceStatusChartMetric.CLIENT_EMITTERS),
                MetricRow("Compositions", format(client?.compositions), PerformanceStatusChartMetric.CLIENT_COMPOSITIONS),
                MetricRow("CooFX scenes", format(client?.cooFxScenes), PerformanceStatusChartMetric.CLIENT_COOFX_SCENES),
                MetricRow("CooFX particles", format(client?.cooFxParticles), PerformanceStatusChartMetric.CLIENT_COOFX_PARTICLES),
                MetricRow("CooFX models", format(client?.cooFxModels), PerformanceStatusChartMetric.CLIENT_COOFX_MODELS),
                MetricRow("Terrain effect groups", format(client?.terrainEffectGroups), PerformanceStatusChartMetric.CLIENT_TERRAIN_GROUPS),
                MetricRow("Terrain mappings", format(client?.terrainMappings), PerformanceStatusChartMetric.CLIENT_TERRAIN_MAPPINGS),
                MetricRow("Post effects", format(client?.postEffects), PerformanceStatusChartMetric.CLIENT_POST_EFFECTS),
                MetricRow("Heap", bytes(client?.heapUsedBytes), PerformanceStatusChartMetric.CLIENT_HEAP_USED),
            )

            View.SERVER -> listOf(
                MetricRow("TPS", format(server?.tps), PerformanceStatusChartMetric.SERVER_TPS),
                MetricRow("Target TPS", format(server?.targetTps), PerformanceStatusChartMetric.SERVER_TARGET_TPS),
                MetricRow("MSPT average", format(server?.averageMspt), PerformanceStatusChartMetric.SERVER_AVERAGE_MSPT),
                MetricRow("MSPT P95", format(server?.p95Mspt), PerformanceStatusChartMetric.SERVER_P95_MSPT),
                MetricRow("MSPT max", format(server?.maxMspt), PerformanceStatusChartMetric.SERVER_MAX_MSPT),
                MetricRow("Refresh interval", server?.refreshIntervalTicks?.let { "$it ticks" } ?: "-"),
                MetricRow("Snapshot age", format(sample?.serverSnapshotAgeMillis?.toDouble(), " ms"), PerformanceStatusChartMetric.SERVER_SNAPSHOT_AGE),
                MetricRow("Players", format(server?.onlinePlayers), PerformanceStatusChartMetric.SERVER_PLAYERS),
                MetricRow("ParticleGroups", format(server?.particleGroups), PerformanceStatusChartMetric.SERVER_PARTICLE_GROUPS),
                MetricRow("RenderEntities", format(server?.renderEntities), PerformanceStatusChartMetric.SERVER_RENDER_ENTITIES),
                MetricRow("DisplayEntities", format(server?.displayEntities), PerformanceStatusChartMetric.SERVER_DISPLAY_ENTITIES),
                MetricRow("Emitters", format(server?.emitters), PerformanceStatusChartMetric.SERVER_EMITTERS),
                MetricRow("Compositions", format(server?.compositions), PerformanceStatusChartMetric.SERVER_COMPOSITIONS),
                MetricRow("Terrain effect groups", format(server?.terrainEffectGroups), PerformanceStatusChartMetric.SERVER_TERRAIN_GROUPS),
                MetricRow("Terrain mappings", format(server?.terrainMappings), PerformanceStatusChartMetric.SERVER_TERRAIN_MAPPINGS),
                MetricRow("SoundInstances", format(server?.soundInstances), PerformanceStatusChartMetric.SERVER_SOUND_INSTANCES),
                MetricRow("Sound loops", format(server?.soundLoops), PerformanceStatusChartMetric.SERVER_SOUND_LOOPS),
                MetricRow("Barrages", format(server?.barrages), PerformanceStatusChartMetric.SERVER_BARRAGES),
                MetricRow("CooFX scenes", format(server?.cooFxScenes), PerformanceStatusChartMetric.SERVER_COOFX_SCENES),
                MetricRow("Heap", bytes(server?.heapUsedBytes), PerformanceStatusChartMetric.SERVER_HEAP_USED),
            )

            View.NETWORK -> listOf(
                MetricRow("CooPacket upload packets / tick", format(sample?.clientNetworkDelta?.sentPackets), PerformanceStatusChartMetric.CLIENT_PACKETS_SENT),
                MetricRow("CooPacket upload bytes / tick", bytes(sample?.clientNetworkDelta?.sentBytes), PerformanceStatusChartMetric.CLIENT_BYTES_SENT),
                MetricRow("CooPacket download packets / tick", format(sample?.clientNetworkDelta?.receivedPackets), PerformanceStatusChartMetric.CLIENT_PACKETS_RECEIVED),
                MetricRow("CooPacket download bytes / tick", bytes(sample?.clientNetworkDelta?.receivedBytes), PerformanceStatusChartMetric.CLIENT_BYTES_RECEIVED),
                MetricRow("Client upload total", bytes(client?.cooPackets?.sentBytes)),
                MetricRow("Client download total", bytes(client?.cooPackets?.receivedBytes)),
                MetricRow("Server upload packets / interval", format(sample?.serverNetworkDelta?.sentPackets), PerformanceStatusChartMetric.SERVER_PACKETS_SENT),
                MetricRow("Server upload bytes / interval", bytes(sample?.serverNetworkDelta?.sentBytes), PerformanceStatusChartMetric.SERVER_BYTES_SENT),
                MetricRow("Server download packets / interval", format(sample?.serverNetworkDelta?.receivedPackets), PerformanceStatusChartMetric.SERVER_PACKETS_RECEIVED),
                MetricRow("Server download bytes / interval", bytes(sample?.serverNetworkDelta?.receivedBytes), PerformanceStatusChartMetric.SERVER_BYTES_RECEIVED),
                MetricRow("Server upload total", bytes(server?.cooPackets?.sentBytes)),
                MetricRow("Server download total", bytes(server?.cooPackets?.receivedBytes)),
            )
        }
    }

    /** 绘制最近有限历史中用户选择的多维归一化折线。 */
    private fun renderChart(graphics: GuiGraphics, top: Int, bottom: Int) {
        val history = PerformanceStatusClientController.historySnapshot()
        val left = 14
        val right = width - 14
        graphics.fill(left, top, right, bottom, 0xD014171C.toInt())
        if (selectedMetrics.isEmpty()) {
            graphics.drawCenteredString(font, Component.literal("未选择曲线"), width / 2, top + 8, 0xFF7B8794.toInt())
            return
        }
        val legendBottom = renderChartLegend(graphics, history, left + 5, right - 5, top + 4)
        val plotLeft = left + 4
        val plotRight = right - 4
        val plotTop = (legendBottom + 3).coerceAtMost(bottom - 12)
        val plotBottom = bottom - 5
        repeat(3) { index ->
            val y = plotTop + (plotBottom - plotTop) * (index + 1) / 4
            graphics.hLine(plotLeft, plotRight, y, 0x443E4650)
        }
        graphics.hLine(plotLeft, plotRight, plotBottom, 0xFF59616C.toInt())
        if (history.size < 2 || plotBottom <= plotTop) return
        selectedMetrics.forEachIndexed { index, metric ->
            drawSeries(
                graphics = graphics,
                samples = history,
                metric = metric,
                left = plotLeft,
                right = plotRight,
                top = plotTop,
                bottom = plotBottom,
                maximum = seriesMaximum(history, metric),
                color = seriesColor(index),
            )
        }
    }

    /** 绘制颜色图例；数值按“当前 / 本窗口量程上界”显示。 */
    private fun renderChartLegend(
        graphics: GuiGraphics,
        samples: List<PerformanceStatusSample>,
        left: Int,
        right: Int,
        top: Int,
    ): Int {
        var x = left
        var y = top
        selectedMetrics.forEachIndexed { index, metric ->
            val current = latestMetricValue(samples, metric)
            val maximum = seriesMaximum(samples, metric)
            val text = "${metric.label} ${formatChartValue(metric, current)} / ${formatChartValue(metric, maximum)}"
            val itemWidth = 8 + font.width(text) + 9
            if (x > left && x + itemWidth > right) {
                x = left
                y += 11
            }
            graphics.fill(x, y + 2, x + 7, y + 8, seriesColor(index))
            graphics.drawString(font, text, x + 10, y, 0xFFE3E8ED.toInt(), false)
            x += itemWidth
        }
        return y + 9
    }

    /** 返回图表窗口内可见值与指标基础量程中的较大值。 */
    private fun seriesMaximum(
        samples: List<PerformanceStatusSample>,
        metric: PerformanceStatusChartMetric,
    ): Double {
        var maximum = metric.minimumMaximum
        samples.forEach { sample ->
            val value = metric.extract(sample)
            if (value != null && value.isFinite() && value >= 0.0 && value > maximum) maximum = value
        }
        return maximum
    }

    /** 返回一项指标最后一个有效样本。 */
    private fun latestMetricValue(
        samples: List<PerformanceStatusSample>,
        metric: PerformanceStatusChartMetric,
    ): Double? {
        for (index in samples.lastIndex downTo 0) {
            metric.extract(samples[index])?.let { value ->
                if (value.isFinite() && value >= 0.0) return value
            }
        }
        return null
    }

    /** 在 GuiGraphics 同一批次中用旋转细矩形连接相邻采样点。 */
    private fun drawSeries(
        graphics: GuiGraphics,
        samples: List<PerformanceStatusSample>,
        metric: PerformanceStatusChartMetric,
        left: Int,
        right: Int,
        top: Int,
        bottom: Int,
        maximum: Double,
        color: Int,
    ) {
        val denominator = (samples.size - 1).coerceAtLeast(1)
        var previousX: Int? = null
        var previousY: Int? = null
        samples.forEachIndexed { index, sample ->
            val value = metric.extract(sample) ?: return@forEachIndexed
            if (!value.isFinite() || value < 0.0) return@forEachIndexed
            val x = left + index * (right - left) / denominator
            if (x == previousX && index != samples.lastIndex) return@forEachIndexed
            val normalized = (value / maximum).coerceIn(0.0, 1.0)
            val y = bottom - (normalized * (bottom - top)).roundToInt()
            val startX = previousX
            val startY = previousY
            if (startX != null && startY != null) {
                drawLineSegment(graphics, startX, startY, x, y, color)
            }
            previousX = x
            previousY = y
        }
    }

    /** 绘制一个两像素粗的任意角度 GUI 线段。 */
    private fun drawLineSegment(
        graphics: GuiGraphics,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        color: Int,
    ) {
        val deltaX = endX - startX
        val deltaY = endY - startY
        val length = hypot(deltaX.toDouble(), deltaY.toDouble()).roundToInt().coerceAtLeast(1)
        val angle = (atan2(deltaY.toDouble(), deltaX.toDouble()) * 180.0 / PI).toFloat()
        graphics.pose().pushPose()
        graphics.pose().translate(startX.toFloat(), startY.toFloat(), 0F)
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(angle))
        graphics.fill(0, -1, length + 1, 1, color)
        graphics.pose().popPose()
    }

    /** 返回选择顺序对应的高对比度曲线颜色。 */
    private fun seriesColor(index: Int): Int {
        return when (index % 6) {
            0 -> 0xFF55D6A9.toInt()
            1 -> 0xFFFFC857.toInt()
            2 -> 0xFF57A7FF.toInt()
            3 -> 0xFFE980C0.toInt()
            4 -> 0xFFFF6B6B.toInt()
            else -> 0xFF8DE1FF.toInt()
        }
    }

    /** 按指标单位格式化图例值。 */
    private fun formatChartValue(metric: PerformanceStatusChartMetric, value: Double?): String {
        value ?: return "-"
        return when (metric.valueKind) {
            PerformanceStatusChartValueKind.NUMBER -> {
                if (value == value.toLong().toDouble()) value.toLong().toString() else format(value)
            }
            PerformanceStatusChartValueKind.MILLISECONDS -> format(value, " ms")
            PerformanceStatusChartValueKind.BYTES -> bytes(value.toLong())
        }
    }

    /** 格式化整数或长整数指标。 */
    private fun format(value: Number?): String {
        return value?.toLong()?.toString() ?: "-"
    }

    /** 格式化小数指标并追加单位。 */
    private fun format(value: Double?, suffix: String = ""): String {
        return value?.let { number -> String.format(Locale.ROOT, "%.2f%s", number, suffix) } ?: "-"
    }

    /** 把字节数格式化为紧凑 IEC 单位。 */
    private fun bytes(value: Long?): String {
        value ?: return "-"
        if (value < 1_024L) return "$value B"
        val kibibytes = value / 1_024.0
        if (kibibytes < 1_024.0) return String.format(Locale.ROOT, "%.1f KiB", kibibytes)
        val mebibytes = kibibytes / 1_024.0
        if (mebibytes < 1_024.0) return String.format(Locale.ROOT, "%.1f MiB", mebibytes)
        return String.format(Locale.ROOT, "%.2f GiB", mebibytes / 1_024.0)
    }

    /** 一行当前值及其可选图表维度。 */
    private data class MetricRow(
        val label: String,
        val value: String,
        val metric: PerformanceStatusChartMetric? = null,
    )

    /** Status 表格分段。 */
    private enum class View {
        CLIENT,
        SERVER,
        NETWORK
    }
}
