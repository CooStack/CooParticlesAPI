package cn.coostack.cooparticlesapi.entities.structure.client

import cn.coostack.cooparticlesapi.entities.structure.ModelEditPayload
import cn.coostack.cooparticlesapi.entities.structure.StructureModelSettings
import cn.coostack.cooparticlesapi.entities.structure.StructureSnapshot
import cn.coostack.cooparticlesapi.network.packet.api.CooClientPacketManager
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Checkbox
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.EditBox
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.lwjgl.glfw.GLFW
import java.util.UUID

/** 结构模型设置窗口；只提交编辑意图，库存使用服务端原版容器。
 * @property data 服务端提供的目标及目录，客户端不得替换结构内容
 */
class ModelEditorScreen(private val data: CompoundTag) : Screen(Component.literal("结构模型编辑器")) {
    /** 初始方块可设置模型属性，已有实体模式必须由编辑器打开。 */
    private val editing = data.hasUUID("Entity")
    /** 原始草稿独立于服务端已验证的模型数据，允许字段尚未填完。 */
    private val savedDraft = data.getCompound("EditorDraft")
    /** 输入草稿在子窗口返回和窗口缩放时保留。 */
    private var draft = StructureModelSettings.fromNbt(if (savedDraft.contains("Draft")) savedDraft.getCompound("Draft") else data.getCompound("Settings"))
    /** 首次转换时未修改的支点由服务端按所选结构补全。 */
    private var explicitPivot = if (savedDraft.contains("ExplicitPivot")) savedDraft.getBoolean("ExplicitPivot")
        else editing || data.getBoolean("SavedBlock")
    /** 当前选择的结构标识。 */
    private var name = if (savedDraft.contains("Name")) savedDraft.getString("Name") else data.getString("Name")
    /** 尚未提交的动画开关。 */
    private var deathAnimation = if (savedDraft.contains("DeathAnimation")) savedDraft.getBoolean("DeathAnimation") else draft.deathAnimation
    /** 拾取开关与名称草稿独立保存，名称未填完时仍可调整模型中心。 */
    private var pickable = if (savedDraft.contains("Pickable")) savedDraft.getBoolean("Pickable") else draft.pickable
    /** 手持子页原始输入，与主页面一起保存和撤销。 */
    private var itemDraft = savedDraft.getCompound("ItemDraft").copy()
    /** 子菜单保存的手持物品配置，不与未完成的变换文字混合。 */
    private var itemSettings = if (savedDraft.contains("ItemSettings")) StructureModelSettings.fromNbt(savedDraft.getCompound("ItemSettings")) else draft
    /** 物品的进食与重新放置是相互独立的能力。 */
    private var edible = itemSettings.edible
    /** 默认关闭重新放置，避免仅作为道具的模型变回实体。 */
    private var placeable = itemSettings.placeable
    /** 可拾取开启时才显示的子菜单入口。 */
    private lateinit var itemButton: Button
    /** 生命、旋转、偏移、缩放和中心的十三个数值输入控件。 */
    private val values = mutableListOf<ModelNumberField>()
    /** 重建窗口前暂存原始文字，包括尚未输入完整的数值。 */
    private var rawValues: List<String>? = if (savedDraft.contains("Values")) {
        savedDraft.getList("Values", Tag.TAG_STRING.toInt()).let { list -> (0 until list.size).map { list.getString(it) } }
    } else null
    /** 结构 ID 输入框，在首次初始化时创建。 */
    private lateinit var nameField: EditBox
    /** 可见的校验错误；保存失败不会丢弃输入。 */
    private var error = ""
    /** 保存请求等待服务端回执时禁止重复提交。 */
    private var pending = false
    /** 防止已关闭窗口发出的预览回包打开另一个编辑会话。 */
    private var previewRequest = UUID.randomUUID()
    /** 内嵌视口不会打开另一个 Screen。 */
    private val previewPane = ModelPreviewPane()
    /** 输入停止若干客户端刻后才请求快照，避免每个字符都加载模板。 */
    private var previewDelay = 0
    /** 已显示的结构标识；空值使子页返回后重新请求丢失的回包。 */
    private var previewName: String? = null
    /** 左侧表单在小窗口中滚动，底部保存按钮始终固定。 */
    private val formWidgets = mutableListOf<Pair<AbstractWidget, Int>>()
    /** 左表单当前滚动像素数。 */
    private var formScroll = 0
    /** 左表单未滚动时的内容底边。 */
    private var formBottom = 0
    /** 表单宽度与行高同时用于布局、标签和命中测试。 */
    private var formWidth = 310
    /** 第一行数字输入框的固定纵坐标。 */
    private val numberTop = 72
    /** 窄表单把标签放到数值上方，因此需要更大的行距。 */
    private var numberRowHeight = 22
    /** 从服务端目录建立的补全候选及选择状态。 */
    private val completion = ModelNameSuggestions(data.getList("Names", Tag.TAG_STRING.toInt()).let { list ->
        (0 until list.size).map { list.getString(it) }
    })
    /** Escape 或点击其他控件后隐藏候选，继续输入时重新打开。 */
    private var suggestionsDismissed = false
    /** 补全写回时不重建候选组，以支持连续 Tab 循环。 */
    private var applyingCompletion = false
    /** 下拉窗口最多显示的候选数，绘制与鼠标命中共用。 */
    private val visibleRows = 6
    /** 本次主页面编辑的独立快照，不包含服务器真实库存。 */
    private val history = ModelEditHistory<CompoundTag>()

