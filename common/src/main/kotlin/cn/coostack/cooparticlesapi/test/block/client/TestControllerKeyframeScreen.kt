package cn.coostack.cooparticlesapi.test.block.client

import cn.coostack.cooparticlesapi.test.block.BlockTestAnimationKeyframe
import cn.coostack.cooparticlesapi.test.block.BlockTestAnimationTrack
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketOpenTestControllerScreenS2C
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketUpdateTestControllerC2S
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3
import kotlin.math.round

/**
 * 编辑单个关键帧的 tick 和三维值，并提供玩家取点。
 *
 * 示例：双击时间线帧后手填坐标，再点击保存返回曲线编辑器。
 * 禁止把位置轨道的值当作绝对世界坐标保存；位置始终相对控制器中心。
 *
 * @property parent 返回的曲线编辑器
 * @property track 正在编辑的轨道
 * @property frameIndex 关键帧索引
 * @property kind 位置或 forward 轨道类型
 * @property screenPacket 当前控制器开屏快照，用于复用世界取点流程
 * @property draftPacket 当前控制器客户端草稿，用于计算取点原点
 */
class TestControllerKeyframeScreen(
    private val parent: TestControllerCurveEditorScreen,
    private val track: BlockTestAnimationTrack,
    private val frameIndex: Int,
    private val kind: TestControllerTrackKind,
    private val origin: Vec3,
    private val screenPacket: PacketOpenTestControllerScreenS2C,
    private val draftPacket: PacketUpdateTestControllerC2S,
) : Screen(Component.literal("${kind.displayName}关键帧")) {
    private lateinit var tickBox: EditBox
    private lateinit var xBox: EditBox
    private lateinit var yBox: EditBox
    private lateinit var zBox: EditBox
    private lateinit var pickModeButton: Button
    private lateinit var pickButton: Button
    private lateinit var undoButton: Button
    private lateinit var redoButton: Button
    private lateinit var cancelButton: Button
    private lateinit var saveButton: Button
    private val inputHistory = TestControllerUndoHistory<KeyframeInput>(capacity = 64)
    private var preservedTick: String? = null
    private var preservedX: String? = null
    private var preservedY: String? = null
    private var preservedZ: String? = null

    private data class KeyframeInput(val tick: String, val x: String, val y: String, val z: String)

    /**
     * 建立关键帧输入框和取点按钮。
     *
     * 示例：forward 轨道仍显示三个分量，保存时会归一化采样值。
     * 禁止在初始化阶段修改关键帧对象。
     */
    override fun init() {
        val frame = track.keyframes.getOrNull(frameIndex) ?: run {
            returnToParent()
            return
        }
        val left = width / 2 - 150
        val tickValue = preservedTick ?: frame.tick.toString()
        val xValue = preservedX ?: format(frame.value.x)
        val yValue = preservedY ?: format(frame.value.y)
        val zValue = preservedZ ?: format(frame.value.z)
        val hadPreservedInput = preservedTick != null || preservedX != null || preservedY != null || preservedZ != null
        preservedTick = null
        preservedX = null
        preservedY = null
        preservedZ = null
        tickBox = input(left + 90, 56, 70, tickValue)
        xBox = input(left + 90, 88, 60, xValue)
        yBox = input(left + 156, 88, 60, yValue)
        zBox = input(left + 222, 88, 60, zValue)
        pickModeButton = Button.builder(Component.literal("拾取模式")) {
            startPickMode()
        }.bounds(left, 120, 80, 20).build()
        pickButton = Button.builder(Component.literal("拾取玩家")) {
            pickFromPlayer()
        }.bounds(left + 90, 120, 100, 20).build()
        cancelButton = Button.builder(Component.literal("取消")) {
            returnToParent()
        }.bounds(left + 150, height - 42, 70, 20).build()
        saveButton = Button.builder(Component.literal("保存")) {
            applyValues(frame)
            parent.commitKeyframeEdit()
            returnToParent()
        }.bounds(left + 230, height - 42, 70, 20).build()
        undoButton = Button.builder(Component.literal("撤回")) {
            restoreInput(inputHistory.undo())
        }.bounds(left, height - 42, 70, 20).build()
        redoButton = Button.builder(Component.literal("重做")) {
            restoreInput(inputHistory.redo())
        }.bounds(left + 74, height - 42, 70, 20).build()
        addRenderableWidget(tickBox)
        addRenderableWidget(xBox)
        addRenderableWidget(yBox)
        addRenderableWidget(zBox)
        addRenderableWidget(pickModeButton)
        addRenderableWidget(pickButton)
        addRenderableWidget(undoButton)
        addRenderableWidget(redoButton)
        addRenderableWidget(cancelButton)
        addRenderableWidget(saveButton)
        if (!hadPreservedInput) {
            inputHistory.seed(currentInput())
        } else {
            inputHistory.record(currentInput())
        }
        updateHistoryButtons()
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (Screen.hasControlDown()) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_Z && Screen.hasShiftDown()) {
                restoreInput(inputHistory.redo())
                return true
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_Z) {
                restoreInput(inputHistory.undo())
                return true
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_Y) {
                restoreInput(inputHistory.redo())
                return true
            }
        }
        val handled = super.keyPressed(keyCode, scanCode, modifiers)
        if (handled) recordInput()
        return handled
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        val handled = super.charTyped(codePoint, modifiers)
        if (handled) recordInput()
        return handled
    }

    /**
     * Esc 返回曲线编辑器。
     *
     * 示例：取消手填值不会改变原关键帧。
     * 禁止把取消操作发送到服务端。
     */
    override fun onClose() {
        returnToParent()
    }

    /**
     * 绘制关键帧字段标签。
     *
     * 示例：位置轨道标题会注明相对控制器中心。
     * 禁止在渲染时提交配置包。
     */
    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics, mouseX, mouseY, partialTick)
        super.render(graphics, mouseX, mouseY, partialTick)
        val left = width / 2 - 150
        graphics.drawString(font, title, left, 22, 0xFFFFFF, true)
        graphics.drawString(font, "tick", left, 62, 0xE0E0E0, true)
        graphics.drawString(font, if (kind == TestControllerTrackKind.POSITION) "相对位置" else "forward", left, 94, 0xE0E0E0, true)
        graphics.drawString(font, "X", left + 96, 78, 0xC0C0C0, false)
        graphics.drawString(font, "Y", left + 162, 78, 0xC0C0C0, false)
        graphics.drawString(font, "Z", left + 228, 78, 0xC0C0C0, false)
    }

    private fun input(x: Int, y: Int, width: Int, value: String): EditBox {
        return EditBox(font, x, y, width, 20, Component.empty()).also {
            it.value = value
            it.setFilter { text -> text.isEmpty() || text.matches(Regex("[-+0-9.eE]*")) }
        }
    }

    /**
     * 保存输入值，并把 tick 限制在当前帧的相邻边界内。
     *
     * 示例：首帧后面存在第 10 tick 的帧时，手填 20 会保存为 9。
     * 禁止让关键帧跨过相邻帧；否则锁定首帧会失去起始语义。
     *
     * @param frame 正在编辑且仍位于 [track] 中的关键帧
     */
    private fun applyValues(frame: BlockTestAnimationKeyframe) {
        frame.tick = (tickBox.value.toIntOrNull() ?: frame.tick).coerceIn(track.keyframeTickRange(frameIndex))
        frame.value = Vec3(
            xBox.value.toDoubleOrNull() ?: frame.value.x,
            yBox.value.toDoubleOrNull() ?: frame.value.y,
            zBox.value.toDoubleOrNull() ?: frame.value.z
        ).let(kind::normalizeValue)
        track.normalize(frame.value)
    }

    private fun pickFromPlayer() {
        if (track.keyframes.getOrNull(frameIndex) == null) return
        val player = Minecraft.getInstance().player ?: return
        val picked = if (kind == TestControllerTrackKind.POSITION) {
            player.position().subtract(origin)
        } else {
            kind.normalizeValue(player.lookAngle)
        }
        xBox.value = format(picked.x)
        yBox.value = format(picked.y)
        zBox.value = format(picked.z)
        recordInput()
    }

    /**
     * 进入与主控制器相同的 Shift+右键世界取点模式。
     *
     * 示例：位置关键帧取到方块中心后，目标坐标会回填为相对控制器中心的值。
     * 禁止在这里发送更新包；关键帧窗口的“保存”按钮才会修改轨道对象。
     */
    private fun startPickMode() {
        if (track.keyframes.getOrNull(frameIndex) == null) return
        captureInputValues()
        val pickKind = if (kind == TestControllerTrackKind.POSITION) {
            TestControllerPickKind.OFFSET
        } else {
            TestControllerPickKind.FORWARD
        }
        minecraft?.setScreen(null)
        TestControllerPickClient.begin(
            screenPacket = screenPacket,
            packet = draftPacket,
            kind = pickKind,
            precisionUnlocked = true,
            onPicked = { point, fromBlock, lookAngle ->
                val picked = if (kind == TestControllerTrackKind.POSITION) {
                    point.subtract(origin)
                } else if (fromBlock) {
                    kind.normalizeValue(point.subtract(origin))
                } else {
                    kind.normalizeValue(lookAngle)
                }
                preservedX = format(picked.x)
                preservedY = format(picked.y)
                preservedZ = format(picked.z)
                minecraft?.setScreen(this)
            },
            onCancelled = {
                minecraft?.setScreen(this)
            }
        )
    }

    /**
     * 暂存当前输入框内容，供取点模式结束后重新建立窗口时恢复。
     *
     * 示例：用户先修改 tick，再按 Esc 取消取点，tick 文本仍保持修改前的草稿。
     * 禁止在没有初始化输入框时读取控件状态。
     */
    private fun captureInputValues() {
        if (!::tickBox.isInitialized) return
        preservedTick = tickBox.value
        preservedX = xBox.value
        preservedY = yBox.value
        preservedZ = zBox.value
    }

    private fun format(value: Double): String {
        val rounded = round(value * 1_000_000.0) / 1_000_000.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }

    private fun returnToParent() {
        minecraft?.setScreen(parent)
    }

    private fun currentInput(): KeyframeInput {
        return KeyframeInput(tickBox.value, xBox.value, yBox.value, zBox.value)
    }

    private fun recordInput() {
        if (::tickBox.isInitialized) inputHistory.record(currentInput())
        updateHistoryButtons()
    }

    private fun restoreInput(input: KeyframeInput?) {
        if (input == null || !::tickBox.isInitialized) {
            updateHistoryButtons()
            return
        }
        tickBox.value = input.tick
        xBox.value = input.x
        yBox.value = input.y
        zBox.value = input.z
        updateHistoryButtons()
    }

    private fun updateHistoryButtons() {
        if (!::undoButton.isInitialized) return
        undoButton.active = inputHistory.canUndo
        redoButton.active = inputHistory.canRedo
    }
}
