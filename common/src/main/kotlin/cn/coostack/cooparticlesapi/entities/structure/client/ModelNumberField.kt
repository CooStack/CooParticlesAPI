package cn.coostack.cooparticlesapi.entities.structure.client

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.abs

/**
 * 保留原版文字编辑的数值输入框，右侧上下箭头支持点击步进和按住竖直拖动。
 * @property stepper 当前参数的数值范围和步长
 */
class ModelNumberField(
    renderer: Font, x: Int, y: Int, width: Int, height: Int, label: Component,
    private val stepper: ModelNumberStepper
) : EditBox(renderer, x, y, width, height, label) {
    /** 箭头列宽度，文字裁剪、绘制与点击共用，单位为界面像素。 */
    private val arrowWidth = 12
    /** 按下箭头时的初始数值；空值表示没有数值拖动。 */
    private var pressedValue: Double? = null
    /** 按下时的纵坐标，用于区分点击和拖动。 */
    private var pressedY = 0.0
    /** 本次按下的箭头方向，上箭头为正。 */
    private var pressedDirection = 1.0
    /** 是否已经越过拖动阈值；拖动结束不能额外执行一次点击步进。 */
    private var scrubbed = false
    /** 上箭头的悬停与辅助朗读说明。 */
    private val increaseTooltip = Tooltip.create(Component.literal("增大；按住上下拖动"))
    /** 下箭头的悬停与辅助朗读说明。 */
    private val decreaseTooltip = Tooltip.create(Component.literal("减小；按住上下拖动"))

    override fun getInnerWidth(): Int = (super.getInnerWidth() - arrowWidth).coerceAtLeast(1)

    override fun setEditable(editable: Boolean) {
        super.setEditable(editable)
        active = editable
    }

    override fun setFocused(focused: Boolean) {
        if (!focused) pressedValue = null
        super.setFocused(focused)
    }

    /** 文字区仍由原版处理光标；箭头区延迟到释放时步进，以免拖动起手多跳一次。 */
    override fun onClick(mouseX: Double, mouseY: Double) {
        pressedValue = null
        if (mouseX >= x + width - arrowWidth) {
            pressedValue = value.toDoubleOrNull()?.takeIf { it.isFinite() }
            pressedY = mouseY
            pressedDirection = if (mouseY < y + height / 2) 1.0 else -1.0
            scrubbed = false
        } else {
            super.onClick(mouseX, mouseY)
        }
    }

    /** 越过三像素阈值后，每四像素对应一个步长；移出控件仍保持拖动捕获。 */
    override fun onDrag(mouseX: Double, mouseY: Double, deltaX: Double, deltaY: Double) {
        val initial = pressedValue
        if (initial == null) {
            super.onDrag(mouseX, mouseY, deltaX, deltaY)
            return
        }
        val distance = pressedY - mouseY
        if (abs(distance) >= 3.0) scrubbed = true
        if (scrubbed) changeValue(initial, distance / 4.0)
    }

    override fun onRelease(mouseX: Double, mouseY: Double) {
        pressedValue?.let { if (!scrubbed) changeValue(it, pressedDirection) }
        pressedValue = null
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (active && isFocused && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN)) {
            value.toDoubleOrNull()?.takeIf { it.isFinite() }?.let {
                changeValue(it, if (keyCode == GLFW.GLFW_KEY_UP) 1.0 else -1.0)
            }
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    /** 使用与键盘一致的修饰键，不把控件格式化应用到手动输入的原始文字。 */
    private fun changeValue(initial: Double, steps: Double) {
        value = stepper.adjust(initial, steps, Screen.hasShiftDown(), Screen.hasControlDown()).toString()
        moveCursorToEnd(false)
    }

    /** 文字与箭头分区绘制，长数值不会盖住箭头，禁用状态仍显示控件边界。 */
    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val left = x + width - arrowWidth
        context.enableScissor(x, y, left, y + height)
        super.renderWidget(context, mouseX, mouseY, delta)
        context.disableScissor()
        val middle = y + height / 2
        val hovered = active && mouseX >= left && mouseX < x + width && mouseY >= y && mouseY < y + height
        context.fill(left, y, x + width, y + height, if (isFocused) 0xFFFFFFFF.toInt() else 0xFF909090.toInt())
        context.fill(left + 1, y + 1, x + width - 1, middle, if (hovered && mouseY < middle) 0xFF505050.toInt() else 0xFF252525.toInt())
        context.fill(left + 1, middle + 1, x + width - 1, y + height - 1, if (hovered && mouseY >= middle) 0xFF505050.toInt() else 0xFF252525.toInt())
        val center = left + arrowWidth / 2
        val color = if (active) 0xFFE0E0E0.toInt() else 0xFF606060.toInt()
        for (row in 0..2) {
            context.fill(center - row, y + 3 + row, center + row + 1, y + 4 + row, color)
            context.fill(center - row, middle + 6 - row, center + row + 1, middle + 7 - row, color)
        }
        tooltip = if (!hovered) null else if (mouseY < middle) increaseTooltip else decreaseTooltip
    }
}
