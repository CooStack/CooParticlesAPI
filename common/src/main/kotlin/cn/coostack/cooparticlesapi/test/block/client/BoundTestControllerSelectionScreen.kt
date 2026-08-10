package cn.coostack.cooparticlesapi.test.block.client

import cn.coostack.cooparticlesapi.network.packet.api.CooClientPacketManager
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketOpenBoundTestSelectionScreenS2C
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketSelectBoundTestControllerC2S
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class BoundTestControllerSelectionScreen(
    private val packet: PacketOpenBoundTestSelectionScreenS2C
) : Screen(Component.literal("选择测试方块")) {
    private val rowHeight = 28
    private val maxVisibleRows = 10
    private var scrollOffset = 0

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val index = rowAt(mouseX.toInt(), mouseY.toInt())
        if (index != null && packet.loaded.getOrNull(index) == true) {
            CooClientPacketManager.sendTo(PacketSelectBoundTestControllerC2S(packet.dimensions[index], packet.positions[index]))
            onClose()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (!isInList(mouseX.toInt(), mouseY.toInt()) || maxScrollOffset() <= 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        }
        val delta = if (scrollY < 0.0) 1 else -1
        scrollOffset = (scrollOffset + delta).coerceIn(0, maxScrollOffset())
        return true
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics, mouseX, mouseY, partialTick)
        super.render(graphics, mouseX, mouseY, partialTick)
        renderList(graphics, mouseX, mouseY)
        renderHoveredTooltip(graphics, mouseX, mouseY)
    }

    private fun renderList(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        clampScrollOffset()
        val left = listLeft()
        val top = listTop()
        val listWidth = listWidth()
        val rowsTop = rowsTop()
        val rowsBottom = rowsBottom()
        val bottom = rowsBottom + 10
        graphics.fill(left - 10, top - 10, left + listWidth + 10, bottom, 0xCC101214.toInt())
        graphics.fill(left - 9, top - 9, left + listWidth + 9, top - 8, 0xFF6F8A6F.toInt())
        graphics.drawString(font, title, left, top, 0xFFFFFF, true)
        graphics.drawString(font, "已绑定 ${entryCount()} 个测试方块", left, top + 12, 0xCFCFCF, true)

        graphics.enableScissor(left, rowsTop, left + listWidth, rowsBottom)
        var y = rowsTop
        for (index in scrollOffset until minOf(entryCount(), scrollOffset + visibleRowCount())) {
            val hovered = rowAt(mouseX, mouseY) == index
            val loaded = packet.loaded.getOrNull(index) == true
            val running = packet.running.getOrNull(index) == true
            val rowColor = when {
                hovered && loaded -> 0xAA2F6F2F.toInt()
                hovered -> 0xAA3A3A3A.toInt()
                loaded -> 0x88181818.toInt()
                else -> 0x88402020.toInt()
            }
            graphics.fill(left, y, left + listWidth, y + rowHeight - 2, rowColor)
            val groupId = packet.groupIds.getOrElse(index) { "未配置" }
            val status = displayStatus(index)
            graphics.drawString(font, groupId, left + 6, y + 4, if (loaded) 0xE0FFE0 else 0xFFAAAA, true)
            graphics.drawString(font, status, left + 6, y + 16, 0xE0E0E0, true)
            y += rowHeight
        }
        graphics.disableScissor()
        renderScrollbar(graphics, left, rowsTop, rowsBottom, listWidth)
    }

    private fun renderHoveredTooltip(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val hovered = rowAt(mouseX, mouseY)
        if (hovered != null) {
            val pos = packet.positions.getOrNull(hovered)
            val dimension = packet.dimensions.getOrElse(hovered) { "unknown" }
            val status = displayStatus(hovered)
            graphics.renderTooltip(
                font,
                Component.literal("位置: $dimension ${if (pos != null) "${pos.x}, ${pos.y}, ${pos.z}" else "unknown"}\n状态: $status"),
                mouseX,
                mouseY
            )
        }
    }

    private fun displayStatus(index: Int): String {
        val running = packet.running.getOrNull(index) == true
        val currentIndex = packet.currentIndices.getOrElse(index) { 0 }
        val currentOptionId = packet.currentOptionIds.getOrElse(index) { "" }
        val optionCount = packet.optionCounts.getOrElse(index) { 0 }
        if (running) {
            return testControllerRunningStatus(currentIndex, optionCount, currentOptionId)
        }
        return compactStatus(packet.statuses.getOrElse(index) { "未知" })
    }

    private fun compactStatus(status: String): String {
        if (status.startsWith("[测试 ")) {
            val end = status.indexOf(']')
            if (end >= 0) {
                return status.substring(1, end)
            }
        }
        return if (status.length > 48) status.take(45) + "..." else status
    }

    private fun rowAt(mouseX: Int, mouseY: Int): Int? {
        val left = listLeft()
        val top = rowsTop()
        val listWidth = listWidth()
        if (mouseX !in left..(left + listWidth) || mouseY < top || mouseY >= rowsBottom()) return null
        val index = scrollOffset + (mouseY - top) / rowHeight
        return if (index in 0 until entryCount()) index else null
    }

    private fun renderScrollbar(graphics: GuiGraphics, left: Int, rowsTop: Int, rowsBottom: Int, listWidth: Int) {
        val maxScroll = maxScrollOffset()
        if (maxScroll <= 0) return
        val barX = left + listWidth - 5
        val barHeight = rowsBottom - rowsTop
        val thumbHeight = maxOf(14, barHeight * visibleRowCount() / entryCount())
        val travel = (barHeight - thumbHeight).coerceAtLeast(1)
        val thumbTop = rowsTop + travel * scrollOffset / maxScroll
        graphics.fill(barX, rowsTop, barX + 4, rowsBottom, 0xAA080808.toInt())
        graphics.fill(barX, thumbTop, barX + 4, thumbTop + thumbHeight, 0xFFE0E0E0.toInt())
    }

    private fun isInList(mouseX: Int, mouseY: Int): Boolean {
        val left = listLeft()
        return mouseX in left..(left + listWidth()) && mouseY in rowsTop() until rowsBottom()
    }

    private fun listLeft(): Int {
        return width / 2 - listWidth() / 2
    }

    private fun listTop(): Int {
        return 32
    }

    private fun rowsTop(): Int {
        return listTop() + 32
    }

    private fun rowsBottom(): Int {
        return rowsTop() + visibleRowCount() * rowHeight
    }

    private fun visibleRowCount(): Int {
        val availableRows = ((height - rowsTop() - 24) / rowHeight).coerceAtLeast(1)
        return minOf(entryCount().coerceAtLeast(1), maxVisibleRows, availableRows)
    }

    private fun maxScrollOffset(): Int {
        return (entryCount() - visibleRowCount()).coerceAtLeast(0)
    }

    private fun clampScrollOffset() {
        scrollOffset = scrollOffset.coerceIn(0, maxScrollOffset())
    }

    private fun listWidth(): Int {
        return minOf(360, width - 40).coerceAtLeast(180)
    }

    private fun entryCount(): Int {
        return minOf(
            packet.dimensions.size,
            packet.positions.size,
            packet.groupIds.size,
            packet.statuses.size,
            packet.currentIndices.size,
            packet.currentOptionIds.size,
            packet.optionCounts.size
        )
    }
}