    /** 按初次选择或编辑模式构建控件，并保留未提交的草稿。 */
    override fun init() {
        if (values.isNotEmpty()) rawValues = values.map { it.value }
        if (::nameField.isInitialized) name = nameField.value
        values.clear()
        formWidgets.clear()
        formWidth = ((width - 24) * 0.55).toInt().coerceAtLeast(164).coerceAtMost(width - 116)
        numberRowHeight = if (formWidth >= 300) 22 else 34
        val x = 8
        val y = 14
        nameField = form(EditBox(font, x, y + 28, formWidth - 60, 20, Component.literal("结构 ID")))
        nameField.setMaxLength(256)
        nameField.value = name
        completion.update(name)
        nameField.setResponder { input ->
            if (!applyingCompletion) {
                completion.update(input)
                suggestionsDismissed = false
            }
            updateGhostText()
            schedulePreview()
        }
        form(Button.builder(Component.literal("选择…")) {
            name = nameField.value
            val list = data.getList("Names", Tag.TAG_STRING.toInt())
            minecraft?.setScreen(ModelStructureScreen(this, (0 until list.size).map { list.getString(it) }))
        }.bounds(x + formWidth - 56, y + 28, 56, 20).build())
        val defaults = listOf(draft.health.toString()) + draft.rotation.map { it.toString() } +
            draft.offset.map { it.toString() } + draft.scale.map { it.toString() } + draft.pivot.map { it.toString() }
        val labels = listOf("最大生命", "旋转", "偏移", "缩放", "模型中心")
        for (index in defaults.indices) {
            val row = if (index == 0) 0 else (index - 1) / 3 + 1
            val column = if (index == 0) 0 else (index - 1) % 3
            val stepper = when (row) {
                0 -> ModelNumberStepper(0.1, 1024.0, 1.0)
                1 -> ModelNumberStepper(-360.0, 360.0, 1.0)
                2 -> ModelNumberStepper(-16.0, 16.0, 0.1)
                3 -> ModelNumberStepper(0.05, 16.0, 0.05)
                else -> ModelNumberStepper(-48.0, 48.0, 0.1)
            }
            val label = labels[row] + if (row == 0) "" else " ${"XYZ"[column]}"
            val labelWidth = if (formWidth >= 300) 88 else 0
            val fieldWidth = (formWidth - labelWidth - 8) / 3
            val field = ModelNumberField(font, x + labelWidth + column * (fieldWidth + 4),
                numberTop + row * numberRowHeight, fieldWidth, 20, Component.literal(label), stepper)
            field.setMaxLength(32)
            field.value = rawValues?.getOrNull(index) ?: defaults[index]
            values.add(form(field))
        }
        // 所有控件初始化后才监听，避免逐个写回草稿时将未填完的坐标误当成中心修改。
        values.forEachIndexed { index, field ->
            field.setResponder {
                if (index >= 10) explicitPivot = true
                updateDraft()
            }
        }
        val controlsY = numberTop + 5 * numberRowHeight + 4
        form(Checkbox.builder(Component.literal("死亡动画"), font)
            .pos(x, controlsY).selected(deathAnimation)
            .onValueChange { _, checked -> deathAnimation = checked }.build())
        form(Checkbox.builder(Component.literal("可拾取"), font)
            .pos(x, controlsY + 26).selected(pickable)
            .onValueChange { _, checked ->
                pickable = checked
                layoutForm()
            }.build())
        form(Button.builder(Component.literal("掉落物…")) {
            submit("drops")
        }.bounds(x + formWidth - 76, controlsY, 76, 20).build())
        itemButton = form(Button.builder(Component.literal("手持模型配置…")) {
            minecraft?.setScreen(ModelItemScreen(this, itemSettings.copy(pickable = true), itemDraft))
        }.bounds(x + formWidth - 100, controlsY + 26, 100, 20).build())
        if (!editing) {
            form(Button.builder(Component.literal("从物品获取")) { submit("import") }
                .bounds(x, controlsY + 52, 100, 20).build()).active = !data.getBoolean("SavedBlock")
        }
        formBottom = controlsY + if (editing) 50 else 76
        val saveWidth = if (editing) formWidth - 54 else (formWidth - 62) / 2
        addRenderableWidget(Button.builder(Component.literal(if (editing) "保存并恢复生命" else "保存配置")) {
            if (!pending) submit(if (editing) "apply" else "save")
        }.bounds(x, height - 28, saveWidth, 20).build())
        if (!editing) {
            addRenderableWidget(Button.builder(Component.literal("转换为实体")) { if (!pending) submit("apply") }
                .bounds(x + saveWidth + 4, height - 28, formWidth - saveWidth - 58, 20).build())
        }
        addRenderableWidget(Button.builder(Component.literal("取消")) { onClose() }.bounds(x + formWidth - 50, height - 28, 50, 20).build())
        previewPane.left = x + formWidth + 8
        previewPane.top = 4
        previewPane.right = width - 8
        previewPane.bottom = height - 36
        val previewWidth = previewPane.right - previewPane.left
        addRenderableWidget(Button.builder(Component.literal("适应视图")) { previewPane.fit() }
            .bounds(previewPane.left, height - 28, previewWidth.coerceAtMost(72), 20).build())
        if (previewWidth >= 176) {
            addRenderableWidget(Checkbox.builder(Component.literal("坐标轴"), font)
                .pos(previewPane.left + 78, height - 28).selected(previewPane.axes)
                .onValueChange { _, checked -> previewPane.axes = checked }.build())
        }
        if (previewWidth >= 252) {
            addRenderableWidget(Checkbox.builder(Component.literal("碰撞框"), font)
                .pos(previewPane.left + 154, height - 28).selected(previewPane.collision)
                .onValueChange { _, checked -> previewPane.collision = checked }.build())
        }
        layoutForm()
        if (pending) formWidgets.forEach { (widget, _) -> widget.active = false }
        if (previewName == null) schedulePreview()
        if (history.current == null) history.record(captureState())
    }

