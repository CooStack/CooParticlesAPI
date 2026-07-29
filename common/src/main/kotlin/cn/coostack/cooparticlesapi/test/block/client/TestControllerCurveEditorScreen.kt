package cn.coostack.cooparticlesapi.test.block.client

import cn.coostack.cooparticlesapi.test.block.BlockTestAnimationKeyframe
import cn.coostack.cooparticlesapi.test.block.BlockTestAnimationTrack
import cn.coostack.cooparticlesapi.test.block.BlockTestAnimationTrackCodec
import cn.coostack.cooparticlesapi.test.block.BlockTestCurveType
import cn.coostack.cooparticlesapi.test.block.BlockTestPlaybackMode
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketOpenTestControllerScreenS2C
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketUpdateTestControllerC2S
import net.minecraft.Util
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 编辑动态轨道的总时长、关键帧和 A 到 B 的进度曲线。
 *
 * 示例：Ctrl+左键时间线添加帧，选中相邻两帧后切换线性或贝塞尔。
 * 禁止在未选中相邻两帧时拖动曲线手柄。
 *
 * @property packet 服务端开屏快照
 * @property draft 动态窗口共享的客户端草稿
 * @property kind 当前编辑的位置或 forward
 * @property onBack 返回动态选择界面的动作
 */
