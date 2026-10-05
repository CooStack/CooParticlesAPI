package cn.coostack.cooparticlesapi.entities.structure.editor.client

import cn.coostack.cooparticlesapi.entities.structure.editor.StructureSelection
import cn.coostack.cooparticlesapi.entities.structure.editor.StructureSelectionPayload
import cn.coostack.cooparticlesapi.network.packet.api.CooClientPacketManager
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Checkbox
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.EditBox
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.core.BlockPos

/**
 * 结构保存界面；字段对应原版保存模式，原点是选区最小角而不是一个额外放置的结构方块。
 * @property data 服务端确认的两个角点、窗口版本及独立草稿
 */
class StructureSaveScreen(private val data: CompoundTag) : Screen(Component.literal("结构编辑器 · 保存")) {
    /** 本窗口绑定的固定选区，不能用后来改变的选点提交旧配置。 */
    private val selected = StructureSelection(BlockPos.of(data.getLong("First")), BlockPos.of(data.getLong("Second")))
    /** 服务器选区版本，保存结果只由对应窗口处理。 */
    private val revision = data.getUUID("Revision")
    /** 原版模板名称，未填写命名空间时沿用 minecraft。 */
    private var name = data.getCompound("Draft").getString("Name")
    /** 相对选区最小角的三个输入字符串，允许编辑中的空值与负号。 */
    private val offsets = MutableList(3) { axis -> data.getCompound("Draft").getInt("Offset${"XYZ"[axis]}").toString() }
    /** 三轴尺寸输入，未保存草稿时使用包含端点的原始尺寸。 */
    private val sizes = MutableList(3) { axis ->
        val draft = data.getCompound("Draft")
        if (draft.contains("SizeX")) draft.getInt("Size${"XYZ"[axis]}").toString()
        else listOf(selected.size.x, selected.size.y, selected.size.z)[axis].toString()
    }
    /** 是否将普通实体保存进原版文件，不会在模型实体内运行或复制它们。 */
    private var includeEntities = data.getCompound("Draft").getBoolean("IncludeEntities")
    /** 是否显示空气、结构空位和屏障标记，仅影响客户端。 */
    var showAir = data.getCompound("Draft").getBoolean("ShowAir")
        private set
    /** 是否显示常驻范围和角点，仅影响客户端。 */
    var showBox = !data.getCompound("Draft").contains("ShowBox") || data.getCompound("Draft").getBoolean("ShowBox")
        private set
    /** 默认关闭的破坏性转换选项，每次保存前还需确认。 */
    private var convert = false
    /** 保存请求等待期间禁用重复提交，但仍渲染准确选区。 */
    private var pending = false
    /** 服务端失败或等待状态，保留输入便于重试。 */
    private var status = ""
    /** 当前尺寸下的保存按钮。 */
    private var saveButton: Button? = null
    /** 当前尺寸下的完成按钮，只保存选区设置，不写结构文件。 */
    private var doneButton: Button? = null
    /** 当前尺寸下的取消按钮。 */
    private var cancelButton: Button? = null
    /** 所有输入控件，等待回执时锁定以保持提交与显示一致。 */
    private val inputs = mutableListOf<AbstractWidget>()
    /** 内容横向起点。 */
    private var left = 0
    /** 内容纵向起点。 */
    private var top = 0
    /** 控件总宽度，不随文字和选项变化。 */
    private var contentWidth = 0

    /** 重建控件时从成员草稿恢复内容，保证窗口缩放不丢失输入。 */
    override fun init() {
        inputs.clear()
        contentWidth = minOf(340, width - 16)
        left = (width - contentWidth) / 2
        top = ((height - 226) / 2).coerceAtLeast(4)
        val field = addRenderableWidget(EditBox(font, left, top + 30, contentWidth, 20, Component.literal("结构名称")))
        field.setMaxLength(256)
        field.value = name
        field.setResponder { name = it; refresh() }
        inputs.add(field)
        val cell = (contentWidth - 12) / 3
        repeat(3) { axis ->
            val x = left + axis * (cell + 6)
            number(x, top + 68, cell, "相对位置 ${"XYZ"[axis]}", offsets, axis)
            number(x, top + 106, cell, "大小 ${"XYZ"[axis]}", sizes, axis)
        }
        checkbox("包含实体", left, top + 134, includeEntities) { includeEntities = it }
        checkbox("显示边框", left + contentWidth / 2, top + 134, showBox) { showBox = it }
        checkbox("显示不可见方块", left, top + 157, showAir) { showAir = it }
        checkbox("清空并替换为实体", left + contentWidth / 2, top + 157, convert) { convert = it }
        saveButton = addRenderableWidget(Button.builder(Component.literal("保存")) {
            if (convert) {
                minecraft?.setScreen(ConfirmScreen({ accepted ->
                    minecraft?.setScreen(this)
                    if (accepted) submit(true)
                }, Component.literal("替换选区内的方块？"),
                    Component.literal("方块将变成静态结构实体；容器和红石将停止运行，库存不会作为掉落物返还。")))
            } else submit(true)
        }.bounds(left, top + 204, cell, 20).build())
        doneButton = addRenderableWidget(Button.builder(Component.literal("完成")) { submit(false) }
            .bounds(left + cell + 6, top + 204, cell, 20).build())
        cancelButton = addRenderableWidget(Button.builder(Component.literal("取消")) { onClose() }
            .bounds(left + 2 * (cell + 6), top + 204, cell, 20).build())
        refresh()
        setInitialFocus(field)
    }

