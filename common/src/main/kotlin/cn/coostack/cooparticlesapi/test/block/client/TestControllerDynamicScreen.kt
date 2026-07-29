package cn.coostack.cooparticlesapi.test.block.client

import cn.coostack.cooparticlesapi.network.packet.api.CooClientPacketManager
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketOpenTestControllerScreenS2C
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketUpdateTestControllerC2S
import cn.coostack.cooparticlesapi.test.block.BlockTestAnimationTrackCodec
import cn.coostack.cooparticlesapi.test.block.BlockTestPlaybackMode
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3

/**
 * 选择模拟玩家的动态通道，并进入对应曲线编辑器。
 *
 * 示例：把位置切到动态后点击“编辑位置曲线”即可添加关键帧。
 * 禁止在这个界面直接修改服务端状态；保存按钮才会发送草稿。
 *
 * @property packet 用于返回主界面的服务端快照
 * @property draft 尚未提交的客户端配置
 */
class TestControllerDynamicScreen internal constructor(
    private val packet: PacketOpenTestControllerScreenS2C,
    private val draft: PacketUpdateTestControllerC2S,
    private val sharedHistory: TestControllerUndoHistory<TestControllerPacketDrafts.TestControllerConfigSnapshot>? = null,
) : Screen(Component.literal("动态效果")) {
    private lateinit var positionModeButton: Button
    private lateinit var forwardModeButton: Button
    private lateinit var positionEditButton: Button
    private lateinit var forwardEditButton: Button
    private lateinit var backButton: Button
    private lateinit var saveButton: Button
    private lateinit var undoButton: Button
    private lateinit var redoButton: Button
    private val history = sharedHistory ?: TestControllerUndoHistory(
        initial = TestControllerPacketDrafts.snapshotFrom(packet, draft)
    )

    /**
     * 创建轨道选择和命令按钮。
     *
     * 示例：从主界面进入时会直接显示服务端当前的动态开关。
     * 禁止在初始化阶段读取服务端方块实体。
     */
    override fun init() {
        val left = width / 2 - 150
        val top = 54
        positionModeButton = Button.builder(Component.literal(positionModeLabel())) {
            draft.positionDynamic = !draft.positionDynamic
            updateButtons()
        }.bounds(left + 90, top, 116, 20).build()
        forwardModeButton = Button.builder(Component.literal(forwardModeLabel())) {
            draft.forwardDynamic = !draft.forwardDynamic
            updateButtons()
        }.bounds(left + 90, top + 32, 116, 20).build()
        positionEditButton = Button.builder(Component.literal("编辑位置曲线")) {
            minecraft?.setScreen(
                TestControllerCurveEditorScreen(
                    packet,
                    draft,
                    TestControllerTrackKind.POSITION,
                    history,
                ) { reopenDynamicScreen() }
            )
        }.bounds(left + 212, top, 98, 20).build()
        forwardEditButton = Button.builder(Component.literal("编辑 forward 曲线")) {
            minecraft?.setScreen(
                TestControllerCurveEditorScreen(
                    packet,
                    draft,
                    TestControllerTrackKind.FORWARD,
                    history,
                ) { reopenDynamicScreen() }
            )
        }.bounds(left + 212, top + 32, 98, 20).build()
        backButton = Button.builder(Component.literal("返回")) {
            returnToController()
        }.bounds(left + 76, height - 42, 76, 20).build()
        saveButton = Button.builder(Component.literal("保存")) {
            CooClientPacketManager.sendTo(draft)
            minecraft?.setScreen(null)
        }.bounds(left + 166, height - 42, 76, 20).build()
        undoButton = Button.builder(Component.literal("撤回")) {
            recordHistory()
            restoreHistory(history.undo())
        }.bounds(width - 108, 8, 48, 20).build()
        redoButton = Button.builder(Component.literal("重做")) {
            recordHistory()
            restoreHistory(history.redo())
        }.bounds(width - 56, 8, 48, 20).build()
        addRenderableWidget(positionModeButton)
        addRenderableWidget(forwardModeButton)
        addRenderableWidget(positionEditButton)
        addRenderableWidget(forwardEditButton)
        addRenderableWidget(backButton)
        addRenderableWidget(saveButton)
        addRenderableWidget(undoButton)
        addRenderableWidget(redoButton)
        updateButtons()
    }

    /**
     * Esc 返回主控制器界面并保留尚未提交的草稿。
     *
     * 示例：编辑曲线后按 Esc，主界面仍能继续调整测试参数。
     * 禁止把 Esc 当作保存操作。
     */
    override fun onClose() {
        returnToController()
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (Screen.hasControlDown()) {
            val state = when {
                keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_Z && Screen.hasShiftDown() -> history.redo()
                keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_Z -> history.undo()
                keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_Y -> history.redo()
                else -> null
            }
            if (state != null) {
                restoreHistory(state)
                return true
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    /**
     * 绘制动态通道状态和轨道摘要。
     *
     * 示例：动态轨道会显示时长、播放方式和关键帧数量。
     * 禁止在渲染阶段修改轨道内容。
     */
    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        recordHistory()
        updateHistoryButtons()
        renderBackground(graphics, mouseX, mouseY, partialTick)
        super.render(graphics, mouseX, mouseY, partialTick)
        val left = width / 2 - 150
        val top = 54
        graphics.drawString(font, title, left, 20, 0xFFFFFF, true)
        graphics.drawString(font, "选择通道", left, top - 12, 0xE0E0E0, true)
        graphics.drawString(font, "位置", left + 12, top + 6, 0xE0E0E0, true)
        graphics.drawString(font, "forward", left + 12, top + 38, 0xE0E0E0, true)
        graphics.drawString(font, trackSummary(TestControllerTrackKind.POSITION), left + 90, top + 22, 0xA8DDA8, true)
        graphics.drawString(font, trackSummary(TestControllerTrackKind.FORWARD), left + 90, top + 54, 0xA8DDA8, true)
    }

    private fun updateButtons() {
        if (!::positionModeButton.isInitialized) return
        positionModeButton.message = Component.literal(positionModeLabel())
        forwardModeButton.message = Component.literal(forwardModeLabel())
        positionEditButton.active = draft.positionDynamic
        forwardEditButton.active = draft.forwardDynamic
    }

    private fun recordHistory() {
        history.record(TestControllerPacketDrafts.snapshotFrom(packet, draft))
    }

    private fun updateHistoryButtons() {
        if (!::undoButton.isInitialized) return
        undoButton.active = history.canUndo
        redoButton.active = history.canRedo
    }

    private fun restoreHistory(snapshot: TestControllerPacketDrafts.TestControllerConfigSnapshot?) {
        if (snapshot == null) return
        val restoredPacket = snapshot.toPacket()
        minecraft?.setScreen(
            TestControllerDynamicScreen(restoredPacket, TestControllerPacketDrafts.draftFrom(restoredPacket), history)
        )
    }

    private fun positionModeLabel(): String {
        return "${TestControllerTrackKind.POSITION.displayName}: ${if (draft.positionDynamic) "动态" else "静态"}"
    }

    private fun forwardModeLabel(): String {
        return "${TestControllerTrackKind.FORWARD.displayName}: ${if (draft.forwardDynamic) "动态" else "静态"}"
    }

    private fun trackSummary(kind: TestControllerTrackKind): String {
        val fallback = if (kind == TestControllerTrackKind.POSITION) {
            Vec3(draft.offsetX, draft.offsetY, draft.offsetZ)
        } else {
            Vec3(draft.forwardX, draft.forwardY, draft.forwardZ)
        }
        val encoded = if (kind == TestControllerTrackKind.POSITION) draft.positionTrack else draft.forwardTrack
        val track = BlockTestAnimationTrackCodec.decode(encoded, fallback)
        return "${track.durationTicks} tick / ${track.playbackMode.displayName} / ${track.keyframes.size} 帧"
    }

    /**
     * 从曲线编辑器返回动态通道选择页。
     *
     * 示例：位置曲线按 Esc 后仍可继续编辑 forward 曲线。
     * 禁止在此提交网络包，草稿仍由动态页持有。
     */
    private fun reopenDynamicScreen() {
        minecraft?.setScreen(TestControllerDynamicScreen(packet, draft, history))
    }

    /**
     * 返回控制器主界面并保留动态草稿。
     *
     * 示例：动态页点击“返回”后可继续修改测试模式。
     * 禁止丢弃尚未保存的轨道字段。
     */
    private fun returnToController() {
        minecraft?.setScreen(TestControllerScreen(TestControllerPacketDrafts.reopenPacket(packet, draft), false, history))
    }
}
