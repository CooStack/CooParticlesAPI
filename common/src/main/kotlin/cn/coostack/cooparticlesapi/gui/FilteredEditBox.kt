package cn.coostack.cooparticlesapi.gui

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import net.minecraft.util.StringUtil

/**
 * 为 26.3 原版输入框保留整体文字过滤；拒绝输入时不通知响应器，也不移动光标和选区。
 * 粘贴截断与代理字符边界沿用原版规则，过滤器只检查最终候选文字。
 * @param font 当前客户端字体
 * @param x 输入框左边界的 GUI 像素坐标
 * @param y 输入框上边界的 GUI 像素坐标
 * @param width 输入框宽度，单位为 GUI 像素
 * @param height 输入框高度，单位为 GUI 像素
 * @param message 输入框辅助朗读标签
 */
class FilteredEditBox(
    font: Font, x: Int, y: Int, width: Int, height: Int, message: Component
) : EditBox(font, x, y, width, height, message) {
    /** 整体文字是否合法；空值表示尚未配置过滤器。 */
    private var inputFilter: ((String) -> Boolean)? = null
    /** 与原版同步的最大 UTF-16 长度，用于预检粘贴后的候选文字。 */
    private var inputLimit = 32
    /** 原版选区的另一端，用于保留前向和反向选择语义。 */
    private var selectionAnchor = 0

    /**
     * 设置后续编辑采用的整体文字检查，不修改已经存在的文字。
     * 示例：`box.setFilter { it.isEmpty() || it.toIntOrNull() != null }`。
     * @param filter 接收编辑后的完整文字，返回是否允许提交给原版输入框
     */
    fun setFilter(filter: (String) -> Boolean) {
        inputFilter = filter
    }

    override fun setMaxLength(maxLength: Int) {
        inputLimit = maxLength
        super.setMaxLength(maxLength)
    }

    override fun setHighlightPos(position: Int) {
        super.setHighlightPos(position)
        selectionAnchor = position.coerceIn(0, value.length)
    }

    override fun setValue(text: String) {
        if (inputFilter?.invoke(text) != false) super.setValue(text)
    }

    /** 在原版修改光标或触发响应器前，检查与原版截断规则一致的替换结果。 */
    override fun insertText(text: String) {
        val start = minOf(cursorPosition, selectionAnchor)
        val end = maxOf(cursorPosition, selectionAnchor)
        var available = inputLimit - value.length + end - start
        if (available <= 0) return
        var inserted = StringUtil.filterText(text)
        if (inserted.length > available) {
            if (inserted[available - 1].isHighSurrogate()) available--
            inserted = inserted.take(available)
        }
        val candidate = value.replaceRange(start, end, inserted)
        if (inputFilter?.invoke(candidate) != false) super.insertText(text)
    }

    /** 删除同样先检查整体结果，避免非法候选短暂进入外部草稿。 */
    override fun deleteCharsToPos(position: Int) {
        if (selectionAnchor != cursorPosition) {
            insertText("")
            return
        }
        val start = minOf(position, cursorPosition)
        val end = maxOf(position, cursorPosition)
        val candidate = value.removeRange(start, end)
        if (inputFilter?.invoke(candidate) != false) super.deleteCharsToPos(position)
    }
}