    /** 创建可保留不完整输入的整数框，范围由提交前统一校验。 */
    private fun number(x: Int, y: Int, size: Int, label: String, values: MutableList<String>, axis: Int) {
        val field = addRenderableWidget(EditBox(font, x, y, size, 20, Component.literal(label)))
        field.setMaxLength(4)
        field.value = values[axis]
        field.setFilter { it.isEmpty() || it == "-" || it.toIntOrNull() != null }
        field.setResponder { values[axis] = it; refresh() }
        inputs.add(field)
    }

    /** 使用原版复选框表达二元选项，选项变化只更新窗口草稿。 */
    private fun checkbox(label: String, x: Int, y: Int, checked: Boolean, update: (Boolean) -> Unit) {
        inputs.add(addRenderableWidget(Checkbox.builder(Component.literal(label), font)
            .pos(x, y).maxWidth(contentWidth / 2 - 4).selected(checked).onValueChange { _, value -> update(value) }.build()))
    }

    /**
     * 返回窗口当前合法输入的准确整数选区，供世界线框预览使用。
     * @return 输入不完整或超过原版参数范围时为空，不用平滑后的显示坐标反推选区
     */
    fun previewSelection(): StructureSelection? {
        val position = offsets.map { it.toIntOrNull() ?: return null }
        val dimensions = sizes.map { it.toIntOrNull() ?: return null }
        return runCatching { selected.adjusted(
            BlockPos(position[0], position[1], position[2]), BlockPos(dimensions[0], dimensions[1], dimensions[2])
        ) }.getOrNull()
    }

    /** 使按钮有效性与精确范围、名称和服务端等待状态保持一致。 */
    private fun refresh() {
        val valid = previewSelection() != null
        saveButton?.active = !pending && valid && ResourceLocation.tryParse(name.trim()) != null && name.isNotBlank()
        doneButton?.active = !pending && valid
        cancelButton?.active = !pending
        inputs.forEach { it.active = !pending }
    }

    /** 提交范围参数和原版保存选项，不携带结构内容或绝对选点。 */
    private fun submit(write: Boolean) {
        if (pending || previewSelection() == null) return
        if (!(minecraft?.connection != null)) {
            status = "服务器不支持结构编辑器"
            return
        }
        val draft = CompoundTag().apply {
            putString("Name", name)
            repeat(3) {
                putInt("Offset${"XYZ"[it]}", offsets[it].toInt())
                putInt("Size${"XYZ"[it]}", sizes[it].toInt())
            }
            putBoolean("IncludeEntities", includeEntities)
            putBoolean("ShowBox", showBox)
            putBoolean("ShowAir", showAir)
            putBoolean("Convert", convert && write)
        }
        val request = CompoundTag().apply {
            putString("Action", if (write) "save" else "draft")
            putUUID("Revision", revision)
            put("Draft", draft)
        }
        pending = true
        status = if (write) "正在保存…" else "正在保留设置…"
        refresh()
        CooClientPacketManager.sendTo(StructureSelectionPayload(request))
    }

    /**
     * 接收本窗口对应的保存结果，失败保留所有输入，旧窗口响应不影响当前窗口。
     * @param response 服务端结果标签；必须具有与窗口相同的 Revision
     */
    fun result(response: CompoundTag) {
        if (!response.hasUUID("Revision") || response.getUUID("Revision") != revision) return
        pending = false
        if (response.getBoolean("Success")) {
            minecraft?.player?.displayClientMessage(Component.literal(response.getString("Message")), true)
            onClose()
        } else {
            status = response.getString("Message")
            refresh()
        }
    }

    /** 使用原版屏幕背景和控件，字段标题按固定三列排列，不让长错误消息挤压按钮。 */
    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)
        context.drawCenteredString(font, title, width / 2, top + 2, 0xFFFFFF)
        context.drawString(font, "结构名称", left, top + 18, 0xCCCCCC)
        val cell = (contentWidth - 12) / 3
        repeat(3) { axis ->
            context.drawString(font, "相对位置 ${"XYZ"[axis]}", left + axis * (cell + 6), top + 56, 0xCCCCCC)
            context.drawString(font, "大小 ${"XYZ"[axis]}", left + axis * (cell + 6), top + 94, 0xCCCCCC)
        }
        val message = if (status.isNotEmpty()) status else {
            val origin = previewSelection()?.origin
            if (origin == null) "相对位置 -48…48，大小 1…48" else "原点：${origin.x}, ${origin.y}, ${origin.z}"
        }
        context.drawString(font, font.plainSubstrByWidth(message, contentWidth), left, top + 186,
            if (status.isNotEmpty() && !pending) 0xFF7777 else 0xCCCCCC)
    }

    override fun onClose() { if (!pending) minecraft?.setScreen(null) }
    override fun shouldCloseOnEsc(): Boolean = !pending
    override fun isPauseScreen(): Boolean = false
}