    /** 保存原始文字及有效变换，未完成数字和中心补偿也能精确撤销。 */
    private fun captureState(): CompoundTag = CompoundTag().apply {
        putString("Name", nameField.value)
        put("Values", ListTag().apply { values.forEach { add(StringTag.valueOf(it.value)) } })
        put("Draft", draft.toNbt())
        put("ItemSettings", itemSettings.toNbt())
        put("ItemDraft", itemDraft.copy())
        putBoolean("ExplicitPivot", explicitPivot)
        putBoolean("DeathAnimation", deathAnimation)
        putBoolean("Pickable", pickable)
    }

    /** 恢复本地草稿并重新请求匹配的快照，绝不撤回已提交的保存或物品转移。 */
    private fun restoreState(state: CompoundTag) {
        val focusedIndex = values.indexOf(focused)
        val focusedName = focused === nameField
        draft = StructureModelSettings.fromNbt(state.getCompound("Draft"))
        itemSettings = StructureModelSettings.fromNbt(state.getCompound("ItemSettings"))
        itemDraft = state.getCompound("ItemDraft").copy()
        edible = itemSettings.edible
        placeable = itemSettings.placeable
        explicitPivot = state.getBoolean("ExplicitPivot")
        deathAnimation = state.getBoolean("DeathAnimation")
        pickable = state.getBoolean("Pickable")
        nameField.setResponder {}
        nameField.value = state.getString("Name")
        val raw = state.getList("Values", Tag.TAG_STRING.toInt())
        rawValues = (0 until raw.size).map { raw.getString(it) }
        values.clear()
        error = ""
        schedulePreview()
        rebuildWidgets()
        if (focusedName) setFocused(nameField)
        else if (focusedIndex >= 0) setFocused(values[focusedIndex])
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        val handled = super.charTyped(chr, modifiers)
        if (!pending) history.record(captureState())
        return handled
    }

