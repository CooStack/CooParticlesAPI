package cn.coostack.cooparticlesapi.performance.client

import com.mojang.math.Axis
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
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

    /** 相关性分析视图分段按钮。 */
    private lateinit var correlationTab: Button

    /** 底部用于修改趋势历史最大保留时长的输入框。 */
    private lateinit var historyDurationBox: EditBox

    /** 当前表格首行在完整指标列表中的偏移。 */
    private var rowOffset = 0

    /** 当前视图允许的最大滚动偏移。 */
    private var maxRowOffset = 0

    /** 当前图表选择的原始指标和比值曲线。 */
    private val selectedMetrics = PerformanceStatusClientController.selectedChartSelections()
        .toCollection(linkedSetOf())

    /** 可作为比值除数的性能指标。 */
    private val ratioPerformanceMetrics = PerformanceStatusChartMetric.entries.filter {
        it.role == PerformanceStatusChartMetricRole.PERFORMANCE
    }

    /** 可作为比值被除数的影响指标。 */
    private val ratioImpactMetrics = PerformanceStatusChartMetric.entries.filter {
        it.role == PerformanceStatusChartMetricRole.IMPACT
    }

    /** 当前比值配置中的性能指标，作为负载比值的除数；输入不精确匹配时为 null。 */
    private var ratioPerformanceMetric: PerformanceStatusChartMetric? = PerformanceStatusChartMetric.CLIENT_FPS

    /** 当前比值配置中的影响指标，作为负载比值的被除数；输入不精确匹配时为 null。 */
    private var ratioImpactMetric: PerformanceStatusChartMetric? = PerformanceStatusChartMetric.CLIENT_PARTICLES

    /** 相关性页中支持输入补全的性能项输入框。 */
    private lateinit var ratioPerformanceSelector: EditBox

    /** 相关性页中支持输入补全的影响项输入框。 */
    private lateinit var ratioImpactSelector: EditBox

    /** 当前性能项输入候选。 */
    private var ratioPerformanceSuggestions: List<PerformanceStatusChartMetric> = emptyList()

    /** 当前影响项输入候选。 */
    private var ratioImpactSuggestions: List<PerformanceStatusChartMetric> = emptyList()

    /** 当前性能项输入候选中的键盘选择位置。 */
    private var ratioPerformanceSuggestionIndex = 0

    /** 当前影响项输入候选中的键盘选择位置。 */
    private var ratioImpactSuggestionIndex = 0

    /** 把当前影响项除以性能项加入图表的按钮。 */
    private lateinit var addRatioButton: Button

    /** 当前主图时间窗口在完整历史中的归一化起点。 */
    private var rangeStart = 0.0

    /** 当前主图时间窗口在完整历史中的归一化终点。 */
    private var rangeEnd = 1.0

    /** 当前正在拖动的时间范围区域。 */
    private var rangeDragMode = RangeDragMode.NONE

    /** 开始拖动时鼠标在时间轴上的归一化位置。 */
    private var rangeDragAnchor = 0.0

    /** 开始拖动时的范围起点。 */
    private var rangeDragStart = 0.0

    /** 开始拖动时的范围终点。 */
    private var rangeDragEnd = 1.0

    /** 最近一帧时间轴左边界。 */
    private var timelineLeft = 0

    /** 最近一帧时间轴右边界。 */
    private var timelineRight = 0

    /** 最近一帧时间轴上边界。 */
    private var timelineTop = 0

    /** 最近一帧时间轴下边界。 */
    private var timelineBottom = 0

    /** 最近一帧实际显示的可点击指标行。 */
    private var visibleMetricRows: List<MetricRow> = emptyList()

    /** 最近一帧指标表的可点击上边界。 */
    private var visibleRowsTop = 0

    /** 最近一帧指标表的可点击下边界。 */
    private var visibleRowsBottom = 0

    /** 创建视图分段按钮、结束按钮和关闭按钮。 */
    override fun init() {
        val tabWidth = ((width - 48) / 4).coerceIn(64, 110)
        val tabsLeft = (width - tabWidth * 4) / 2
        clientTab = Button.builder(Component.literal("客户端")) {
            selectView(View.CLIENT)
        }.bounds(tabsLeft, 28, tabWidth, 20).build()
        serverTab = Button.builder(Component.literal("服务器")) {
            selectView(View.SERVER)
        }.bounds(tabsLeft + tabWidth, 28, tabWidth, 20).build()
        networkTab = Button.builder(Component.literal("网络")) {
            selectView(View.NETWORK)
        }.bounds(tabsLeft + tabWidth * 2, 28, tabWidth, 20).build()
        correlationTab = Button.builder(Component.literal("相关性")) {
            selectView(View.CORRELATION)
        }.bounds(tabsLeft + tabWidth * 3, 28, tabWidth, 20).build()
        addRenderableWidget(clientTab)
        addRenderableWidget(serverTab)
        addRenderableWidget(networkTab)
        addRenderableWidget(correlationTab)
        val ratioY = 52
        val ratioWidth = ((width - 44) / 3).coerceIn(72, 180)
        ratioPerformanceSelector = EditBox(font, 14, ratioY, ratioWidth, 20, Component.literal("性能项"))
        ratioPerformanceSelector.setMaxLength(64)
        ratioPerformanceSelector.setValue(ratioPerformanceMetric?.label.orEmpty())
        ratioPerformanceSelector.setResponder { updateRatioSuggestions(ratioPerformanceSelector) }
        ratioImpactSelector = EditBox(
            font,
            18 + ratioWidth,
            ratioY,
            ratioWidth,
            20,
            Component.literal("影响项"),
        )
        ratioImpactSelector.setMaxLength(64)
        ratioImpactSelector.setValue(ratioImpactMetric?.label.orEmpty())
        ratioImpactSelector.setResponder { updateRatioSuggestions(ratioImpactSelector) }
        addRatioButton = Button.builder(Component.literal("添加比值")) {
            addRatioSelection()
        }.bounds(22 + ratioWidth * 2, ratioY, ratioWidth, 20).build()
        addRenderableWidget(ratioPerformanceSelector)
        addRenderableWidget(ratioImpactSelector)
        addRenderableWidget(addRatioButton)
        updateRatioSuggestions(ratioPerformanceSelector)
        updateRatioSuggestions(ratioImpactSelector)
        val buttonY = (height - 28).coerceAtLeast(52)
        historyDurationBox = EditBox(font, 48, buttonY, 64, 20, Component.literal("最大查看时间"))
        historyDurationBox.setMaxLength(19)
        historyDurationBox.setValue(PerformanceStatusClientController.historyDurationSeconds().toString())
        historyDurationBox.setFilter { value ->
            value.isEmpty() || value.all { character -> character in '0'..'9' }
        }
        historyDurationBox.setResponder { value ->
            value.toLongOrNull()?.let { seconds ->
                PerformanceStatusClientController.updateHistoryDurationSeconds(seconds)
            }
        }
        addRenderableWidget(historyDurationBox)
        val actionWidth = ((width - 24) / 2).coerceIn(80, 120)
        addRenderableWidget(Button.builder(Component.literal("关闭")) {
            onClose()
        }.bounds(width - actionWidth - 14, buttonY, actionWidth, 20).build())
        selectView(view)
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
        graphics.drawString(font, "历史秒", 14, height - 22, 0xFF9DA7B3.toInt(), false)
        val sample = PerformanceStatusClientController.latestSample()
        val tableTop = if (view == View.CORRELATION) 78 else 56
        val chartHeight = (height / 3).coerceIn(96, 180)
        val chartBottom = (height - 50).coerceAtLeast(tableTop + chartHeight)
        val chartTop = chartBottom - chartHeight
        renderTable(graphics, sample, tableTop, chartTop - 6)
        renderChart(graphics, chartTop, chartBottom)
        renderRatioSuggestions(graphics, mouseX, mouseY)
        val outputName = if (PerformanceStatusClientController.isRecording()) {
            PerformanceStatusClientController.outputPath()?.fileName?.toString().orEmpty()
        } else {
            ""
        }
        if (outputName.isNotEmpty()) {
            graphics.drawString(font, outputName, 14, height - 43, 0xFF9DA7B3.toInt(), false)
        }
    }

    /** 处理输入补全候选的键盘选择。 */
    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val focused = focusedRatioSelector()
        if (view == View.CORRELATION && focused != null && ratioSuggestions(focused).isNotEmpty()) {
            when (keyCode) {
                GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    return acceptRatioSuggestion(focused)
                }
                GLFW.GLFW_KEY_DOWN -> {
                    moveRatioSuggestion(focused, 1)
                    return true
                }
                GLFW.GLFW_KEY_UP -> {
                    moveRatioSuggestion(focused, -1)
                    return true
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    /** 点击补全候选后写回规范化的指标名称。 */
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            ratioSuggestionAt(mouseX.toInt(), mouseY.toInt())?.let { (box, index) ->
                val metric = ratioSuggestions(box).getOrNull(index) ?: return true
                acceptRatioMetric(box, metric)
                return true
            }
        }
        if (button == 0 && beginRangeDrag(mouseX, mouseY)) return true
        if (button == 0 && mouseX >= 14.0 && mouseX <= width - 14.0 &&
            mouseY >= visibleRowsTop && mouseY < visibleRowsBottom
        ) {
            val rowIndex = ((mouseY - visibleRowsTop) / 12.0).toInt()
            val row = visibleMetricRows.getOrNull(rowIndex)
            row?.selection?.let { selection ->
                toggleSelection(selection)
                return true
            }
            row?.metric?.let { metric ->
                toggleMetric(metric)
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    /** 拖动左右把手缩放时间范围，拖动中间选区平移范围。 */
    override fun mouseDragged(
        mouseX: Double,
        mouseY: Double,
        button: Int,
        dragX: Double,
        dragY: Double,
    ): Boolean {
        if (button == 0 && rangeDragMode != RangeDragMode.NONE) {
            updateRangeDrag(mouseX)
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    /** 释放鼠标后结束当前时间范围拖动。 */
    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && rangeDragMode != RangeDragMode.NONE) {
            rangeDragMode = RangeDragMode.NONE
            return true
        }
        return super.mouseReleased(mouseX, mouseY, button)
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

    /** 界面关闭时保存曲线选择，并仅结束实时查看。 */
    override fun removed() {
        PerformanceStatusClientController.updateSelectedChartSelections(selectedMetrics)
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
        correlationTab.active = selected != View.CORRELATION
        val correlationVisible = selected == View.CORRELATION
        ratioPerformanceSelector.visible = correlationVisible
        ratioPerformanceSelector.active = correlationVisible
        ratioImpactSelector.visible = correlationVisible
        ratioImpactSelector.active = correlationVisible
        addRatioButton.visible = correlationVisible
        addRatioButton.active = correlationVisible && ratioPerformanceMetric != null && ratioImpactMetric != null
    }

    /** 切换任意原始指标或相关性曲线。 */
    private fun toggleSelection(selection: PerformanceStatusChartSelection) {
        if (!selectedMetrics.remove(selection)) {
            if (selectedMetrics.size >= PERFORMANCE_STATUS_MAX_SELECTED_SERIES) {
                selectedMetrics.remove(selectedMetrics.first())
            }
            selectedMetrics.add(selection)
        }
        PerformanceStatusClientController.updateSelectedChartSelections(selectedMetrics)
    }

    /** 切换一项原始图表维度。 */
    private fun toggleMetric(metric: PerformanceStatusChartMetric) {
        toggleSelection(PerformanceStatusChartSelection.Metric(metric))
    }

    /** 把输入框文本解析为对应角色的指标。 */
    private fun resolveRatioMetric(
        box: EditBox,
        metrics: List<PerformanceStatusChartMetric>,
    ): PerformanceStatusChartMetric? {
        val input = box.value.trim()
        if (input.isEmpty()) return null
        return metrics.firstOrNull { metric ->
            metric.label.equals(input, ignoreCase = true) || metric.name.equals(input, ignoreCase = true)
        }
    }

    /** 根据输入文本更新候选、灰色补全提示和当前有效指标。 */
    private fun updateRatioSuggestions(box: EditBox) {
        val metrics = if (box === ratioPerformanceSelector) ratioPerformanceMetrics else ratioImpactMetrics
        val input = box.value.trim()
        val suggestions = metrics.filter { metric ->
            input.isEmpty() || metric.label.contains(input, ignoreCase = true) ||
                metric.name.contains(input, ignoreCase = true)
        }
        if (box === ratioPerformanceSelector) {
            ratioPerformanceSuggestions = suggestions
            ratioPerformanceMetric = resolveRatioMetric(box, ratioPerformanceMetrics)
            ratioPerformanceSuggestionIndex = 0
        } else if (box === ratioImpactSelector) {
            ratioImpactSuggestions = suggestions
            ratioImpactMetric = resolveRatioMetric(box, ratioImpactMetrics)
            ratioImpactSuggestionIndex = 0
        }
        val first = suggestions.firstOrNull()
        box.setSuggestion(
            if (input.isNotEmpty() && first?.label?.startsWith(input, ignoreCase = true) == true) {
                first.label.drop(input.length)
            } else {
                null
            },
        )
        if (::addRatioButton.isInitialized) {
            addRatioButton.active = view == View.CORRELATION &&
                ratioPerformanceMetric != null && ratioImpactMetric != null
        }
    }

    /** 返回当前获得焦点的相关性输入框。 */
    private fun focusedRatioSelector(): EditBox? {
        return when {
            ratioPerformanceSelector.isFocused && ratioPerformanceSelector.active -> ratioPerformanceSelector
            ratioImpactSelector.isFocused && ratioImpactSelector.active -> ratioImpactSelector
            else -> null
        }
    }

    /** 返回指定输入框当前的候选列表。 */
    private fun ratioSuggestions(box: EditBox): List<PerformanceStatusChartMetric> {
        return if (box === ratioPerformanceSelector) ratioPerformanceSuggestions else ratioImpactSuggestions
    }

    /** 返回指定输入框当前的候选键盘位置。 */
    private fun ratioSuggestionIndex(box: EditBox): Int {
        return if (box === ratioPerformanceSelector) ratioPerformanceSuggestionIndex else ratioImpactSuggestionIndex
    }

    /** 更新指定输入框当前的候选键盘位置。 */
    private fun setRatioSuggestionIndex(box: EditBox, index: Int) {
        if (box === ratioPerformanceSelector) {
            ratioPerformanceSuggestionIndex = index
        } else {
            ratioImpactSuggestionIndex = index
        }
    }

    /** 接受当前键盘选中的相关性候选。 */
    private fun acceptRatioSuggestion(box: EditBox): Boolean {
        val suggestions = ratioSuggestions(box)
        val metric = suggestions.getOrNull(ratioSuggestionIndex(box)) ?: return false
        acceptRatioMetric(box, metric)
        return true
    }

    /** 将候选写回输入框并重新计算另一侧按钮状态。 */
    private fun acceptRatioMetric(box: EditBox, metric: PerformanceStatusChartMetric) {
        box.value = metric.label
        updateRatioSuggestions(box)
    }

    /** 移动相关性候选的键盘选择位置。 */
    private fun moveRatioSuggestion(box: EditBox, delta: Int) {
        val suggestions = ratioSuggestions(box)
        if (suggestions.isEmpty()) return
        val next = (ratioSuggestionIndex(box) + delta).coerceIn(0, suggestions.lastIndex)
        setRatioSuggestionIndex(box, next)
    }

    /** 绘制当前焦点输入框的候选列表。 */
    private fun renderRatioSuggestions(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (view != View.CORRELATION) return
        val box = focusedRatioSelector() ?: return
        val suggestions = ratioSuggestions(box)
        if (suggestions.isEmpty()) return
        val maxVisibleSuggestions = 5
        val x = box.x
        var y = box.y + box.height + 2
        val end = minOf(suggestions.size, maxVisibleSuggestions)
        for (index in 0 until end) {
            val metric = suggestions[index]
            val hovered = mouseX in x..(x + box.width) && mouseY in y..(y + 13)
            val background = when {
                hovered -> 0xCC224422.toInt()
                index == ratioSuggestionIndex(box) -> 0xCC1A331A.toInt()
                else -> 0xCC000000.toInt()
            }
            graphics.fill(x, y, x + box.width, y + 13, background)
            graphics.drawString(font, metric.label, x + 3, y + 3, 0xE0FFE0, true)
            y += 13
        }
    }

    /** 返回鼠标命中的相关性候选。 */
    private fun ratioSuggestionAt(mouseX: Int, mouseY: Int): Pair<EditBox, Int>? {
        if (view != View.CORRELATION) return null
        val box = focusedRatioSelector() ?: return null
        val suggestions = ratioSuggestions(box)
        val maxVisibleSuggestions = 5
        var y = box.y + box.height + 2
        val end = minOf(suggestions.size, maxVisibleSuggestions)
        for (index in 0 until end) {
            if (mouseX in box.x..(box.x + box.width) && mouseY in y..(y + 13)) {
                return box to index
            }
            y += 13
        }
        return null
    }

    /** 把影响项除以性能项加入曲线选择。 */
    private fun addRatioSelection() {
        val impact = ratioImpactMetric ?: return
        val performance = ratioPerformanceMetric ?: return
        val selection = PerformanceStatusChartSelection.Ratio(impact = impact, performance = performance)
        selectedMetrics.remove(selection)
        if (selectedMetrics.size >= PERFORMANCE_STATUS_MAX_SELECTED_SERIES) {
            selectedMetrics.remove(selectedMetrics.first())
        }
        selectedMetrics.add(selection)
        PerformanceStatusClientController.updateSelectedChartSelections(selectedMetrics)
    }

    /** 在最近一帧时间轴中开始拖动把手或中间选区。 */
    private fun beginRangeDrag(mouseX: Double, mouseY: Double): Boolean {
        if (timelineRight <= timelineLeft || mouseY < timelineTop - 3 || mouseY > timelineBottom + 3) return false
        val startX = timelineX(rangeStart)
        val endX = timelineX(rangeEnd)
        rangeDragMode = when {
            kotlin.math.abs(mouseX - startX) <= 5.0 -> RangeDragMode.START
            kotlin.math.abs(mouseX - endX) <= 5.0 -> RangeDragMode.END
            mouseX in startX.toDouble()..endX.toDouble() -> RangeDragMode.WINDOW
            else -> RangeDragMode.NONE
        }
        if (rangeDragMode == RangeDragMode.NONE) return false
        rangeDragAnchor = timelineRatio(mouseX)
        rangeDragStart = rangeStart
        rangeDragEnd = rangeEnd
        return true
    }

    /** 根据鼠标位置更新当前时间范围，最小范围固定为一秒。 */
    private fun updateRangeDrag(mouseX: Double) {
        val ratio = timelineRatio(mouseX)
        val history = PerformanceStatusClientController.historySnapshot()
        val minimumSpan = minimumRangeSpan(history)
        when (rangeDragMode) {
            RangeDragMode.START -> rangeStart = ratio.coerceIn(0.0, rangeEnd - minimumSpan)
            RangeDragMode.END -> rangeEnd = ratio.coerceIn(rangeStart + minimumSpan, 1.0)
            RangeDragMode.WINDOW -> {
                val span = rangeDragEnd - rangeDragStart
                val desiredStart = rangeDragStart + ratio - rangeDragAnchor
                rangeStart = desiredStart.coerceIn(0.0, 1.0 - span)
                rangeEnd = rangeStart + span
            }
            RangeDragMode.NONE -> Unit
        }
    }

    /** 把一秒换算为当前完整历史中的最小归一化时间跨度。 */
    private fun minimumRangeSpan(history: List<PerformanceStatusSample>): Double {
        if (history.size < 2) return 1.0
        val durationMillis = history.last().elapsedMillis - history.first().elapsedMillis
        if (durationMillis < 1_000L) return 1.0
        return (1_000.0 / durationMillis.toDouble()).coerceIn(0.0, 1.0)
    }

    /** 把鼠标时间轴位置转换为零到一的归一化位置。 */
    private fun timelineRatio(mouseX: Double): Double {
        val width = (timelineRight - timelineLeft).coerceAtLeast(1)
        return ((mouseX - timelineLeft) / width).coerceIn(0.0, 1.0)
    }

    /** 把归一化时间位置转换为时间轴像素位置。 */
    private fun timelineX(ratio: Double): Int {
        return timelineLeft + ((timelineRight - timelineLeft) * ratio).roundToInt()
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
            val selection = row.selection ?: row.metric?.let(PerformanceStatusChartSelection::Metric)
            val metricIndex = selection?.let(selectedMetrics::indexOf) ?: -1
            val labelX = if (selection == null) {
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
                MetricRow("帧耗时", format(client?.frameTimeMs, " ms"), PerformanceStatusChartMetric.CLIENT_FRAME_TIME),
                MetricRow("客户端 tick 间隔", format(client?.tickIntervalMs, " ms"), PerformanceStatusChartMetric.CLIENT_TICK_INTERVAL),
                MetricRow("客户端 TPS", format(client?.clientTps), PerformanceStatusChartMetric.CLIENT_TPS),
                MetricRow("Particles", format(client?.particles), PerformanceStatusChartMetric.CLIENT_PARTICLES),
                MetricRow("CParticles", format(client?.cParticles), PerformanceStatusChartMetric.CLIENT_CPARTICLES),
                MetricRow("CParticle 系统", format(client?.cParticleSystems), PerformanceStatusChartMetric.CLIENT_CPARTICLE_SYSTEMS),
                MetricRow("SoundInstances", format(client?.soundInstances), PerformanceStatusChartMetric.CLIENT_SOUND_INSTANCES),
                MetricRow("Coo 声音", format(client?.managedSoundInstances), PerformanceStatusChartMetric.CLIENT_MANAGED_SOUNDS),
                MetricRow("声音循环", format(client?.soundLoops), PerformanceStatusChartMetric.CLIENT_SOUND_LOOPS),
                MetricRow("客户端 RenderEntities", format(client?.renderEntities), PerformanceStatusChartMetric.CLIENT_RENDER_ENTITIES),
                MetricRow("客户端 DisplayEntities", format(client?.displayEntities), PerformanceStatusChartMetric.CLIENT_DISPLAY_ENTITIES),
                MetricRow("客户端 Emitters", format(client?.emitters), PerformanceStatusChartMetric.CLIENT_EMITTERS),
                MetricRow("客户端 Compositions", format(client?.compositions), PerformanceStatusChartMetric.CLIENT_COMPOSITIONS),
                MetricRow("CooFX 场景", format(client?.cooFxScenes), PerformanceStatusChartMetric.CLIENT_COOFX_SCENES),
                MetricRow("CooFX 粒子", format(client?.cooFxParticles), PerformanceStatusChartMetric.CLIENT_COOFX_PARTICLES),
                MetricRow("CooFX 模型", format(client?.cooFxModels), PerformanceStatusChartMetric.CLIENT_COOFX_MODELS),
                MetricRow("地形效果组", format(client?.terrainEffectGroups), PerformanceStatusChartMetric.CLIENT_TERRAIN_GROUPS),
                MetricRow("地形映射", format(client?.terrainMappings), PerformanceStatusChartMetric.CLIENT_TERRAIN_MAPPINGS),
                MetricRow("后处理", format(client?.postEffects), PerformanceStatusChartMetric.CLIENT_POST_EFFECTS),
                MetricRow("客户端 GC 次数", format(client?.gcCollectionCount), PerformanceStatusChartMetric.CLIENT_GC_COUNT),
                MetricRow("客户端 GC 耗时", format(client?.gcCollectionTimeMs?.toDouble(), " ms"), PerformanceStatusChartMetric.CLIENT_GC_TIME),
                MetricRow("堆内存已用", bytes(client?.heapUsedBytes), PerformanceStatusChartMetric.CLIENT_HEAP_USED),
                MetricRow("堆内存上限", bytes(client?.heapMaxBytes)),
            )

            View.SERVER -> listOf(
                MetricRow("TPS", format(server?.tps), PerformanceStatusChartMetric.SERVER_TPS),
                MetricRow("目标 TPS", format(server?.targetTps), PerformanceStatusChartMetric.SERVER_TARGET_TPS),
                MetricRow("MSPT 平均", format(server?.averageMspt), PerformanceStatusChartMetric.SERVER_AVERAGE_MSPT),
                MetricRow("MSPT P95", format(server?.p95Mspt), PerformanceStatusChartMetric.SERVER_P95_MSPT),
                MetricRow("MSPT 最大", format(server?.maxMspt), PerformanceStatusChartMetric.SERVER_MAX_MSPT),
                MetricRow("刷新间隔", server?.refreshIntervalTicks?.let { "$it tick" } ?: "-"),
                MetricRow("快照延迟", format(sample?.serverSnapshotAgeMillis?.toDouble(), " ms"), PerformanceStatusChartMetric.SERVER_SNAPSHOT_AGE),
                MetricRow("在线玩家", format(server?.onlinePlayers), PerformanceStatusChartMetric.SERVER_PLAYERS),
                MetricRow("ParticleGroups", format(server?.particleGroups), PerformanceStatusChartMetric.SERVER_PARTICLE_GROUPS),
                MetricRow("服务端 RenderEntities", format(server?.renderEntities), PerformanceStatusChartMetric.SERVER_RENDER_ENTITIES),
                MetricRow("服务端 DisplayEntities", format(server?.displayEntities), PerformanceStatusChartMetric.SERVER_DISPLAY_ENTITIES),
                MetricRow("服务端 Emitters", format(server?.emitters), PerformanceStatusChartMetric.SERVER_EMITTERS),
                MetricRow("服务端 Compositions", format(server?.compositions), PerformanceStatusChartMetric.SERVER_COMPOSITIONS),
                MetricRow("服务端地形效果组", format(server?.terrainEffectGroups), PerformanceStatusChartMetric.SERVER_TERRAIN_GROUPS),
                MetricRow("服务端地形映射", format(server?.terrainMappings), PerformanceStatusChartMetric.SERVER_TERRAIN_MAPPINGS),
                MetricRow("服务端声音", format(server?.soundInstances), PerformanceStatusChartMetric.SERVER_SOUND_INSTANCES),
                MetricRow("服务端声音循环", format(server?.soundLoops), PerformanceStatusChartMetric.SERVER_SOUND_LOOPS),
                MetricRow("Barrages", format(server?.barrages), PerformanceStatusChartMetric.SERVER_BARRAGES),
                MetricRow("服务端 CooFX 场景", format(server?.cooFxScenes), PerformanceStatusChartMetric.SERVER_COOFX_SCENES),
                MetricRow("服务端 GC 次数", format(server?.gcCollectionCount), PerformanceStatusChartMetric.SERVER_GC_COUNT),
                MetricRow("服务端 GC 耗时", format(server?.gcCollectionTimeMs?.toDouble(), " ms"), PerformanceStatusChartMetric.SERVER_GC_TIME),
                MetricRow("服务端堆内存已用", bytes(server?.heapUsedBytes), PerformanceStatusChartMetric.SERVER_HEAP_USED),
                MetricRow("服务端堆内存上限", bytes(server?.heapMaxBytes)),
            )

            View.NETWORK -> listOf(
                MetricRow("CooPacket 上传包 / tick", format(sample?.clientNetworkDelta?.sentPackets), PerformanceStatusChartMetric.CLIENT_PACKETS_SENT),
                MetricRow("CooPacket 上传字节 / tick", bytes(sample?.clientNetworkDelta?.sentBytes), PerformanceStatusChartMetric.CLIENT_BYTES_SENT),
                MetricRow("CooPacket 下载包 / tick", format(sample?.clientNetworkDelta?.receivedPackets), PerformanceStatusChartMetric.CLIENT_PACKETS_RECEIVED),
                MetricRow("CooPacket 下载字节 / tick", bytes(sample?.clientNetworkDelta?.receivedBytes), PerformanceStatusChartMetric.CLIENT_BYTES_RECEIVED),
                MetricRow("原版上传包 / ${sample?.vanillaPacketAggregationTicks ?: PerformanceStatusClientController.vanillaPacketAggregationTicks()} tick", format(sample?.clientVanillaPacketDelta?.sentPackets), PerformanceStatusChartMetric.CLIENT_VANILLA_PACKETS_SENT),
                MetricRow("原版下载包 / ${sample?.vanillaPacketAggregationTicks ?: PerformanceStatusClientController.vanillaPacketAggregationTicks()} tick", format(sample?.clientVanillaPacketDelta?.receivedPackets), PerformanceStatusChartMetric.CLIENT_VANILLA_PACKETS_RECEIVED),
                MetricRow("原版上传包总计", format(client?.vanillaPackets?.sentPackets)),
                MetricRow("原版下载包总计", format(client?.vanillaPackets?.receivedPackets)),
                MetricRow("客户端 CooPacket 上传总计", bytes(client?.cooPackets?.sentBytes)),
                MetricRow("客户端 CooPacket 下载总计", bytes(client?.cooPackets?.receivedBytes)),
                MetricRow("服务端 CooPacket 上传包 / 快照", format(sample?.serverNetworkDelta?.sentPackets), PerformanceStatusChartMetric.SERVER_PACKETS_SENT),
                MetricRow("服务端 CooPacket 上传字节 / 快照", bytes(sample?.serverNetworkDelta?.sentBytes), PerformanceStatusChartMetric.SERVER_BYTES_SENT),
                MetricRow("服务端 CooPacket 下载包 / 快照", format(sample?.serverNetworkDelta?.receivedPackets), PerformanceStatusChartMetric.SERVER_PACKETS_RECEIVED),
                MetricRow("服务端 CooPacket 下载字节 / 快照", bytes(sample?.serverNetworkDelta?.receivedBytes), PerformanceStatusChartMetric.SERVER_BYTES_RECEIVED),
                MetricRow("服务端原版上传包 / 快照", format(sample?.serverVanillaPacketDelta?.sentPackets), PerformanceStatusChartMetric.SERVER_VANILLA_PACKETS_SENT),
                MetricRow("服务端原版下载包 / 快照", format(sample?.serverVanillaPacketDelta?.receivedPackets), PerformanceStatusChartMetric.SERVER_VANILLA_PACKETS_RECEIVED),
                MetricRow("服务端原版上传包总计", format(server?.vanillaPackets?.sentPackets)),
                MetricRow("服务端原版下载包总计", format(server?.vanillaPackets?.receivedPackets)),
            )

            View.CORRELATION -> {
                val ratios = selectedMetrics.filterIsInstance<PerformanceStatusChartSelection.Ratio>()
                if (ratios.isEmpty()) {
                    listOf(MetricRow("尚未添加比值", "-"))
                } else {
                    ratios.map { ratio ->
                        val current = sample?.let(ratio::extract)
                        MetricRow(
                            label = ratio.label,
                            value = current?.let(::formatRatio) ?: "-",
                            selection = ratio,
                        )
                    }
                }
            }
        }
    }

    /** 绘制最近有限历史中用户选择的多维归一化折线和时间范围条。 */
    private fun renderChart(graphics: GuiGraphics, top: Int, bottom: Int) {
        val history = PerformanceStatusClientController.historySnapshot()
        timelineLeft = 18
        timelineRight = width - 18
        timelineTop = bottom - 17
        timelineBottom = bottom - 3
        val visibleHistory = visibleHistory(history)
        val left = 14
        val right = width - 14
        val chartPointLimit = 128
        val chartHistory = samplePerformanceStatusChartPoints(
            visibleHistory,
            chartPointLimit,
        )
        graphics.fill(left, top, right, bottom, 0xD014171C.toInt())
        if (selectedMetrics.isEmpty()) {
            graphics.drawCenteredString(font, Component.literal("未选择曲线"), width / 2, top + 8, 0xFF7B8794.toInt())
            renderTimeline(graphics, history)
            return
        }
        val legendBottom = renderChartLegend(graphics, chartHistory, left + 5, right - 5, top + 4)
        val plotLeft = left + 4
        val plotRight = right - 4
        val plotTop = (legendBottom + 3).coerceAtMost(timelineTop - 8)
        val plotBottom = timelineTop - 5
        repeat(3) { index ->
            val y = plotTop + (plotBottom - plotTop) * (index + 1) / 4
            graphics.hLine(plotLeft, plotRight, y, 0x443E4650)
        }
        graphics.hLine(plotLeft, plotRight, plotBottom, 0xFF59616C.toInt())
        if (chartHistory.size >= 2 && plotBottom > plotTop) {
            selectedMetrics.forEachIndexed { index, selection ->
                drawSeries(
                    graphics = graphics,
                    samples = chartHistory,
                    selection = selection,
                    left = plotLeft,
                    right = plotRight,
                    top = plotTop,
                    bottom = plotBottom,
                    maximum = seriesMaximum(chartHistory, selection),
                    color = seriesColor(index),
                )
            }
        }
        renderTimeline(graphics, history)
    }

    /** 根据时间范围把完整历史裁剪为当前主图窗口。 */
    private fun visibleHistory(history: List<PerformanceStatusSample>): List<PerformanceStatusSample> {
        if (history.size < 2) return history
        val lastIndex = history.lastIndex
        val startIndex = (rangeStart * lastIndex).roundToInt().coerceIn(0, lastIndex - 1)
        val endIndex = (rangeEnd * lastIndex).roundToInt().coerceIn(startIndex + 1, lastIndex)
        return history.subList(startIndex, endIndex + 1)
    }

    /** 绘制完整历史总览线和当前可视范围的左右拖动把手。 */
    private fun renderTimeline(graphics: GuiGraphics, history: List<PerformanceStatusSample>) {
        val y = (timelineTop + timelineBottom) / 2
        graphics.fill(timelineLeft, y - 1, timelineRight, y + 1, 0xFF3E4650.toInt())
        val startX = timelineX(rangeStart)
        val endX = timelineX(rangeEnd)
        graphics.fill(startX, y - 2, endX + 1, y + 2, 0xFF55A9D6.toInt())
        graphics.fill(startX - 3, timelineTop, startX + 3, timelineBottom + 1, 0xFFE3E8ED.toInt())
        graphics.fill(endX - 3, timelineTop, endX + 3, timelineBottom + 1, 0xFFE3E8ED.toInt())
        if (history.size >= 2) {
            val first = history.first().elapsedMillis
            val last = history.last().elapsedMillis
            val start = history[(rangeStart * history.lastIndex).roundToInt().coerceIn(0, history.lastIndex)].elapsedMillis
            val end = history[(rangeEnd * history.lastIndex).roundToInt().coerceIn(0, history.lastIndex)].elapsedMillis
            val text = "${formatDuration(end - start)} / ${formatDuration(last - first)}"
            graphics.drawString(font, text, timelineLeft, timelineTop - 10, 0xFF9DA7B3.toInt(), false)
        }
    }

    /** 用秒、毫秒显示当前时间窗口和完整历史长度。 */
    private fun formatDuration(durationMillis: Long): String {
        return if (durationMillis >= 1_000L) {
            String.format(Locale.ROOT, "%.1fs", durationMillis / 1_000.0)
        } else {
            "${durationMillis.coerceAtLeast(0L)}ms"
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
        selectedMetrics.forEachIndexed { index, selection ->
            val text = when (selection) {
                is PerformanceStatusChartSelection.Metric -> {
                    val current = latestMetricValue(samples, selection)
                    val maximum = seriesMaximum(samples, selection)
                    "${selection.label} ${formatChartValue(selection, current)} / ${formatChartValue(selection, maximum)}"
                }
                is PerformanceStatusChartSelection.Ratio -> {
                    val stats = ratioStats(samples, selection)
                    if (stats == null) {
                        "${selection.label} 当前 -"
                    } else {
                        "${selection.label} 当前 ${formatRatio(stats.current)} 总 ${formatRatio(stats.total)} " +
                            "高 ${formatRatio(stats.maximum)} 低 ${formatRatio(stats.minimum)}"
                    }
                }
            }
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
        selection: PerformanceStatusChartSelection,
    ): Double {
        var maximum = selection.minimumMaximum
        samples.forEach { sample ->
            val value = selection.extract(sample)
            if (value != null && value.isFinite() && value >= 0.0 && value > maximum) maximum = value
        }
        return maximum
    }

    /** 返回一项曲线最后一个有效样本。 */
    private fun latestMetricValue(
        samples: List<PerformanceStatusSample>,
        selection: PerformanceStatusChartSelection,
    ): Double? {
        for (index in samples.lastIndex downTo 0) {
            selection.extract(samples[index])?.let { value ->
                if (value.isFinite() && value >= 0.0) return value
            }
        }
        return null
    }

    /** 计算比值曲线当前窗口的当前、累计、最高和最低值。 */
    private fun ratioStats(
        samples: List<PerformanceStatusSample>,
        ratio: PerformanceStatusChartSelection.Ratio,
    ): RatioStats? {
        var performanceTotal = 0.0
        var impactTotal = 0.0
        var minimum = Double.POSITIVE_INFINITY
        var maximum = Double.NEGATIVE_INFINITY
        var current: Double? = null
        samples.forEach { sample ->
            val impactValue = ratio.impact.extract(sample)
            val performanceValue = ratio.performance.extract(sample)
            if (impactValue == null || performanceValue == null || performanceValue <= 0.0) return@forEach
            if (!impactValue.isFinite() || !performanceValue.isFinite()) return@forEach
            val value = impactValue / performanceValue
            if (!value.isFinite() || value < 0.0) return@forEach
            performanceTotal += performanceValue
            impactTotal += impactValue
            minimum = minOf(minimum, value)
            maximum = maxOf(maximum, value)
            current = value
        }
        if (performanceTotal <= 0.0 || !minimum.isFinite() || !maximum.isFinite()) return null
        return RatioStats(
            current = current ?: return null,
            total = impactTotal / performanceTotal,
            minimum = minimum,
            maximum = maximum,
        )
    }

    /** 绘制一条原始指标或比值曲线。 */
    private fun drawSeries(
        graphics: GuiGraphics,
        samples: List<PerformanceStatusSample>,
        selection: PerformanceStatusChartSelection,
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
            val value = selection.extract(sample) ?: return@forEachIndexed
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

    /** 返回选择顺序对应的 12 色高对比度曲线颜色。 */
    private fun seriesColor(index: Int): Int {
        return when (index % PERFORMANCE_STATUS_MAX_SELECTED_SERIES) {
            0 -> 0xFF55D6A9.toInt()
            1 -> 0xFFFFC857.toInt()
            2 -> 0xFF57A7FF.toInt()
            3 -> 0xFFE980C0.toInt()
            4 -> 0xFFFF6B6B.toInt()
            5 -> 0xFF8DE1FF.toInt()
            6 -> 0xFFB18CFF.toInt()
            7 -> 0xFFFF9F43.toInt()
            8 -> 0xFF7ED957.toInt()
            9 -> 0xFFFF78C8.toInt()
            10 -> 0xFF00D4C7.toInt()
            else -> 0xFFD4E157.toInt()
        }
    }

    /** 按曲线单位格式化图例值。 */
    private fun formatChartValue(selection: PerformanceStatusChartSelection, value: Double?): String {
        value ?: return "-"
        return when (selection.valueKind) {
            PerformanceStatusChartValueKind.NUMBER -> {
                if (value == value.toLong().toDouble()) value.toLong().toString() else format(value)
            }
            PerformanceStatusChartValueKind.MILLISECONDS -> format(value, " ms")
            PerformanceStatusChartValueKind.BYTES -> bytes(value.toLong())
        }
    }

    /** 以紧凑小数显示性能项与影响项的比值。 */
    private fun formatRatio(value: Double): String {
        return if (value >= 1.0) {
            String.format(Locale.ROOT, "%.2f", value)
        } else {
            String.format(Locale.ROOT, "%.4f", value)
        }
    }

    /** 比值曲线的窗口统计结果。 */
    private data class RatioStats(
        val current: Double,
        val total: Double,
        val minimum: Double,
        val maximum: Double,
    )

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

    /**
     * 时间轴拖动区域。
     *
     * NONE 表示未拖动；START 和 END 分别改变左右边界；WINDOW 保持跨度并平移整个范围。
     * 状态只在一次鼠标按下到释放期间有效，不跨 GUI 生命周期保存。
     */
    private enum class RangeDragMode {
        /** 当前没有时间范围拖动。 */
        NONE,

        /** 正在拖动左侧范围把手。 */
        START,

        /** 正在拖动右侧范围把手。 */
        END,

        /** 正在平移两个把手之间的完整范围。 */
        WINDOW,
    }

    /** 一行当前值及其可选图表维度。 */
    private data class MetricRow(
        val label: String,
        val value: String,
        val metric: PerformanceStatusChartMetric? = null,
        val selection: PerformanceStatusChartSelection? = null,
    )

    /** Status 表格分段。 */
    private enum class View {
        CLIENT,
        SERVER,
        NETWORK,
        CORRELATION,
    }
}