class TestControllerCurveEditorScreen internal constructor(
    private val packet: PacketOpenTestControllerScreenS2C,
    private val draft: PacketUpdateTestControllerC2S,
    private val kind: TestControllerTrackKind,
    private val sharedHistory: TestControllerUndoHistory<TestControllerPacketDrafts.TestControllerConfigSnapshot>? = null,
    private val onBack: () -> Unit,
) : Screen(Component.literal("${kind.displayName}曲线")) {
    private lateinit var durationBox: EditBox
    private lateinit var playbackButton: Button
    private lateinit var curveButton: Button
    private lateinit var backButton: Button
    private lateinit var undoButton: Button
    private lateinit var redoButton: Button
    private val history = sharedHistory ?: TestControllerUndoHistory(
        initial = TestControllerPacketDrafts.snapshotFrom(packet, draft)
    )
    private val selectedIndices = LinkedHashSet<Int>()
    private var track = loadTrack()
    private var draggingKeyframe = -1
    private var draggingHandle = 0
    private var hoveredKeyframe = -1
    private var lastClickedKeyframe = -1
    private var lastClickTime = 0L

    /**
     * 记录上一帧总时长输入框是否持有焦点。
     *
     * 示例：Tab 切走焦点后的下一帧据此提交完整文本。
     * 禁止用该状态判断文本是否合法；数值校验由 [syncDuration] 负责。
     */
    private var durationWasFocused = false

    /**
     * 建立曲线编辑器控件。
     *
     * 示例：进入时总时长输入框会显示轨道当前 tick 数。
     * 禁止在这里发送网络包，提交由动态窗口或主界面负责。
     */
    override fun init() {
        selectedIndices.clear()
        draggingKeyframe = -1
        draggingHandle = 0
        durationWasFocused = false
        val left = contentLeft()
        durationBox = EditBox(font, left + 64, 32, 56, 20, Component.empty()).also {
            it.value = track.durationTicks.toString()
            it.setFilter { value -> value.isEmpty() || value.all { character -> character in '0'..'9' } }
        }
        playbackButton = Button.builder(Component.literal(playbackLabel())) {
            track.playbackMode = when (track.playbackMode) {
                BlockTestPlaybackMode.ONCE -> BlockTestPlaybackMode.LOOP
                BlockTestPlaybackMode.LOOP -> BlockTestPlaybackMode.PINGPONG
                BlockTestPlaybackMode.PINGPONG -> BlockTestPlaybackMode.ONCE
            }
            writeDraft()
            commitHistory()
            updateButtons()
        }.bounds(left + 126, 32, 104, 20).build()
        curveButton = Button.builder(Component.literal(curveLabel())) {
            val segment = selectedSegment() ?: return@builder
            track.keyframes[segment].curveToNext = when (track.keyframes[segment].curveToNext) {
                BlockTestCurveType.LINEAR -> BlockTestCurveType.BEZIER
                BlockTestCurveType.BEZIER -> BlockTestCurveType.LINEAR
            }
            writeDraft()
            commitHistory()
            updateButtons()
        }.bounds(left + 236, 32, 112, 20).build()
        undoButton = Button.builder(Component.literal("撤回")) {
            commitHistory()
            restoreHistory(history.undo())
        }.bounds(width - 108, 8, 48, 20).build()
        redoButton = Button.builder(Component.literal("重做")) {
            commitHistory()
            restoreHistory(history.redo())
        }.bounds(width - 56, 8, 48, 20).build()
        backButton = Button.builder(Component.literal("返回")) {
            finish()
        }.bounds(width / 2 - 35, height - 28, 70, 20).build()
        addRenderableWidget(durationBox)
        addRenderableWidget(playbackButton)
        addRenderableWidget(curveButton)
        addRenderableWidget(undoButton)
        addRenderableWidget(redoButton)
        addRenderableWidget(backButton)
        updateButtons()
    }

    /**
     * Esc 返回动态选择界面并保留当前草稿。
     *
     * 示例：关键帧设置窗口取消后仍能回到曲线编辑器。
     * 禁止把 Esc 当作保存到服务端。
     */
    override fun onClose() {
        finish()
    }

    /**
     * 在 Enter 提交总时长，其他按键继续交给当前控件处理。
     *
     * 示例：输入 `100` 后按 Enter，只会以完整文本修改一次轨道。
     * 禁止在数字仍处于逐字符输入阶段时提前归一化关键帧。
     *
     * @param keyCode GLFW 键码
     * @param scanCode 平台扫描码
     * @param modifiers 修饰键掩码
     * @return 当前界面是否消费按键
     */
    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (Screen.hasControlDown()) {
            if (keyCode == GLFW.GLFW_KEY_Z && Screen.hasShiftDown()) {
                commitHistory()
                restoreHistory(history.redo())
                return true
            }
            if (keyCode == GLFW.GLFW_KEY_Z) {
                commitHistory()
                restoreHistory(history.undo())
                return true
            }
            if (keyCode == GLFW.GLFW_KEY_Y) {
                commitHistory()
                restoreHistory(history.redo())
                return true
            }
        }
        if (durationBox.isFocused &&
            (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
        ) {
            syncDuration()
            commitHistory()
            setFocused(null)
            durationWasFocused = false
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        val handled = super.charTyped(codePoint, modifiers)
        if (handled) commitHistory()
        return handled
    }

    /**
     * 处理时间线添加、删除、选择和关键帧拖动。
     *
     * 示例：Alt+左键锁定帧不会删除，Ctrl+左键会以当前曲线值创建新帧。
     * 禁止在曲线图区域用 Ctrl 添加关键帧。
     */
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(mouseX, mouseY, button)
        }
        if (!durationBox.isMouseOver(mouseX, mouseY)) {
            syncDuration()
            if (durationBox.isFocused) {
                setFocused(null)
            }
            durationWasFocused = false
        }
        val handle = handleAt(mouseX, mouseY)
        if (handle != 0) {
            draggingHandle = handle
            return true
        }
        val keyframe = keyframeAt(mouseX, mouseY)
        if (keyframe >= 0) {
            if (Screen.hasAltDown()) {
                if (!track.keyframes[keyframe].locked) {
                    track.keyframes.removeAt(keyframe)
                    selectedIndices.clear()
                    writeDraft()
                    commitHistory()
                    updateButtons()
                }
                return true
            }
            val now = Util.getMillis()
            if (lastClickedKeyframe == keyframe && now - lastClickTime <= DOUBLE_CLICK_MILLIS) {
                minecraft?.setScreen(
                    TestControllerKeyframeScreen(
                        this,
                        track,
                        keyframe,
                        kind,
                        Vec3.atCenterOf(packet.blockPos),
                        packet,
                        draft,
                    )
                )
                lastClickedKeyframe = -1
                return true
            }
            lastClickedKeyframe = keyframe
            lastClickTime = now
            toggleSelection(keyframe)
            draggingKeyframe = keyframe
            return true
        }
        if (Screen.hasControlDown() && inTimeline(mouseX, mouseY)) {
            val tick = tickAt(mouseX)
            addKeyframe(tick)
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    /**
     * 拖动时间线帧或贝塞尔手柄。
     *
     * 示例：拖动首帧向后会延长起点静止区间。
     * 禁止拖动关键帧越过相邻帧。
     */
    override fun mouseDragged(
        mouseX: Double,
        mouseY: Double,
        button: Int,
        dragX: Double,
        dragY: Double,
    ): Boolean {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
        if (draggingHandle != 0) {
            moveHandle(mouseX, mouseY, draggingHandle)
            return true
        }
        if (draggingKeyframe >= 0) {
            moveKeyframe(mouseX)
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    /**
     * 结束帧或手柄拖动。
     *
     * 示例：释放鼠标后轨道会立即写回动态窗口草稿。
     * 禁止在这里重新排序已完成归一化的关键帧。
     */
    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && (draggingKeyframe >= 0 || draggingHandle != 0)) {
            draggingKeyframe = -1
            draggingHandle = 0
            writeDraft()
            commitHistory()
            return true
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    /**
     * 绘制时间线、关键帧标记和从下到上的进度曲线。
     *
     * 示例：先由 [Screen.render] 完成一次背景模糊，再绘制保持清晰的时间线和曲线。
     * 禁止额外调用 `renderBackground`；父类已经执行该步骤，重复调用会把自绘面板一起模糊。
     */
    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (durationWasFocused && !durationBox.isFocused) {
            syncDuration()
        }
        durationWasFocused = durationBox.isFocused
        hoveredKeyframe = keyframeAt(mouseX.toDouble(), mouseY.toDouble())
        super.render(graphics, mouseX, mouseY, partialTick)
        if (draggingKeyframe < 0 && draggingHandle == 0) commitHistory()
        updateHistoryButtons()
        drawTimeline(graphics)
        drawCurve(graphics)
        val left = contentLeft()
        graphics.drawString(font, title, left, 14, 0xFFFFFF, true)
        graphics.drawString(font, "总 tick 数", left, 38, 0xE0E0E0, true)
        graphics.drawString(font, "关键帧：Ctrl+左键添加，Alt+左键删除，双击设置", left, 62, 0xC0C0C0, true)
        graphics.drawString(font, "时间线", left, TIMELINE_TOP - 10, 0xE0E0E0, true)
        graphics.drawString(font, "进度", graphLeft() + 4, GRAPH_TOP + 4, 0xE0E0E0, true)
    }

    /**
     * 在不透明面板内绘制时间轴、刻度和关键帧。
     *
     * 示例：首尾关键帧会从面板边缘内缩，完整显示并保留点击空间。
     * 禁止使用半透明背景，否则模糊世界画面会降低标记对比度。
     *
     * @param graphics 当前 GUI 绘图上下文
     */
    private fun drawTimeline(graphics: GuiGraphics) {
        val left = timelineLeft()
        val right = timelineRight()
        val bottom = timelineBottom()
        graphics.fill(left, TIMELINE_TOP, right, bottom, 0xFF151515.toInt())
        graphics.fill(left, TIMELINE_TOP, right, TIMELINE_TOP + 2, 0xFFE0E0E0.toInt())
        graphics.fill(left, bottom - 2, right, bottom, 0xFFB8B8B8.toInt())
        graphics.fill(left, TIMELINE_TOP, left + 2, bottom, 0xFFE0E0E0.toInt())
        graphics.fill(right - 2, TIMELINE_TOP, right, bottom, 0xFFB8B8B8.toInt())
        graphics.fill(timelinePlotLeft(), TIMELINE_TOP + 13, timelinePlotRight(), TIMELINE_TOP + 16, 0xFFE0E0E0.toInt())
        for (tick in 0..track.durationTicks step tickStep()) {
            val x = timelineX(tick.toDouble())
            graphics.fill(x, TIMELINE_TOP + 8, x + 2, TIMELINE_TOP + 22, 0xFFB8B8B8.toInt())
            graphics.drawString(font, tick.toString(), x + 2, TIMELINE_TOP + 2, 0xFFE0E0E0.toInt(), false)
        }
        track.keyframes.forEachIndexed { index, frame ->
            val x = timelineX(frame.tick.toDouble())
            val selected = index in selectedIndices
            val hovered = index == hoveredKeyframe && Screen.hasAltDown()
            val color = when {
                hovered -> 0xFFFF3333.toInt()
                selected -> 0xFFFFD45C.toInt()
                frame.locked -> 0xFF65D0FF.toInt()
                else -> 0xFFE0E0E0.toInt()
            }
            graphics.fill(x - 7, TIMELINE_TOP + 5, x + 8, TIMELINE_TOP + 26, 0xFF202020.toInt())
            graphics.fill(x - 5, TIMELINE_TOP + 7, x + 6, TIMELINE_TOP + 24, color)
            graphics.drawString(font, "${index + 1}", x - 3, bottom + 3, color, false)
        }
    }

    /**
     * 绘制不透明进度曲线面板、网格和当前相邻关键帧的插值曲线。
     *
     * 示例：没有选择相邻帧时仍显示清晰的面板边框与进度网格。
     * 禁止让世界背景透过曲线区域，以免网格与路径线混在一起。
     *
     * @param graphics 当前 GUI 绘图上下文
     */
    private fun drawCurve(graphics: GuiGraphics) {
        val left = graphLeft()
        val top = GRAPH_TOP
        val right = graphRight()
        val bottom = graphBottom()
        graphics.fill(left, top, right, bottom, 0xFF101010.toInt())
        graphics.fill(left, top, right, top + 2, 0xFFE0E0E0.toInt())
        graphics.fill(left, bottom - 2, right, bottom, 0xFFC0C0C0.toInt())
        graphics.fill(left, top, left + 2, bottom, 0xFFE0E0E0.toInt())
        graphics.fill(right - 2, top, right, bottom, 0xFFC0C0C0.toInt())
        for (step in 1..4) {
            val x = left + graphWidth() * step / 5
            val y = bottom - graphHeight() * step / 5
            graphics.fill(x, top + 2, x + 2, bottom - 2, 0xFF686868.toInt())
            graphics.fill(left + 2, y, right - 2, y + 2, 0xFF686868.toInt())
        }
        val segment = selectedSegment() ?: return
        val first = track.keyframes[segment]
        val second = track.keyframes[segment + 1]
        var previousX = 0.0
        var previousY = 0.0
        for (step in 1..CURVE_SAMPLES) {
            val raw = step.toDouble() / CURVE_SAMPLES.toDouble()
            val progress = track.segmentProgress(segment, raw)
            val x = raw
            drawLine(
                graphics,
                graphX(previousX), graphY(previousY),
                graphX(x), graphY(progress),
                0xFF65D0FF.toInt()
            )
            previousX = x
            previousY = progress
        }
        if (first.curveToNext == BlockTestCurveType.BEZIER) {
            drawLine(graphics, graphX(0.0), graphY(0.0), graphX(first.outgoingTime), graphY(first.outgoingProgress), 0xFFB080FF.toInt())
            drawLine(graphics, graphX(1.0), graphY(1.0), graphX(second.incomingTime), graphY(second.incomingProgress), 0xFFB080FF.toInt())
            drawHandle(graphics, graphX(first.outgoingTime), graphY(first.outgoingProgress), 0xFFFFB060.toInt())
            drawHandle(graphics, graphX(second.incomingTime), graphY(second.incomingProgress), 0xFFFFB060.toInt())
        }
    }

    private fun drawHandle(graphics: GuiGraphics, x: Int, y: Int, color: Int) {
        graphics.fill(x - 5, y - 5, x + 6, y + 6, 0xFF151515.toInt())
        graphics.fill(x - 3, y - 3, x + 4, y + 4, color)
    }

    /**
     * 用不透明方块采样绘制曲线，确保低 GUI 缩放下仍有连续的可见线宽。
     *
     * 示例：相邻采样点之间使用 4x4 方块连接。
     * 禁止改回单像素填充，否则曲线会在缩放后再次发虚。
     */
    private fun drawLine(graphics: GuiGraphics, x1: Int, y1: Int, x2: Int, y2: Int, color: Int) {
        val steps = max(abs(x2 - x1), abs(y2 - y1)).coerceAtLeast(1)
        for (step in 0..steps) {
            val progress = step.toDouble() / steps.toDouble()
            val x = (x1 + (x2 - x1) * progress).roundToInt()
            val y = (y1 + (y2 - y1) * progress).roundToInt()
            graphics.fill(x - 1, y - 1, x + 3, y + 3, color)
        }
    }

    private fun toggleSelection(index: Int) {
        if (!selectedIndices.add(index)) {
            selectedIndices.remove(index)
        }
        while (selectedIndices.size > 2) {
            selectedIndices.remove(selectedIndices.first())
        }
        updateButtons()
    }

    private fun addKeyframe(tick: Int) {
        if (track.keyframes.size >= BlockTestAnimationTrack.MAX_KEYFRAMES) return
        val first = track.keyframes.firstOrNull() ?: return
        if (tick <= first.tick || track.keyframes.any { it.tick == tick }) return
        val value = kind.normalizeValue(track.sampleTimeline(tick.toDouble()))
        track.keyframes += BlockTestAnimationKeyframe(tick, value)
        track.normalize(fallbackValue())
        selectedIndices.clear()
        selectedIndices += track.keyframes.indexOfFirst { it.tick == tick }
        writeDraft()
        commitHistory()
        updateButtons()
    }

    /**
     * 按时间线横坐标移动当前拖动帧，并限制在相邻帧之间。
     *
     * 示例：拖动首帧时最多停在第二帧前一 tick。
     * 禁止直接修改帧索引或重新排序列表；范围由轨道统一提供。
     *
     * @param mouseX 鼠标在界面中的横坐标
     */
    private fun moveKeyframe(mouseX: Double) {
        val frame = track.keyframes.getOrNull(draggingKeyframe) ?: return
        val tick = tickAt(mouseX)
        frame.tick = tick.coerceIn(track.keyframeTickRange(draggingKeyframe))
        writeDraft()
    }

    private fun moveHandle(mouseX: Double, mouseY: Double, handle: Int) {
        val segment = selectedSegment() ?: return
        val frame = if (handle == OUTGOING_HANDLE) track.keyframes[segment] else track.keyframes[segment + 1]
        val x = ((mouseX - graphLeft()).toDouble() / graphWidth().toDouble()).coerceIn(0.0, 1.0)
        val progress = (1.0 - (mouseY - graphTop()).toDouble() / graphHeight().toDouble()).coerceIn(0.0, 1.0)
        if (handle == OUTGOING_HANDLE) {
            frame.outgoingTime = x.coerceAtMost(track.keyframes[segment + 1].incomingTime)
            frame.outgoingProgress = progress
        } else {
            frame.incomingTime = x.coerceAtLeast(track.keyframes[segment].outgoingTime)
            frame.incomingProgress = progress
        }
        writeDraft()
    }

    private fun handleAt(mouseX: Double, mouseY: Double): Int {
        val segment = selectedSegment() ?: return 0
        val first = track.keyframes[segment]
        val second = track.keyframes[segment + 1]
        if (first.curveToNext != BlockTestCurveType.BEZIER) return 0
        val outgoingDistance = distance(mouseX, mouseY, graphX(first.outgoingTime), graphY(first.outgoingProgress))
        if (outgoingDistance <= HANDLE_RADIUS) return OUTGOING_HANDLE
        val incomingDistance = distance(mouseX, mouseY, graphX(second.incomingTime), graphY(second.incomingProgress))
        return if (incomingDistance <= HANDLE_RADIUS) INCOMING_HANDLE else 0
    }

    /**
     * 返回时间线命中半径内距离鼠标最近的关键帧。
     *
     * 示例：相邻 tick 的标记重叠时，点击后一个标记仍会选中后一个。
     * 禁止按列表顺序返回第一个候选，否则密集关键帧无法编辑。
     */
    private fun keyframeAt(mouseX: Double, mouseY: Double): Int {
        if (!inTimeline(mouseX, mouseY)) return -1
        var nearestIndex = -1
        var nearestDistance = MARKER_RADIUS + 1.0
        track.keyframes.forEachIndexed { index, frame ->
            val distance = abs(mouseX - timelineX(frame.tick.toDouble()))
            if (distance <= MARKER_RADIUS && distance < nearestDistance) {
                nearestIndex = index
                nearestDistance = distance
            }
        }
        return nearestIndex
    }

    private fun selectedSegment(): Int? {
        if (selectedIndices.size != 2) return null
        val sorted = selectedIndices.sorted()
        val leftIndex = sorted[0]
        return leftIndex.takeIf {
            sorted[1] == it + 1 && it in 0 until track.keyframes.lastIndex
        }
    }

    private fun updateButtons() {
        if (!::curveButton.isInitialized) return
        playbackButton.message = Component.literal(playbackLabel())
        curveButton.message = Component.literal(curveLabel())
        curveButton.active = selectedSegment() != null
    }

    private fun playbackLabel(): String = "播放: ${track.playbackMode.displayName}"

    private fun curveLabel(): String {
        val segment = selectedSegment() ?: return "曲线: 需选相邻帧"
        return "曲线: ${track.keyframes[segment].curveToNext.displayName}"
    }

    /**
     * 提交完整的总时长文本并归一化轨道。
     *
     * 示例：输入 `80` 后离开输入框，轨道会一次性调整为 80 tick。
     * 禁止把空文本当作 0；无效输入会恢复当前总时长。
     */
    private fun syncDuration() {
        val parsed = durationBox.value.toIntOrNull()
        if (parsed == null) {
            durationBox.value = track.durationTicks.toString()
            return
        }
        if (parsed != track.durationTicks) {
            track.durationTicks = parsed
            track.normalize(fallbackValue())
            selectedIndices.clear()
            draggingKeyframe = -1
            writeDraft()
            updateButtons()
        }
        durationBox.value = track.durationTicks.toString()
    }

    private fun writeDraft() {
        if (kind == TestControllerTrackKind.POSITION) {
            draft.positionTrack = BlockTestAnimationTrackCodec.encode(track)
        } else {
            draft.forwardTrack = BlockTestAnimationTrackCodec.encode(track)
        }
    }

    /** 将当前曲线草稿提交到共享历史。 */
    private fun commitHistory() {
        history.record(TestControllerPacketDrafts.snapshotFrom(packet, draft))
    }

    private fun updateHistoryButtons() {
        if (!::undoButton.isInitialized) return
        undoButton.active = history.canUndo
        redoButton.active = history.canRedo
    }

    /** 关键帧设置窗口保存后提交一条完整曲线修改。 */
    internal fun commitKeyframeEdit() {
        writeDraft()
        commitHistory()
    }

    private fun restoreHistory(snapshot: TestControllerPacketDrafts.TestControllerConfigSnapshot?) {
        if (snapshot == null) {
            updateHistoryButtons()
            return
        }
        val restoredPacket = snapshot.toPacket()
        TestControllerPacketDrafts.copyDraft(TestControllerPacketDrafts.draftFrom(restoredPacket), draft)
        track = loadTrack()
        selectedIndices.clear()
        draggingKeyframe = -1
        draggingHandle = 0
        if (::durationBox.isInitialized) durationBox.value = track.durationTicks.toString()
        updateButtons()
        updateHistoryButtons()
    }

    private fun loadTrack(): BlockTestAnimationTrack {
        val fallback = fallbackValue()
        val encoded = if (kind == TestControllerTrackKind.POSITION) draft.positionTrack else draft.forwardTrack
        return BlockTestAnimationTrackCodec.decode(encoded, fallback)
    }

    private fun fallbackValue(): Vec3 {
        return if (kind == TestControllerTrackKind.POSITION) {
            Vec3(draft.offsetX, draft.offsetY, draft.offsetZ)
        } else {
            Vec3(draft.forwardX, draft.forwardY, draft.forwardZ)
        }
    }

    private fun finish() {
        syncDuration()
        writeDraft()
        commitHistory()
        onBack()
    }

    private fun inTimeline(mouseX: Double, mouseY: Double): Boolean {
        return mouseX in timelineLeft().toDouble()..timelineRight().toDouble() &&
                mouseY in TIMELINE_TOP.toDouble()..timelineBottom().toDouble()
    }

    /**
     * 把鼠标横坐标换算为时间轴 tick，并限制在轨道总时长内。
     *
     * 示例：点击内缩后的时间轴右端会返回 [BlockTestAnimationTrack.durationTicks]。
     * 禁止使用整个面板宽度计算，否则首尾帧会再次贴到边框上。
     *
     * @param mouseX 鼠标在界面中的横坐标
     * @return 对应的轨道 tick
     */
    private fun tickAt(mouseX: Double): Int {
        val progress = ((mouseX - timelinePlotLeft()).toDouble() / timelinePlotWidth().toDouble()).coerceIn(0.0, 1.0)
        return (progress * track.durationTicks).roundToInt().coerceIn(0, track.durationTicks)
    }

    /**
     * 把轨道 tick 映射到内缩后的时间轴横坐标。
     *
     * 示例：tick 为 `0` 时返回 [timelinePlotLeft]。
     * 禁止传入其他轨道的时长比例；本方法使用当前 [track] 总时长。
     *
     * @param tick 当前轨道的 tick，可包含小数
     * @return 时间轴上的 GUI 横坐标
     */
    private fun timelineX(tick: Double): Int {
        return timelinePlotLeft() + (tick / track.durationTicks.toDouble() * timelinePlotWidth()).roundToInt()
    }

    /**
     * 返回时间轴实际绘制刻度的左边界。
     *
     * 示例：首帧中心使用该坐标，标记不会被面板裁掉。
     * 禁止把它用于面板背景左边界。
     *
     * @return 内缩后的时间轴左坐标
     */
    private fun timelinePlotLeft(): Int = timelineLeft() + TIMELINE_HORIZONTAL_PADDING

    /**
     * 返回时间轴实际绘制刻度的右边界。
     *
     * 示例：末帧中心使用该坐标，标记完整落在面板内。
     * 禁止把它用于面板背景右边界。
     *
     * @return 内缩后的时间轴右坐标
     */
    private fun timelinePlotRight(): Int = timelineRight() - TIMELINE_HORIZONTAL_PADDING

    /**
     * 返回内缩后时间轴的可用宽度。
     *
     * 示例：[tickAt] 使用该值换算鼠标位置。
     * 禁止返回零或负数；[contentWidth] 的最小值保证该条件。
     *
     * @return 时间轴刻度宽度
     */
    private fun timelinePlotWidth(): Int = timelineWidth() - TIMELINE_HORIZONTAL_PADDING * 2

    private fun contentWidth(): Int = (width - 24).coerceAtMost(CONTENT_MAX_WIDTH).coerceAtLeast(240)

    private fun contentLeft(): Int = (width - contentWidth()) / 2

    private fun timelineWidth(): Int = contentWidth()

    private fun timelineLeft(): Int = contentLeft()

    private fun timelineRight(): Int = timelineLeft() + timelineWidth()

    private fun timelineBottom(): Int = TIMELINE_TOP + TIMELINE_HEIGHT

    private fun graphLeft(): Int = timelineLeft()

    private fun graphWidth(): Int = contentWidth()

    private fun graphHeight(): Int = (height - GRAPH_TOP - 40).coerceAtLeast(44)

    private fun graphRight(): Int = graphLeft() + graphWidth()

    private fun graphTop(): Int = GRAPH_TOP

    private fun graphBottom(): Int = graphTop() + graphHeight()

    private fun graphX(progress: Double): Int = graphLeft() + (progress.coerceIn(0.0, 1.0) * graphWidth()).roundToInt()

    private fun graphY(progress: Double): Int = graphBottom() - (progress.coerceIn(0.0, 1.0) * graphHeight()).roundToInt()

    private fun distance(x: Double, y: Double, targetX: Int, targetY: Int): Double {
        val dx = x - targetX
        val dy = y - targetY
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun tickStep(): Int {
        return when {
            track.durationTicks <= 20 -> 5
            track.durationTicks <= 100 -> 10
            else -> 20
        }
    }

    /**
     * 保存曲线编辑器固定尺寸与交互阈值。
     *
     * 示例：时间线使用 [TIMELINE_HEIGHT] 保持布局稳定。
     * 禁止在这里保存当前轨道或选中关键帧。
     */
    companion object {
        private const val CONTENT_MAX_WIDTH = 520
        private const val TIMELINE_TOP = 88
        private const val TIMELINE_HEIGHT = 32
        private const val GRAPH_TOP = 150
        private const val CURVE_SAMPLES = 64
        /**
         * 时间轴首尾为关键帧标记保留的水平边距。
         *
         * 示例：首帧中心位于面板左边界右侧 8 px。
         * 禁止设为小于关键帧半宽的值。
         */
        private const val TIMELINE_HORIZONTAL_PADDING = 8

        /**
         * 关键帧中心的水平点击半径，单位为 GUI 像素。
         *
         * 示例：鼠标距标记中心 9 px 时仍能选中该帧。
         * 禁止缩小到低于标记半宽，否则边缘可见区域无法点击。
         */
        private const val MARKER_RADIUS = 10.0
        private const val HANDLE_RADIUS = 9.0
        private const val DOUBLE_CLICK_MILLIS = 250L
        private const val OUTGOING_HANDLE = 1
        private const val INCOMING_HANDLE = 2
    }
}