    /** 注册可滚动控件并保留其未滚动的纵坐标。 */
    private fun <T : AbstractWidget> form(widget: T): T {
        formWidgets.add(widget to widget.y)
        return addRenderableWidget(widget)
    }

    /** 只显示完整位于内容区内的控件，避免裁剪后仍能点击隐藏字段。 */
    private fun layoutForm() {
        formScroll = formScroll.coerceIn(0, (formBottom - (height - 48)).coerceAtLeast(0))
        formWidgets.forEach { (widget, originalY) ->
            widget.y = originalY - formScroll
            widget.visible = widget.y >= 28 && widget.y + widget.height <= height - 48 &&
                (widget !== itemButton || pickable)
            if (!widget.visible && widget.isFocused) setFocused(null)
        }
    }

    /** 保留手持设置；变换数据仍以主页面的输入框为准。 */
    internal fun acceptItemSettings(settings: StructureModelSettings, raw: CompoundTag) {
        itemSettings = settings
        itemDraft = raw.copy()
        edible = settings.edible
        placeable = settings.placeable
        history.record(captureState())
    }

    /** 结构输入一旦改变立即清空旧模型，并使未返回的快照失效。 */
    private fun schedulePreview() {
        previewPane.clear()
        previewName = null
        previewRequest = UUID.randomUUID()
        previewDelay = 8
    }

    override fun tick() {
        super.tick()
        if (previewDelay > 0 && --previewDelay == 0 && !pending) requestPreview()
    }

    /** 预览只提交几何草稿，未填写的物品名不会阻断模型显示。 */
    private fun requestPreview() {
        val input = nameField.value.trim()
        if (input.isEmpty()) return
        val id = ResourceLocation.tryParse(input) ?: return
        val request = CompoundTag()
        request.putLong("Pos", data.getLong("Pos"))
        if (editing) request.putUUID("Entity", data.getUUID("Entity"))
        request.putString("Action", "preview")
        request.putString("Name", id.toString())
        request.putUUID("PreviewRequest", previewRequest)
        request.put("Settings", draft.copy(pickable = false).toNbt())
        request.putBoolean("ExplicitPivot", explicitPivot)
        CooClientPacketManager.sendTo(ModelEditPayload(request))
    }

