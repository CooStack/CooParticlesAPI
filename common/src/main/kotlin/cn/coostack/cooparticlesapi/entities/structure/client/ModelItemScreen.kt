package cn.coostack.cooparticlesapi.entities.structure.client

import cn.coostack.cooparticlesapi.entities.structure.StructureModelSettings
import cn.coostack.cooparticlesapi.entities.structure.StructureModels
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Checkbox
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.EditBox
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * 手持模型设置子页，背包仅作只读来源，复制槽不移动、扣除或生成背包物品。
 * @property parent 保存几何草稿的主编辑器
 * @property initial 进入子页时的物品设置，取消时不回写
 * @property initialDraft 上次确认的原始输入，允许未完成的数字和名称
 */
internal class ModelItemScreen(
    private val parent: ModelEditorScreen,
    private val initial: StructureModelSettings,
    private val initialDraft: CompoundTag
) : Screen(Component.literal("手持模型配置")) {
    /** 输入文字在窗口重建时保留，包括尚未完成的数值。 */
    private val textValues = initialDraft.getCompound("Fields").let { fields ->
        fields.allKeys.associateWith { fields.getString(it) }.toMutableMap()
    }
    /** 当前窗口的输入控件，重建前将文字写回草稿。 */
    private val fields = linkedMapOf<String, EditBox>()
    /** 食用及放置开关与模拟物品原生能力分别保存。 */
    private var edible = initial.edible
    /** 潜行右键方块是否优先放回结构实体。 */
    private var placeable = initial.placeable
    /** 是否用手动营养值覆盖复制物品的食物组件。 */
    private var overrideFood = initial.itemProperties.overrideFood
    /** 本地独立副本，仅在主页面提交时由服务端校验来源。 */
    private var sample = ItemStack.EMPTY
    /** 只在第一次初始化时解码旧值，清除槽位后不能被缩放窗口恢复。 */
    private var sampleLoaded = false
    /** 清除槽位时也解除旧版 ID 绑定，避免不可见的继承残留。 */
    private var sampleChanged = initialDraft.getBoolean("SampleChanged")
    /** 点击背包后暂存待放入的副本，原背包保持原样。 */
    private var carried = ItemStack.EMPTY
    /** 数值无效时保留子页和输入。 */
    private var error = ""
    /** 内容原始坐标统一减去滚动量，页脚固定不动。 */
    private var scroll = 0
    /** 固定宽度表单的水平起点。 */
    private var left = 0
    /** 背包格子当前纵坐标，包含滚动偏移。 */
    private var inventoryY = 0
    /** 模拟槽当前纵坐标，包含滚动偏移。 */
    private var sampleY = 0
    /** 可滚动控件及其未滚动纵坐标，不包含固定页脚。 */
    private val contentWidgets = mutableListOf<Pair<AbstractWidget, Int>>()
    /** 随可食用开关整组隐藏的控件，保留实例以保留未完成输入。 */
    private val foodWidgets = mutableListOf<AbstractWidget>()
    /** 覆盖开关既属于食品组，也需要独立更新可编辑状态。 */
    private lateinit var foodOverride: Checkbox
    /** 子页撤销记录独立于主页面，确定后主页面将整组设置视为一次修改。 */
    private val history = ModelEditHistory<CompoundTag>()

    override fun init() {
        fields.forEach { (key, field) -> textValues[key] = field.value }
        fields.clear()
        contentWidgets.clear()
        foodWidgets.clear()
        left = (width - 286) / 2
        if (!sampleLoaded) {
            sample = minecraft?.level?.let { runCatching { initial.itemProperties.simulation(it.registryAccess()) }.getOrNull() }
                ?: ItemStack.EMPTY
            sampleLoaded = true
        }
        val properties = initial.itemProperties
        field("name", "物品名称", initial.itemName, 34, 64)
        field("event", "事件 ID", properties.eventId, 60, 256)
        addRenderableWidget(Checkbox.builder(Component.literal("可食用"), font).pos(left, 86 - scroll)
            .selected(edible).onValueChange { _, checked -> edible = checked; refresh() }.build())
        addRenderableWidget(Checkbox.builder(Component.literal("可重新放置"), font).pos(left + 130, 86 - scroll)
            .selected(placeable).onValueChange { _, checked -> placeable = checked }.build())
        foodOverride = addRenderableWidget(Checkbox.builder(Component.literal("覆盖食物数值"), font).pos(left, 112 - scroll)
            .selected(overrideFood).onValueChange { _, checked -> overrideFood = checked; refresh() }.build())
        number("nutrition", "恢复饱食度", properties.nutrition.toString(), 140, ModelNumberStepper(0.0, 1000.0, 1.0))
        number("saturation", "恢复饱和度", properties.saturation.toString(), 166, ModelNumberStepper(0.0, 1000.0, 0.5))
        number("seconds", "食用时长（秒）", properties.eatSeconds.toString(), 192, ModelNumberStepper(0.05, 120.0, 0.05))
        foodWidgets.add(foodOverride)
        listOf("nutrition", "saturation", "seconds").forEach { foodWidgets.add(fields.getValue(it)) }
        children().filterIsInstance<AbstractWidget>().forEach { contentWidgets.add(it to it.y + scroll) }
        addRenderableWidget(Button.builder(Component.literal("确定")) { accept() }
            .bounds(left, height - 26, 138, 20).build())
        addRenderableWidget(Button.builder(Component.literal("取消")) { onClose() }
            .bounds(left + 148, height - 26, 138, 20).build())
        refresh()
        if (history.current == null) history.record(captureState())
    }

    /** 保存未完成的输入、复制槽及光标副本，不持有真实背包栈。 */
    private fun captureState(): CompoundTag = CompoundTag().apply {
        put("Fields", CompoundTag().apply { fields.forEach { (key, field) -> putString(key, field.value) } })
        putBoolean("Edible", edible)
        putBoolean("Placeable", placeable)
        putBoolean("OverrideFood", overrideFood)
        putBoolean("SampleChanged", sampleChanged)
        minecraft?.level?.registryAccess()?.let {
            put("Sample", sample.saveOptional(it))
            put("Carried", carried.saveOptional(it))
        }
    }

    /** 撤销时仅恢复本地副本，并按照恢复后的食用开关重新计算布局。 */
    private fun restoreState(state: CompoundTag) {
        val focusedKey = fields.entries.firstOrNull { it.value === focused }?.key
        val registries = minecraft?.level?.registryAccess() ?: return
        textValues.clear()
        val savedFields = state.getCompound("Fields")
        savedFields.allKeys.forEach { textValues[it] = savedFields.getString(it) }
        fields.clear()
        edible = state.getBoolean("Edible")
        placeable = state.getBoolean("Placeable")
        overrideFood = state.getBoolean("OverrideFood")
        sampleChanged = state.getBoolean("SampleChanged")
        val savedSample = state.getCompound("Sample")
        val savedCarried = state.getCompound("Carried")
        sample = if (savedSample.isEmpty) ItemStack.EMPTY else ItemStack.parse(registries, savedSample).orElse(ItemStack.EMPTY)
        carried = if (savedCarried.isEmpty) ItemStack.EMPTY else ItemStack.parse(registries, savedCarried).orElse(ItemStack.EMPTY)
        error = ""
        rebuildWidgets()
        focusedKey?.let { fields[it]?.takeIf { field -> field.visible }?.let { field -> setFocused(field) } }
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        val handled = super.charTyped(chr, modifiers)
        history.record(captureState())
        return handled
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (hasControlDown() && (keyCode == GLFW.GLFW_KEY_Z || keyCode == GLFW.GLFW_KEY_Y)) {
            val state = if (keyCode == GLFW.GLFW_KEY_Y || hasShiftDown()) history.redo() else history.undo()
            state?.let { restoreState(it) }
            return true
        }
        val handled = super.keyPressed(keyCode, scanCode, modifiers)
        history.record(captureState())
        return handled
    }

    /** 普通文字输入框与数值控件使用相同的标签列宽。 */
    private fun field(key: String, label: String, default: String, y: Int, max: Int) {
        val field = EditBox(font, left + 112, y - scroll, 174, 20, Component.literal(label))
        field.setMaxLength(max)
        field.value = textValues[key] ?: default
        fields[key] = addRenderableWidget(field)
    }

    /** 数值箭头与文字输入共用验证和草稿保留规则。 */
    private fun number(key: String, label: String, default: String, y: Int, stepper: ModelNumberStepper) {
        val field = ModelNumberField(font, left + 112, y - scroll, 174, 20, Component.literal(label), stepper)
        field.setMaxLength(32)
        field.value = textValues[key] ?: default
        fields[key] = addRenderableWidget(field)
    }

    /** 未勾选覆盖时继承模拟食物的营养，避免默认零值覆盖目标物品。 */
    private fun refresh() {
        val active = edible && (overrideFood || sample.isEmpty || !sample.has(DataComponents.FOOD))
        listOf("nutrition", "saturation", "seconds").forEach { fields[it]?.active = active }
        foodOverride.active = edible
        layoutContent()
    }

    /** 折叠食品组后回收整组高度，并同步滚动范围、控件可见性和物品槽命中位置。 */
    private fun layoutContent() {
        val sampleTop = if (edible) 220 else 112
        val contentBottom = sampleTop + 130
        val limit = height - 44
        scroll = scroll.coerceIn(0, (contentBottom - limit).coerceAtLeast(0))
        sampleY = sampleTop - scroll
        inventoryY = sampleY + 40
        contentWidgets.forEach { (widget, originalY) ->
            widget.y = originalY - scroll
            widget.visible = widget.y >= 28 && widget.y + widget.height <= limit &&
                (edible || widget !in foodWidgets)
            if (!widget.visible && widget.isFocused) setFocused(null)
        }
    }

    /** 确认只回写草稿；无效数值保留原文字，并标记为不能转换的设置。 */
    private fun accept() {
        val manual = fields.getValue("nutrition").active
        val nutrition = if (manual) fields.getValue("nutrition").value.toDoubleOrNull() else initial.itemProperties.nutrition.toDouble()
        val saturation = if (manual) fields.getValue("saturation").value.toFloatOrNull() else initial.itemProperties.saturation
        val seconds = if (manual) fields.getValue("seconds").value.toFloatOrNull() else initial.itemProperties.eatSeconds
        val registries = minecraft?.level?.registryAccess() ?: return
        val properties = initial.itemProperties.copy(
            eventId = fields.getValue("event").value.trim(),
            nutrition = if (nutrition != null && nutrition.isFinite() && nutrition == nutrition.toInt().toDouble()) nutrition.toInt() else -1,
            saturation = saturation ?: Float.NaN, eatSeconds = seconds ?: Float.NaN,
            overrideFood = overrideFood,
            boundItem = if (sample.isEmpty && !sampleChanged) initial.itemProperties.boundItem else "",
            simulatedStack = if (sample.isEmpty) "" else sample.copyWithCount(1).save(registries).toString()
        )
        val configured = initial.copy(itemName = fields.getValue("name").value.trim(), edible = edible,
            placeable = placeable, itemProperties = properties)
        parent.acceptItemSettings(configured, captureState())
        onClose()
    }

    /** 背包格子索引只覆盖主背包和快捷栏，不允许碰触真实容器的光标栈。 */
    private fun inventorySlot(x: Double, y: Double): Int? {
        if (x < left + 53 || x >= left + 233 || y < inventoryY || y >= inventoryY + 80 ||
            y < 28 || y >= height - 44) return null
        val column = ((x - left - 53) / 20).toInt()
        val row = ((y - inventoryY) / 20).toInt()
        return if (row == 3) column else 9 + row * 9 + column
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && mouseY >= 28 && mouseY < height - 44) {
            if (mouseX >= left + 112 && mouseX < left + 132 && mouseY >= sampleY && mouseY < sampleY + 20) {
                if (!sample.isEmpty) {
                    sample = ItemStack.EMPTY
                    carried = ItemStack.EMPTY
                } else if (!carried.isEmpty) {
                    sample = carried.copyWithCount(1)
                    carried = ItemStack.EMPTY
                }
                sampleChanged = true
                refresh()
                history.record(captureState())
                return true
            }
            inventorySlot(mouseX, mouseY)?.let { slot ->
                val stack = minecraft?.player?.inventory?.getItem(slot) ?: return true
                if (stack.isEmpty) carried = ItemStack.EMPTY
                else if (stack.`is`(StructureModels.HELD_MODEL) ||
                    stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.contains("ModelData") == true) {
                    error = "不能模拟另一个模型物品"
                } else {
                    carried = stack.copyWithCount(1)
                    error = ""
                }
                history.record(captureState())
                return true
            }
        }
        val handled = super.mouseClicked(mouseX, mouseY, button)
        history.record(captureState())
        return handled
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && !carried.isEmpty && sample.isEmpty && mouseY >= 28 && mouseY < height - 44 &&
            mouseX >= left + 112 && mouseX < left + 132 && mouseY >= sampleY && mouseY < sampleY + 20) {
            sample = carried.copyWithCount(1)
            sampleChanged = true
            carried = ItemStack.EMPTY
            refresh()
            history.record(captureState())
            return true
        }
        val handled = super.mouseReleased(mouseX, mouseY, button)
        history.record(captureState())
        return handled
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        scroll -= (verticalAmount * 24).toInt()
        layoutContent()
        return true
    }

    override fun setFocused(focused: GuiEventListener?) {
        if (focused !== this.focused) super.setFocused(focused)
    }

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)
        context.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF)
        context.enableScissor(left, 28, left + 286, height - 44)
        val labels = listOf("物品名称" to 40, "事件 ID" to 66) +
            if (edible) listOf("恢复饱食度" to 146, "恢复饱和度" to 172, "食用时长（秒）" to 198) else emptyList()
        labels.forEach { (label, y) -> context.drawString(font, label, left, y - scroll, 0xDDDDDD) }
        context.drawString(font, "模拟物品", left, sampleY + 6, 0xDDDDDD)
        context.drawString(font, "背包", left, inventoryY - 14, 0xDDDDDD)
        context.fill(left + 111, sampleY - 1, left + 133, sampleY + 21, 0xFFA0A0A0.toInt())
        context.fill(left + 112, sampleY, left + 132, sampleY + 20, 0xFF111111.toInt())
        if (!sample.isEmpty) {
            context.renderItem(sample, left + 114, sampleY + 2)
            context.drawString(font, font.plainSubstrByWidth(sample.hoverName.string, 144),
                left + 140, sampleY + 6, 0xDDDDDD)
        }
        repeat(36) { index ->
            val slot = if (index >= 27) index - 27 else index + 9
            val x = left + 53 + index % 9 * 20
            val y = inventoryY + index / 9 * 20
            context.fill(x, y, x + 19, y + 19, 0xFF777777.toInt())
            context.fill(x + 1, y + 1, x + 18, y + 18, 0xFF202020.toInt())
            val stack = minecraft?.player?.inventory?.getItem(slot) ?: ItemStack.EMPTY
            context.renderItem(stack, x + 1, y + 1)
            context.renderItemDecorations(font, stack, x + 1, y + 1)
        }
        context.disableScissor()
        if (error.isNotEmpty()) context.drawString(font, font.plainSubstrByWidth(error, 286),
            left, height - 40, 0xFF9999)
        if (!carried.isEmpty) {
            context.pose().pushPose()
            context.pose().translate(0.0, 0.0, 500.0)
            context.renderItem(carried, mouseX - 8, mouseY - 8)
            context.pose().popPose()
        } else {
            val hovered = inventorySlot(mouseX.toDouble(), mouseY.toDouble())
                ?.let { minecraft?.player?.inventory?.getItem(it) }
            if (hovered != null && !hovered.isEmpty) context.renderTooltip(font, hovered, mouseX, mouseY)
        }
    }

    override fun onClose() { minecraft?.setScreen(parent) }
    override fun isPauseScreen(): Boolean = false
}
