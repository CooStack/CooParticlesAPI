package cn.coostack.cooparticlesapi.test.block.client

import cn.coostack.cooparticlesapi.network.packet.api.CooClientPacketManager
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketOpenTestControllerScreenS2C
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketStartTestControllerC2S
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketStopTestControllerC2S
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketUpdateTestControllerC2S
import cn.coostack.cooparticlesapi.test.block.BlockTestMode
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.util.Locale
import kotlin.math.pow
import kotlin.math.round

class TestControllerScreen(
    private val packet: PacketOpenTestControllerScreenS2C
) : Screen(Component.literal("测试方块")) {
    private lateinit var groupBox: EditBox
    private lateinit var indexBox: EditBox
    private lateinit var delayBox: EditBox
    private lateinit var offsetXBox: EditBox
    private lateinit var offsetYBox: EditBox
    private lateinit var offsetZBox: EditBox
    private lateinit var forwardXBox: EditBox
    private lateinit var forwardYBox: EditBox
    private lateinit var forwardZBox: EditBox
    private lateinit var widthBox: EditBox
    private lateinit var heightBox: EditBox
    private lateinit var depthBox: EditBox
    private lateinit var modeButton: Button
    private lateinit var repeatButton: Button
    private lateinit var offsetPickButton: Button
    private lateinit var forwardPickButton: Button
    private lateinit var precisionButton: Button
    private lateinit var saveButton: Button
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private val maxVisibleSuggestions = 3
    private var mode = BlockTestMode.fromId(packet.mode)
    private var repeatIndex = packet.repeatIndex
    private var precisionUnlocked = false
    private var suggestions: List<String> = emptyList()
    private var selectedSuggestionIndex = -1
    private var suggestionScroll = 0

    override fun init() {
        val left = width / 2 - 170
        var y = 34
        groupBox = editBox(left + 92, y, 220, packet.groupId)
        groupBox.setMaxLength(256)
        groupBox.setResponder { updateSuggestions() }
        addRenderableWidget(groupBox)

        y += 28
        modeButton = Button.builder(Component.literal(mode.displayName)) {
            mode = when (mode) {
                BlockTestMode.SEQUENTIAL -> BlockTestMode.INDEX
                BlockTestMode.INDEX -> BlockTestMode.LOOP
                BlockTestMode.LOOP -> BlockTestMode.SEQUENTIAL
            }
            modeButton.message = Component.literal(mode.displayName)
            updateIndexSuggestion()
            layoutWidgets()
        }.bounds(left + 92, y, 94, 20).build()
        addRenderableWidget(modeButton)

        repeatButton = Button.builder(Component.literal(repeatLabel())) {
            repeatIndex = !repeatIndex
            repeatButton.message = Component.literal(repeatLabel())
        }.bounds(left + 194, y, 118, 20).build()
        addRenderableWidget(repeatButton)

        y += 28
        indexBox = editBox(left + 92, y, 60, packet.selectedIndex.toString())
        indexBox.setResponder { updateIndexSuggestion() }
        delayBox = editBox(left + 252, y, 60, packet.repeatDelayTicks.toString())
        addRenderableWidget(indexBox)
        addRenderableWidget(delayBox)

        y += 28
        offsetXBox = editBox(left + 92, y, 64, trim(packet.offsetX))
        offsetYBox = editBox(left + 166, y, 64, trim(packet.offsetY))
        offsetZBox = editBox(left + 240, y, 64, trim(packet.offsetZ))
        addRenderableWidget(offsetXBox)
        addRenderableWidget(offsetYBox)
        addRenderableWidget(offsetZBox)

        y += 28
        forwardXBox = editBox(left + 92, y, 64, trim(packet.forwardX))
        forwardYBox = editBox(left + 166, y, 64, trim(packet.forwardY))
        forwardZBox = editBox(left + 240, y, 64, trim(packet.forwardZ))
        addRenderableWidget(forwardXBox)
        addRenderableWidget(forwardYBox)
        addRenderableWidget(forwardZBox)

        offsetPickButton = Button.builder(Component.literal("拾取")) {
            startPick(TestControllerPickKind.OFFSET)
        }.bounds(left + 312, offsetXBox.y, 48, 20).build()
        addRenderableWidget(offsetPickButton)

        forwardPickButton = Button.builder(Component.literal("拾取")) {
            startPick(TestControllerPickKind.FORWARD)
        }.bounds(left + 312, forwardXBox.y, 48, 20).build()
        addRenderableWidget(forwardPickButton)

        y += 28
        widthBox = editBox(left + 92, y, 64, trim(packet.boxWidth))
        heightBox = editBox(left + 166, y, 64, trim(packet.boxHeight))
        depthBox = editBox(left + 240, y, 64, trim(packet.boxDepth))
        addRenderableWidget(widthBox)
        addRenderableWidget(heightBox)
        addRenderableWidget(depthBox)

        precisionButton = Button.builder(Component.literal(precisionLabel())) {
            precisionUnlocked = !precisionUnlocked
            precisionButton.message = Component.literal(precisionLabel())
            normalizeNumericBoxes()
        }.bounds(left + 312, y, 48, 20).build()
        addRenderableWidget(precisionButton)

        y += 34
        saveButton = Button.builder(Component.literal("保存")) {
            CooClientPacketManager.sendTo(updatePacket())
            onClose()
        }.bounds(left + 44, y, 74, 20).build()
        addRenderableWidget(saveButton)

        startButton = Button.builder(Component.literal("开始测试")) {
            CooClientPacketManager.sendTo(updatePacket())
            CooClientPacketManager.sendTo(PacketStartTestControllerC2S(packet.dimension, packet.blockPos))
            onClose()
        }.bounds(left + 126, y, 86, 20).build()
        addRenderableWidget(startButton)

        stopButton = Button.builder(Component.literal("停止测试")) {
            CooClientPacketManager.sendTo(PacketStopTestControllerC2S(packet.dimension, packet.blockPos))
            onClose()
        }.bounds(left + 220, y, 86, 20).build()
        addRenderableWidget(stopButton)

        updateSuggestions()
        updateIndexSuggestion()
        layoutWidgets()
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (groupBox.isFocused && suggestions.isNotEmpty()) {
            when (keyCode) {
                GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    return acceptSelectedSuggestion()
                }
                GLFW.GLFW_KEY_DOWN -> {
                    moveSelectedSuggestion(1)
                    return true
                }
                GLFW.GLFW_KEY_UP -> {
                    moveSelectedSuggestion(-1)
                    return true
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val pickedIndex = suggestionAt(mouseX.toInt(), mouseY.toInt())
        if (pickedIndex != null) {
            selectedSuggestionIndex = pickedIndex
            groupBox.value = suggestions[pickedIndex]
            updateSuggestions()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics, mouseX, mouseY, partialTick)
        super.render(graphics, mouseX, mouseY, partialTick)
        renderLabels(graphics)
        renderSuggestions(graphics, mouseX, mouseY)
    }

    private fun renderLabels(graphics: GuiGraphics) {
        val left = width / 2 - 170
        var y = 16
        graphics.drawString(font, title, left, y, 0xFFFFFF, true)
        y = 38
        graphics.drawString(font, "TestGroupID", left, y, 0xE0E0E0, true)
        y += 28
        graphics.drawString(font, "模式", left, y, 0xE0E0E0, true)
        if (mode == BlockTestMode.INDEX) {
            y += 28
            graphics.drawString(font, "索引", left, y, 0xE0E0E0, true)
            graphics.drawString(font, "重播前等待(tick)", left + 174, y, 0xE0E0E0, true)
            graphics.drawString(font, indexHint(), left + 92, y + 14, 0xA8DDA8, true)
            graphics.drawString(font, selectedOptionIdLine(), left + 92, y + 24, 0xC8EFC8, true)
            y += 38
        } else {
            y += 28
        }
        graphics.drawString(font, "位置偏移", left, y, 0xE0E0E0, true)
        y += 28
        graphics.drawString(font, "Forward", left, y, 0xE0E0E0, true)
        y += 28
        graphics.drawString(font, "碰撞箱", left, y, 0xE0E0E0, true)
        graphics.drawString(font, "状态: ${displayStatus()}", left, height - 34, 0xF0F0F0, true)
        graphics.drawString(font, "测试项: ${packet.optionCount}", left, height - 22, 0xF0F0F0, true)
    }

    private fun editBox(x: Int, y: Int, width: Int, value: String): EditBox {
        return EditBox(font, x, y, width, 20, Component.empty()).also { box ->
            box.value = value
        }
    }

    private fun updatePacket(): PacketUpdateTestControllerC2S {
        return PacketUpdateTestControllerC2S().also {
            it.dimension = packet.dimension
            it.blockPos = packet.blockPos
            it.groupId = groupBox.value
            it.mode = mode.id
            it.selectedIndex = indexBox.value.toIntOrNull() ?: 0
            it.repeatIndex = repeatIndex
            it.repeatDelayTicks = delayBox.value.toIntOrNull() ?: 0
            it.offsetX = doubleValue(offsetXBox, 0.0)
            it.offsetY = doubleValue(offsetYBox, 0.0)
            it.offsetZ = doubleValue(offsetZBox, 0.0)
            it.forwardX = doubleValue(forwardXBox, 0.0)
            it.forwardY = doubleValue(forwardYBox, 0.0)
            it.forwardZ = doubleValue(forwardZBox, 1.0)
            it.boxWidth = doubleValue(widthBox, 0.6)
            it.boxHeight = doubleValue(heightBox, 1.8)
            it.boxDepth = doubleValue(depthBox, 0.6)
        }
    }

    private fun updateSuggestions() {
        val input = groupBox.value.trim()
        suggestions = packet.registeredIds
            .filter { id -> input.isBlank() || id.startsWith(input, ignoreCase = true) }
            .filter { id -> input.isBlank() || !id.equals(input, ignoreCase = true) }
        selectedSuggestionIndex = if (suggestions.isEmpty()) -1 else 0
        suggestionScroll = 0
        val first = suggestions.firstOrNull()
        groupBox.setSuggestion(if (input.isNotBlank() && first?.startsWith(input, ignoreCase = true) == true) first.drop(input.length) else null)
        updateIndexSuggestion()
    }

    private fun renderSuggestions(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (!groupBox.isFocused || suggestions.isEmpty()) return
        val x = groupBox.x
        var y = groupBox.y + groupBox.height + 2
        val end = minOf(suggestions.size, suggestionScroll + maxVisibleSuggestions)
        for (index in suggestionScroll until end) {
            val suggestion = suggestions[index]
            val hovered = mouseX in x..(x + groupBox.width) && mouseY in y..(y + 13)
            val selected = index == selectedSuggestionIndex
            val color = when {
                hovered -> 0x99224422.toInt()
                selected -> 0x991A331A.toInt()
                else -> 0x99000000.toInt()
            }
            graphics.fill(x, y, x + groupBox.width, y + 13, color)
            graphics.drawString(font, suggestion, x + 3, y + 3, 0xE0FFE0, true)
            y += 13
        }
    }

    private fun suggestionAt(mouseX: Int, mouseY: Int): Int? {
        if (!groupBox.isFocused || suggestions.isEmpty()) return null
        val x = groupBox.x
        var y = groupBox.y + groupBox.height + 2
        val end = minOf(suggestions.size, suggestionScroll + maxVisibleSuggestions)
        for (index in suggestionScroll until end) {
            if (mouseX in x..(x + groupBox.width) && mouseY in y..(y + 13)) return index
            y += 13
        }
        return null
    }

    private fun acceptSelectedSuggestion(): Boolean {
        val index = selectedSuggestionIndex.takeIf { it in suggestions.indices } ?: return false
        groupBox.value = suggestions[index]
        updateSuggestions()
        updateIndexSuggestion()
        return true
    }

    private fun moveSelectedSuggestion(delta: Int) {
        if (suggestions.isEmpty()) return
        selectedSuggestionIndex = if (selectedSuggestionIndex in suggestions.indices) {
            (selectedSuggestionIndex + delta).coerceIn(0, suggestions.lastIndex)
        } else {
            0
        }
        ensureSelectedSuggestionVisible()
    }

    private fun ensureSelectedSuggestionVisible() {
        if (selectedSuggestionIndex !in suggestions.indices) {
            suggestionScroll = 0
            return
        }
        if (selectedSuggestionIndex < suggestionScroll) {
            suggestionScroll = selectedSuggestionIndex
        }
        if (selectedSuggestionIndex >= suggestionScroll + maxVisibleSuggestions) {
            suggestionScroll = selectedSuggestionIndex - maxVisibleSuggestions + 1
        }
        suggestionScroll = suggestionScroll.coerceIn(0, (suggestions.size - maxVisibleSuggestions).coerceAtLeast(0))
    }

    private fun updateIndexSuggestion() {
        if (!::indexBox.isInitialized) return
        indexBox.setSuggestion(if (mode == BlockTestMode.INDEX && indexBox.value.isBlank()) indexHint() else null)
    }

    private fun indexHint(): String {
        val count = currentOptionIds().size
        return if (count > 0) {
            "可用: 0-${count - 1}"
        } else {
            "无可用索引"
        }
    }

    private fun selectedOptionIdLine(): String {
        val index = indexBox.value.toIntOrNull() ?: packet.selectedIndex
        val id = currentOptionIds().getOrNull(index) ?: return "Option: 无"
        val clipped = if (id.length > 46) id.take(43) + "..." else id
        return "Option: $clipped"
    }

    private fun currentOptionIds(): List<String> {
        val groupId = groupBox.value.trim()
        if (groupId.isBlank()) {
            return emptyList()
        }
        val index = packet.registeredIds.indexOfFirst { id -> id.equals(groupId, ignoreCase = true) }
        if (index !in packet.registeredOptionIds.indices) {
            return if (groupId.equals(packet.groupId, ignoreCase = true)) packet.optionIds else emptyList()
        }
        val encoded = packet.registeredOptionIds[index]
        return if (encoded.isBlank()) {
            emptyList()
        } else {
            encoded.split(PacketOpenTestControllerScreenS2C.OPTION_ID_SEPARATOR)
        }
    }

    private fun displayStatus(): String {
        if (packet.running) {
            return if (packet.currentIndex > 0 && packet.optionCount > 0) {
                "运行中 当前索引: ${packet.currentIndex}/${packet.optionCount}"
            } else {
                compactStatus(packet.status).takeIf { it.isNotBlank() } ?: "运行中"
            }
        }
        return compactStatus(packet.status)
    }

    private fun compactStatus(status: String): String {
        if (status.startsWith("[测试 ")) {
            val end = status.indexOf(']')
            if (end >= 0) {
                return status.substring(1, end)
            }
        }
        return if (status.length > 56) status.take(53) + "..." else status
    }

    private fun repeatLabel(): String {
        return if (repeatIndex) "索引重播: 开" else "索引重播: 关"
    }

    private fun precisionLabel(): String {
        return if (precisionUnlocked) "不限" else "6位"
    }

    private fun doubleValue(box: EditBox, fallback: Double): Double {
        val value = box.value.toDoubleOrNull() ?: fallback
        return if (precisionUnlocked) value else value.roundToDecimals(6)
    }

    private fun trim(value: Double): String {
        return formatDouble(value)
    }

    private fun formatDouble(value: Double): String {
        if (precisionUnlocked) {
            return value.toString()
        }
        val rounded = value.roundToDecimals(6)
        if (rounded % 1.0 == 0.0) {
            return rounded.toInt().toString()
        }
        return String.format(Locale.ROOT, "%.6f", rounded).trimEnd('0').trimEnd('.')
    }

    private fun Double.roundToDecimals(decimals: Int): Double {
        val scale = 10.0.pow(decimals)
        return round(this * scale) / scale
    }

    private fun normalizeNumericBoxes() {
        listOf(
            offsetXBox to 0.0,
            offsetYBox to 0.0,
            offsetZBox to 0.0,
            forwardXBox to 0.0,
            forwardYBox to 0.0,
            forwardZBox to 1.0,
            widthBox to 0.6,
            heightBox to 1.8,
            depthBox to 0.6
        ).forEach { (box, fallback) ->
            box.value = formatDouble(box.value.toDoubleOrNull() ?: fallback)
        }
    }

    private fun layoutWidgets() {
        if (!::groupBox.isInitialized) return
        val left = width / 2 - 170
        val indexVisible = mode == BlockTestMode.INDEX
        val offsetY = if (indexVisible) 128 else 90
        val forwardY = offsetY + 28
        val boxY = forwardY + 28
        val buttonsY = boxY + 34

        groupBox.setX(left + 92)
        groupBox.setY(34)
        modeButton.setX(left + 92)
        modeButton.setY(62)
        repeatButton.setX(left + 194)
        repeatButton.setY(62)
        repeatButton.visible = indexVisible
        repeatButton.active = indexVisible

        indexBox.setX(left + 92)
        indexBox.setY(90)
        delayBox.setX(left + 252)
        delayBox.setY(90)
        listOf(indexBox, delayBox).forEach {
            it.visible = indexVisible
            it.active = indexVisible
        }

        setRow(offsetY, offsetXBox, offsetYBox, offsetZBox, offsetPickButton)
        setRow(forwardY, forwardXBox, forwardYBox, forwardZBox, forwardPickButton)
        setRow(boxY, widthBox, heightBox, depthBox, precisionButton)
        saveButton.setX(left + 44)
        saveButton.setY(buttonsY)
        startButton.setX(left + 126)
        startButton.setY(buttonsY)
        stopButton.setX(left + 220)
        stopButton.setY(buttonsY)
    }

    private fun setRow(y: Int, first: EditBox, second: EditBox, third: EditBox, button: Button) {
        val left = width / 2 - 170
        first.setX(left + 92)
        first.setY(y)
        second.setX(left + 166)
        second.setY(y)
        third.setX(left + 240)
        third.setY(y)
        button.setX(left + 312)
        button.setY(y)
    }

    private fun startPick(kind: TestControllerPickKind) {
        TestControllerPickClient.begin(packet, updatePacket(), kind, precisionUnlocked)
        onClose()
    }
}