    /** 当前输入框拥有焦点时显示候选，避免抢占数值输入框的按键。 */
    private fun showingSuggestions(): Boolean = nameField.visible && nameField.isFocused && !suggestionsDismissed && completion.matches.isNotEmpty()

    /** 返回保证选中项可见的首行下标。 */
    private fun firstSuggestion(): Int = (completion.selected - visibleRows + 1).coerceAtLeast(0)

    /** 输入框中的灰色后缀仅在完整候选以当前输入开头时显示。 */
    private fun updateGhostText() {
        val candidate = if (showingSuggestions()) completion.matches.getOrNull(completion.selected) else null
        nameField.setSuggestion(candidate?.takeIf { it.startsWith(nameField.value) }?.removePrefix(nameField.value))
    }

    /** 补全替换整个结构 ID，并将光标放到末尾，不改变其他参数。 */
    private fun acceptSuggestion(value: String?) {
        if (value == null) return
        applyingCompletion = true
        nameField.value = value
        applyingCompletion = false
        nameField.moveCursorToEnd(false)
        updateGhostText()
        history.record(captureState())
    }

    /** 在控件处理 Tab 焦点切换之前处理补全，Escape 首次只关闭候选。 */
    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (hasControlDown() && (keyCode == GLFW.GLFW_KEY_Z || keyCode == GLFW.GLFW_KEY_Y)) {
            if (!pending) {
                val state = if (keyCode == GLFW.GLFW_KEY_Y || hasShiftDown()) history.redo() else history.undo()
                state?.let { restoreState(it) }
            }
            return true
        }
        if (nameField.isFocused && suggestionsDismissed &&
            (keyCode == GLFW.GLFW_KEY_TAB || keyCode == GLFW.GLFW_KEY_DOWN)) suggestionsDismissed = false
        if (showingSuggestions()) {
            when (keyCode) {
                GLFW.GLFW_KEY_TAB -> { acceptSuggestion(completion.complete(hasShiftDown())); return true }
                GLFW.GLFW_KEY_DOWN -> { completion.move(1); updateGhostText(); return true }
                GLFW.GLFW_KEY_UP -> { completion.move(-1); updateGhostText(); return true }
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    acceptSuggestion(completion.choose(completion.selected))
                    suggestionsDismissed = true
                    updateGhostText()
                    return true
                }
                GLFW.GLFW_KEY_ESCAPE -> { suggestionsDismissed = true; updateGhostText(); return true }
            }
        }
        val handled = super.keyPressed(keyCode, scanCode, modifiers)
        if (!pending) history.record(captureState())
        return handled
    }

    /** 下拉列表先接收点击，不能让点击穿透到后面的生命和旋转输入框。 */
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val row = suggestionRow(mouseX, mouseY)
        if (row != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            acceptSuggestion(completion.choose(row))
            suggestionsDismissed = true
            updateGhostText()
            return true
        }
        if (previewPane.click(mouseX, mouseY, button)) {
            setFocused(null)
            return true
        }
        suggestionsDismissed = !nameField.isMouseOver(mouseX, mouseY)
        val handled = super.mouseClicked(mouseX, mouseY, button)
        updateGhostText()
        if (!pending) history.record(captureState())
        return handled
    }

    /** 原版会在重复点击时先清除同一控件的焦点；数值箭头刚记录的拖动状态必须保留。 */
    override fun setFocused(focused: GuiEventListener?) {
        if (focused === this.focused) return
        super.setFocused(focused)
    }

    /** 只有光标位于候选区域内时滚动选项，不拦截其他区域的滚轮。 */
    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (suggestionRow(mouseX, mouseY) != null && verticalAmount != 0.0) {
            completion.move(if (verticalAmount > 0.0) -1 else 1)
            updateGhostText()
            return true
        }
        if (previewPane.scroll(mouseX, mouseY, verticalAmount)) return true
        if (mouseX >= 8 && mouseX < 8 + formWidth && mouseY >= 28 && mouseY < height - 48) {
            suggestionsDismissed = true
            formScroll -= (verticalAmount * 22).toInt()
            layoutForm()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean =
        previewPane.drag(button, deltaX, deltaY) || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val handled = previewPane.release(button) || super.mouseReleased(mouseX, mouseY, button)
        if (!pending) history.record(captureState())
        return handled
    }

    /** 返回鼠标命中的候选下标；窗口外或未打开时返回空。 */
    private fun suggestionRow(mouseX: Double, mouseY: Double): Int? {
        if (!showingSuggestions()) return null
        val x = nameField.x
        val y = nameField.y + 22
        val count = (completion.matches.size - firstSuggestion()).coerceAtMost(visibleRows)
        if (mouseX < x || mouseX >= x + nameField.width || mouseY < y || mouseY >= y + count * 12) return null
        return firstSuggestion() + ((mouseY - y) / 12).toInt()
    }

    /** 以原版指令风格的深色下拉框覆盖控件，黄色文字表示当前键盘选项。 */
    private fun renderSuggestions(context: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (!showingSuggestions()) return
        val x = nameField.x
        val y = nameField.y + 22
        val first = firstSuggestion()
        val count = (completion.matches.size - first).coerceAtMost(visibleRows)
        context.pose().pushPose()
        context.pose().translate(0.0, 0.0, 400.0)
        context.fill(x, y, x + nameField.width, y + count * 12, 0xFF101010.toInt())
        val hovered = suggestionRow(mouseX.toDouble(), mouseY.toDouble())
        repeat(count) { row ->
            val index = first + row
            if (index == completion.selected || index == hovered) context.fill(x, y + row * 12, x + nameField.width, y + row * 12 + 12, 0xFF353535.toInt())
            context.drawString(font, font.plainSubstrByWidth(completion.matches[index], nameField.width - 8), x + 4, y + row * 12 + 2,
                if (index == completion.selected) 0xFFFF55 else 0xAAAAAA)
        }
        context.pose().popPose()
    }

    /** 接收结构列表选择，不重置其余草稿。 */
    fun select(name: String) {
        this.name = name
        nameField.value = name
        history.record(captureState())
    }

    /** 服务端回执使失败可重试，成功关闭窗口。 */
    fun result(success: Boolean, message: String) {
        pending = false
        if (success) onClose() else {
            error = message
            rebuildWidgets()
        }
    }

    /** 接收服务器加载的真实快照，仅创建不加入世界的客户端预览。 */
    fun preview(response: CompoundTag) {
        if (response.getUUID("PreviewRequest") != previewRequest) return
        if (response.getString("Name") != ResourceLocation.tryParse(nameField.value.trim())?.toString()) {
            return
        }
        if (!response.contains("Structure")) { previewPane.clear(); return }
        val world = minecraft?.level ?: return
        runCatching {
            val snapshot = StructureSnapshot(world.registryAccess(), response.getCompound("Structure"))
            val settings = StructureModelSettings.fromNbt(response.getCompound("Settings"))
            require(settings.valid())
            if (!explicitPivot) {
                draft = draft.copy(pivot = settings.pivot, pivotCompensation = settings.pivotCompensation)
                for (axis in 0..2) {
                    values[10 + axis].setResponder {}
                    values[10 + axis].value = settings.pivot[axis].toString()
                    values[10 + axis].setResponder { explicitPivot = true; updateDraft() }
                }
            }
            previewName = response.getString("Name")
            previewPane.load(previewName!!, snapshot, draft.copy(pickable = false), !editing)
            history.replaceCurrent(captureState())
        }.onSuccess {
            error = ""
        }.onFailure { previewPane.clear() }
    }

    /**
     * 按当前输入顺序累计中心补偿，保持更换中心之前的姿态；不完整输入原样保留。
     * @return 有效的新草稿；任一参数未填完或超出范围时为空
     */
    private fun updateDraft(): StructureModelSettings? {
        val numbers = values.map { it.value.toDoubleOrNull() ?: return null }
        if (numbers.any { !it.isFinite() }) return null
        val current = draft.copy(
            health = numbers[0].toFloat(), rotation = numbers.subList(1, 4),
            offset = numbers.subList(4, 7), scale = numbers.subList(7, 10), deathAnimation = deathAnimation,
            pickable = false
        )
        val newPivot = numbers.subList(10, 13)
        if (!current.valid() || !current.copy(pivot = newPivot).valid()) return null
        if (newPivot != current.pivot) explicitPivot = true
        draft = if (newPivot == current.pivot) current else if (editing) current.withPivotKeepingPosition(newPivot)
            else current.copy(pivot = newPivot)
        previewPane.update(draft)
        return draft
    }

    /** 检查数字输入后提交，不允许使用此协议创建或修改掉落物栈。 */
    private fun submit(action: String) {
        if (pending) return
        val request = CompoundTag()
        request.putLong("Pos", data.getLong("Pos"))
        if (editing) request.putUUID("Entity", data.getUUID("Entity"))
        request.putString("Action", action)
        request.putString("Name", nameField.value.trim())
        if (action == "save") {
            request.put("EditorDraft", captureState())
            pending = true
            error = "正在保存草稿…"
            formWidgets.forEach { (widget, _) -> widget.active = false }
        } else if (action == "apply") {
            if (updateDraft() == null) { error = "请输入范围内的有效数字（中心 ±48）"; return }
            val configured = draft.copy(
                pickable = pickable, itemName = itemSettings.itemName.trim(), edible = edible, placeable = placeable,
                itemProperties = itemSettings.itemProperties)
            if (!configured.valid()) { error = "请补全物品名称，并检查手持配置中的事件 ID 和食品数值"; return }
            request.put("Settings", configured.toNbt())
            request.putBoolean("ExplicitPivot", explicitPivot)
            pending = true
            error = "正在保存…"
            formWidgets.forEach { (widget, _) -> widget.active = false }
        } else if (action == "import") {
            pending = true
            error = "正在转移…"
            formWidgets.forEach { (widget, _) -> widget.active = false }
        }
        CooClientPacketManager.sendTo(ModelEditPayload(request))
    }

    /** 原版背景与控件绘制完成后再画文字，避免文字被第二次背景模糊覆盖。 */
    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        // 1.21.1 的 Screen.render 自带背景模糊，必须先调用，再绘制标题和标签。
        super.render(context, mouseX, mouseY, delta)
        previewPane.render(context)
        val x = 8
        context.drawString(font, title, x, 10, 0xFFFFFF)
        context.enableScissor(x, 28, x + formWidth, height - 48)
        context.drawString(font, "已保存的结构", x, 30 - formScroll, 0xDDDDDD)
        val labels = listOf("最大生命", "旋转 X/Y/Z", "偏移 X/Y/Z", "缩放 X/Y/Z", "模型中心 X/Y/Z")
        labels.forEachIndexed { index, label ->
            context.drawString(font, label, x,
                numberTop + index * numberRowHeight - formScroll + if (formWidth >= 300) 6 else -10, 0xDDDDDD)
        }
        context.disableScissor()
        if (error.isNotEmpty()) context.drawString(font, font.plainSubstrByWidth(error, formWidth), x, height - 42, 0xFF9999)
        renderSuggestions(context, mouseX, mouseY)
    }

    /** 背景模糊只执行一次，并在其后铺设高对比度面板。 */
    override fun renderBackground(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.renderBackground(context, mouseX, mouseY, delta)
        context.fill(4, 4, 12 + formWidth, height - 4, 0xF0202428.toInt())
        context.fill(previewPane.left, 4, width - 8, height - 4, 0xFF16191D.toInt())
    }

    override fun isPauseScreen(): Boolean = false
}
