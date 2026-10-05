package cn.coostack.cooparticlesapi.entities.structure.client

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component

/** 搜索原版模板管理器列出的结构，可直接返回父窗口继续编辑。
 * @property parent 保存草稿的父窗口
 * @property names 服务端目录，最多 4096 项；也可在父窗口直接输入 ID
 */
class ModelStructureScreen(private val parent: ModelEditorScreen, private val names: List<String>) : Screen(Component.literal("选择已保存结构")) {
    /** 当前搜索字符串。 */
    private var query = ""
    /** 从零开始的结果页码。 */
    private var page = 0
    /** 当前页的结果按钮，刷新搜索时原地更新。 */
    private val rows = mutableListOf<Button>()

    /** 构建搜索与分页控件，保留上次搜索状态。 */
    override fun init() {
        rows.clear()
        val x = (width - 310) / 2
        val y = (height - 230) / 2
        val search = addRenderableWidget(EditBox(font, x, y + 24, 310, 20, Component.literal("搜索结构")))
        search.value = query
        search.setResponder { query = it; page = 0; refresh() }
        repeat(6) { index ->
            rows.add(addRenderableWidget(Button.builder(Component.empty()) { button ->
                parent.select(button.message.string)
                minecraft?.setScreen(parent)
            }.bounds(x, y + 50 + index * 23, 310, 20).build()))
        }
        addRenderableWidget(Button.builder(Component.literal("上一页")) { page = (page - 1).coerceAtLeast(0); refresh() }
            .bounds(x, y + 205, 90, 20).build())
        addRenderableWidget(Button.builder(Component.literal("返回")) { onClose() }.bounds(x + 110, y + 205, 90, 20).build())
        addRenderableWidget(Button.builder(Component.literal("下一页")) { page++; refresh() }.bounds(x + 220, y + 205, 90, 20).build())
        refresh()
    }

    /** 过滤并更新当前页，不重建输入框以保留光标。 */
    private fun refresh() {
        val filtered = names.filter { it.contains(query, true) }
        page = page.coerceIn(0, ((filtered.size - 1) / 6).coerceAtLeast(0))
        rows.forEachIndexed { index, button ->
            val name = filtered.getOrNull(page * 6 + index)
            button.message = Component.literal(name ?: "")
            button.visible = name != null
        }
    }

    /** 在原版背景和按钮之后绘制清晰标题及分页提示。 */
    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)
        context.drawCenteredString(font, title, width / 2, (height - 230) / 2 + 5, 0xFFFFFF)
        context.drawCenteredString(font, "第 ${page + 1} 页 · 未列出时可返回直接输入 ID", width / 2, (height - 230) / 2 + 191, 0xCCCCCC)
    }
    override fun onClose() { minecraft?.setScreen(parent) }
    override fun isPauseScreen(): Boolean = false
}
