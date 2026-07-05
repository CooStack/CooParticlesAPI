package cn.coostack.cooparticlesapi.test.block.client

import cn.coostack.cooparticlesapi.network.packet.api.CooClientPacketManager
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketOpenTestControllerScreenS2C
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketStartTestControllerC2S
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketStopTestControllerC2S
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketUpdateTestControllerC2S
import cn.coostack.cooparticlesapi.test.api.EncodedTestOptionParamSpec
import cn.coostack.cooparticlesapi.test.api.TestOptionParamCodec
import cn.coostack.cooparticlesapi.test.api.TestOptionParamEditorKind
import cn.coostack.cooparticlesapi.test.api.TestOptionParamPositionMode
import cn.coostack.cooparticlesapi.test.block.BlockTestMode
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.pow
import kotlin.math.round

class TestControllerScreen(
    private val packet: PacketOpenTestControllerScreenS2C,
    openParamPage: Boolean = false
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
    private lateinit var paramsButton: Button
    private lateinit var backButton: Button

    private val paramBoxes = ArrayList<EditBox>()
    private val paramComponentBoxes = ArrayList<List<EditBox>>()
    private val paramModeButtons = ArrayList<Button>()
    private val paramPickButtons = ArrayList<Button>()
    private val paramPaletteButtons = ArrayList<Button>()
    private val paramSnapButtons = ArrayList<Button>()
    private val paramAbsoluteModes = ArrayList<Boolean>()
    private val paramValues = LinkedHashMap<String, String>()
    private val maxVisibleSuggestions = 3
    private var mode = BlockTestMode.fromId(packet.mode)
    private var repeatIndex = packet.repeatIndex
    private var precisionUnlocked = false
    private var currentParamSpecs: List<EncodedTestOptionParamSpec> = emptyList()
    private var paramStateKey = ""
    private var paramScroll = 0
    private var page = if (openParamPage) ControllerPage.PARAMS else ControllerPage.MAIN
    private var suggestions: List<String> = emptyList()
    private var selectedSuggestionIndex = -1
    private var suggestionScroll = 0
    private var paramSuggestions: List<String> = emptyList()
    private var selectedParamSuggestionIndex = -1
    private var paramSuggestionScroll = 0

    override fun init() {
        val left = width / 2 - 170
        var y = 34
        paramBoxes.clear()
        paramComponentBoxes.clear()
        paramModeButtons.clear()
        paramPickButtons.clear()
        paramPaletteButtons.clear()
        paramSnapButtons.clear()
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
            updateParamBoxes()
        }.bounds(left + 92, y, 94, 20).build()
        addRenderableWidget(modeButton)

        repeatButton = Button.builder(Component.literal(repeatLabel())) {
            repeatIndex = !repeatIndex
            repeatButton.message = Component.literal(repeatLabel())
        }.bounds(left + 194, y, 118, 20).build()
        addRenderableWidget(repeatButton)

        y += 28
        indexBox = editBox(left + 92, y, 60, packet.selectedIndex.toString())
        indexBox.setResponder {
            updateIndexSuggestion()
            updateParamBoxes()
        }
        delayBox = editBox(left + 252, y, 60, packet.repeatDelayTicks.toString())
        addRenderableWidget(indexBox)
        addRenderableWidget(delayBox)

        paramsButton = Button.builder(Component.literal("\u53c2\u6570")) {
            prepareParamState(syncVisible = true)
            page = ControllerPage.PARAMS
            writeVisibleParamBoxes()
            updateParamSuggestion()
            layoutWidgets()
        }.bounds(left + 316, y, 54, 20).build()
        addRenderableWidget(paramsButton)

        repeat(visibleParamRowCapacity()) { index ->
            val paramBox = editBox(left + 92, y, 150, "")
            paramBox.setMaxLength(512)
            paramBox.setResponder { updateParamSuggestion() }
            paramBoxes.add(paramBox)
            addRenderableWidget(paramBox)

            val componentBoxes = ArrayList<EditBox>()
            repeat(PARAM_MAX_COMPONENT_BOXES) {
                val componentBox = editBox(left + 92, y, 40, "")
                componentBox.setMaxLength(64)
                componentBox.setResponder { updateParamSuggestion() }
                componentBoxes.add(componentBox)
                addRenderableWidget(componentBox)
            }
            paramComponentBoxes.add(componentBoxes)

            val modeButton = Button.builder(Component.literal("相对")) {
                toggleParamMode(index)
            }.bounds(left + 248, y, 34, 20).build()
            paramModeButtons.add(modeButton)
            addRenderableWidget(modeButton)

            val pickButton = Button.builder(Component.literal("拾取")) {
                startParamPick(index)
            }.bounds(left + 286, y, 42, 20).build()
            paramPickButtons.add(pickButton)
            addRenderableWidget(pickButton)

            val paletteButton = Button.builder(Component.literal("色板")) {
                applyNextPaletteColor(index)
            }.bounds(left + 248, y, 38, 20).build()
            paramPaletteButtons.add(paletteButton)
            addRenderableWidget(paletteButton)

            val snapButton = Button.builder(Component.literal("吸附")) {
                snapColorToPalette(index)
            }.bounds(left + 290, y, 38, 20).build()
            paramSnapButtons.add(snapButton)
            addRenderableWidget(snapButton)
        }

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

        backButton = Button.builder(Component.literal("\u8fd4\u56de")) {
            syncVisibleParamValuesToState()
            page = ControllerPage.MAIN
            updateParamSuggestion()
            layoutWidgets()
        }.bounds(left, y, 58, 20).build()
        addRenderableWidget(backButton)

        updateSuggestions()
        updateIndexSuggestion()
        updateParamBoxes(syncVisible = false)
        layoutWidgets()
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && page == ControllerPage.PARAMS) {
            syncVisibleParamValuesToState()
            page = ControllerPage.MAIN
            updateParamSuggestion()
            layoutWidgets()
            return true
        }
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
        if (paramSuggestions.isNotEmpty() && focusedEnumParamIndex() != null) {
            when (keyCode) {
                GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    return acceptSelectedParamSuggestion()
                }
                GLFW.GLFW_KEY_DOWN -> {
                    moveSelectedParamSuggestion(1)
                    return true
                }
                GLFW.GLFW_KEY_UP -> {
                    moveSelectedParamSuggestion(-1)
                    return true
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val pickedColor = colorPaletteAt(mouseX.toInt(), mouseY.toInt())
        if (pickedColor != null) {
            applyPaletteColor(pickedColor.first, pickedColor.second)
            return true
        }
        val pickedParamIndex = paramSuggestionAt(mouseX.toInt(), mouseY.toInt())
        if (pickedParamIndex != null) {
            selectedParamSuggestionIndex = pickedParamIndex
            acceptSelectedParamSuggestion()
            return true
        }
        val pickedIndex = suggestionAt(mouseX.toInt(), mouseY.toInt())
        if (pickedIndex != null) {
            selectedSuggestionIndex = pickedIndex
            groupBox.value = suggestions[pickedIndex]
            updateSuggestions()
            return true
        }
        val handled = super.mouseClicked(mouseX, mouseY, button)
        updateParamSuggestion()
        return handled
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (page == ControllerPage.PARAMS && currentParamSpecs.size > paramBoxes.size) {
            val delta = when {
                scrollY > 0.0 -> -1
                scrollY < 0.0 -> 1
                else -> 0
            }
            if (delta != 0) {
                syncVisibleParamValuesToState()
                val previous = paramScroll
                paramScroll = (paramScroll + delta).coerceIn(0, maxParamScroll())
                if (paramScroll != previous) {
                    writeVisibleParamBoxes()
                    updateParamSuggestion()
                    layoutWidgets()
                }
            }
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics, mouseX, mouseY, partialTick)
        super.render(graphics, mouseX, mouseY, partialTick)
        renderLabels(graphics)
        renderSuggestions(graphics, mouseX, mouseY)
        renderParamSuggestions(graphics, mouseX, mouseY)
    }

    private fun renderLabels(graphics: GuiGraphics) {
        if (page == ControllerPage.PARAMS) {
            renderParamPageLabels(graphics)
            return
        }
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

    private fun renderParamPageLabels(graphics: GuiGraphics) {
        val left = width / 2 - 170
        graphics.drawString(font, title, left, 16, 0xFFFFFF, true)
        graphics.drawString(font, fitTextToWidth(selectedOptionIdLine(), PARAM_CONTENT_WIDTH), left, 38, 0xC8EFC8, true)
        if (currentParamSpecs.isEmpty()) {
            graphics.drawString(font, "\u5f53\u524d Option \u6ca1\u6709\u989d\u5916\u53c2\u6570", left, PARAM_LIST_TOP + 4, 0xE0E0E0, true)
            return
        }
        val visibleRows = visibleParamRowCount()
        val start = paramScroll + 1
        val end = (paramScroll + visibleRows).coerceAtMost(currentParamSpecs.size)
        graphics.drawString(font, "\u53c2\u6570 $start-$end/${currentParamSpecs.size}", left, 52, 0xE0E0E0, true)
        repeat(visibleRows) { row ->
            val actualIndex = actualParamIndex(row)
            val spec = currentParamSpecs.getOrNull(actualIndex) ?: return@repeat
            val rowY = PARAM_LIST_TOP + row * PARAM_ROW_HEIGHT
            graphics.drawString(font, paramLabel(spec), left, rowY + 4, 0xE0E0E0, true)
            if (spec.color) {
                renderColorSwatch(graphics, left + 56, rowY + 4, colorOfParam(row, spec))
                renderColorPalette(graphics, row, spec, left, rowY)
            }
        }
        renderParamScrollBar(graphics, left)
    }

    private fun renderParamScrollBar(graphics: GuiGraphics, left: Int) {
        if (currentParamSpecs.size <= paramBoxes.size || paramBoxes.isEmpty()) return
        val trackHeight = paramBoxes.size * PARAM_ROW_HEIGHT - 4
        if (trackHeight <= 0) return
        val trackX = left + 334
        val trackY = PARAM_LIST_TOP
        graphics.fill(trackX, trackY, trackX + 4, trackY + trackHeight, 0xAA111111.toInt())
        val handleHeight = ((trackHeight * paramBoxes.size.toFloat()) / currentParamSpecs.size.toFloat())
            .roundToInt()
            .coerceIn(12, trackHeight)
        val maxScroll = maxParamScroll().coerceAtLeast(1)
        val handleY = trackY + ((trackHeight - handleHeight) * (paramScroll.toFloat() / maxScroll.toFloat())).roundToInt()
        graphics.fill(trackX, handleY, trackX + 4, handleY + handleHeight, 0xFFE0E0E0.toInt())
    }

    private fun editBox(x: Int, y: Int, width: Int, value: String): EditBox {
        return EditBox(font, x, y, width, 20, Component.empty()).also { box ->
            box.value = value
        }
    }

    private fun updatePacket(): PacketUpdateTestControllerC2S {
        prepareParamState(syncVisible = true)
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
            it.optionParamIndex = currentOptionIndex()
            it.optionParamValues = TestOptionParamCodec.encodeOptionValues(currentParamValues())
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
        updateParamBoxes()
    }

    private fun renderSuggestions(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (page != ControllerPage.MAIN) return
        if (!groupBox.isFocused || suggestions.isEmpty()) return
        val x = groupBox.x
        var y = groupBox.y + groupBox.height + 2
        val end = minOf(suggestions.size, suggestionScroll + maxVisibleSuggestions)
        for (index in suggestionScroll until end) {
            val suggestion = suggestions[index]
            val hovered = mouseX in x..(x + groupBox.width) && mouseY in y..(y + 13)
            val selected = index == selectedSuggestionIndex
            val color = when {
                hovered -> 0xCC224422.toInt()
                selected -> 0xCC1A331A.toInt()
                else -> 0xCC000000.toInt()
            }
            graphics.fill(x, y, x + groupBox.width, y + 13, color)
            graphics.drawString(font, suggestion, x + 3, y + 3, 0xE0FFE0, true)
            y += 13
        }
    }

    private fun suggestionAt(mouseX: Int, mouseY: Int): Int? {
        if (page != ControllerPage.MAIN) return null
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

    private fun updateParamBoxes(syncVisible: Boolean = true) {
        prepareParamState(syncVisible)
        writeVisibleParamBoxes()
        updateParamSuggestion()
        layoutWidgets()
    }

    private fun prepareParamState(syncVisible: Boolean) {
        val key = currentParamSetKey()
        if (syncVisible && key == paramStateKey) {
            syncVisibleParamValuesToState()
        }
        currentParamSpecs = currentOptionParamSpecs()
        val savedValues = currentOptionParamValues()
        if (key != paramStateKey) {
            paramValues.clear()
            paramAbsoluteModes.clear()
            currentParamSpecs.forEach { spec ->
                val savedValue = savedValues[spec.id] ?: spec.defaultValue
                paramValues[spec.id] = stripPositionMode(savedValue)
                paramAbsoluteModes.add(positionModeOf(savedValue, spec) == TestOptionParamPositionMode.ABSOLUTE)
            }
            paramScroll = 0
            paramStateKey = key
        } else {
            val currentIds = currentParamSpecs.mapTo(HashSet()) { it.id }
            val iterator = paramValues.keys.iterator()
            while (iterator.hasNext()) {
                if (iterator.next() !in currentIds) {
                    iterator.remove()
                }
            }
            while (paramAbsoluteModes.size > currentParamSpecs.size) {
                paramAbsoluteModes.removeAt(paramAbsoluteModes.lastIndex)
            }
            currentParamSpecs.forEachIndexed { index, spec ->
                val savedValue = savedValues[spec.id] ?: spec.defaultValue
                paramValues.putIfAbsent(spec.id, stripPositionMode(savedValue))
                if (index >= paramAbsoluteModes.size) {
                    paramAbsoluteModes.add(positionModeOf(savedValue, spec) == TestOptionParamPositionMode.ABSOLUTE)
                }
            }
        }
        clampParamScroll()
    }

    private fun syncVisibleParamValuesToState() {
        if (page != ControllerPage.PARAMS) return
        paramBoxes.forEachIndexed { row, box ->
            val spec = currentParamSpecs.getOrNull(actualParamIndex(row)) ?: return@forEachIndexed
            paramValues[spec.id] = rowParamValue(row, spec)
        }
    }

    private fun writeVisibleParamBoxes() {
        paramBoxes.forEachIndexed { row, box ->
            val spec = currentParamSpecs.getOrNull(actualParamIndex(row))
            if (spec == null) {
                box.value = ""
                clearComponentBoxes(row)
            } else {
                writeRowParamValue(row, spec, paramValues[spec.id] ?: stripPositionMode(spec.defaultValue))
                updateParamButtonLabels(row)
            }
        }
    }

    private fun currentParamSetKey(): String {
        return "${mode.id}|${groupBox.value.trim().lowercase(Locale.ROOT)}|${currentOptionIndex()}"
    }

    private fun actualParamIndex(row: Int): Int {
        return paramScroll + row
    }

    private fun visibleParamRowCount(): Int {
        return (currentParamSpecs.size - paramScroll).coerceAtLeast(0).coerceAtMost(paramBoxes.size)
    }

    private fun maxParamScroll(): Int {
        return (currentParamSpecs.size - paramBoxes.size).coerceAtLeast(0)
    }

    private fun clampParamScroll() {
        paramScroll = paramScroll.coerceIn(0, maxParamScroll())
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

    private fun fitTextToWidth(text: String, maxWidth: Int): String {
        if (font.width(text) <= maxWidth) {
            return text
        }
        val suffix = "..."
        var end = text.length
        while (end > 0 && font.width(text.take(end) + suffix) > maxWidth) {
            end--
        }
        return text.take(end) + suffix
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

    private fun currentOptionParamSpecs(): List<EncodedTestOptionParamSpec> {
        if (mode != BlockTestMode.INDEX) {
            return emptyList()
        }
        val index = currentOptionIndex()
        val optionSpecs = currentGroupOptionParamSpecs()
        return optionSpecs.getOrNull(index)?.let(TestOptionParamCodec::decodeOptionSpecs) ?: emptyList()
    }

    private fun currentOptionParamValues(): Map<String, String> {
        if (mode != BlockTestMode.INDEX) {
            return emptyMap()
        }
        if (!groupBox.value.trim().equals(packet.groupId, ignoreCase = true)) {
            return emptyMap()
        }
        return packet.optionParamValues.getOrNull(currentOptionIndex())
            ?.let(TestOptionParamCodec::decodeOptionValues)
            ?: emptyMap()
    }

    private fun currentGroupOptionParamSpecs(): List<String> {
        val groupId = groupBox.value.trim()
        if (groupId.isBlank()) {
            return emptyList()
        }
        val index = packet.registeredIds.indexOfFirst { id -> id.equals(groupId, ignoreCase = true) }
        if (index in packet.registeredOptionParamSpecs.indices) {
            val encoded = packet.registeredOptionParamSpecs[index]
            return if (encoded.isBlank()) {
                emptyList()
            } else {
                encoded.split(TestOptionParamCodec.OPTION_SEPARATOR)
            }
        }
        return if (groupId.equals(packet.groupId, ignoreCase = true)) packet.optionParamSpecs else emptyList()
    }

    private fun currentOptionIndex(): Int {
        return (indexBox.value.toIntOrNull() ?: packet.selectedIndex).coerceAtLeast(0)
    }

    private fun currentParamValues(): Map<String, String> {
        if (mode != BlockTestMode.INDEX) {
            return emptyMap()
        }
        val savedValues = currentOptionParamValues()
        return currentParamSpecs.mapIndexed { index, spec ->
            val rawValue = paramValues[spec.id]
                ?: stripPositionMode(savedValues[spec.id] ?: spec.defaultValue)
            spec.id to encodeParamValue(spec, index, rawValue)
        }.toMap(LinkedHashMap())
    }

    private fun paramLabel(spec: EncodedTestOptionParamSpec): String {
        val label = spec.displayName.ifBlank { spec.id }
        return if (label.length > 12) label.take(11) + "..." else label
    }

    private fun updateParamSuggestion() {
        if (page != ControllerPage.PARAMS) {
            paramBoxes.forEach { it.setSuggestion(null) }
            paramComponentBoxes.flatten().forEach { it.setSuggestion(null) }
            paramSuggestions = emptyList()
            selectedParamSuggestionIndex = -1
            paramSuggestionScroll = 0
            return
        }
        val focusedRow = focusedEnumParamRowIndex()
        paramBoxes.forEachIndexed { row, box ->
            if (row != focusedRow) box.setSuggestion(null)
        }
        updateVisibleComponentSuggestions()
        if (focusedRow == null) {
            paramSuggestions = emptyList()
            selectedParamSuggestionIndex = -1
            paramSuggestionScroll = 0
            return
        }
        val focusedIndex = actualParamIndex(focusedRow)
        val spec = currentParamSpecs.getOrNull(focusedIndex) ?: return
        if (usesComponentBoxes(spec)) return
        val box = paramBoxes[focusedRow]
        val input = box.value.trim()
        paramSuggestions = spec.suggestions
            .filter { candidate -> input.isBlank() || candidate.startsWith(input, ignoreCase = true) }
            .filter { candidate -> input.isBlank() || !candidate.equals(input, ignoreCase = true) }
        selectedParamSuggestionIndex = if (paramSuggestions.isEmpty()) -1 else 0
        paramSuggestionScroll = 0
        val first = paramSuggestions.firstOrNull()
        box.setSuggestion(if (input.isNotBlank() && first?.startsWith(input, ignoreCase = true) == true) first.drop(input.length) else null)
    }

    private fun renderParamSuggestions(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (page != ControllerPage.PARAMS) return
        val focusedRow = focusedEnumParamRowIndex() ?: return
        if (paramSuggestions.isEmpty()) return
        val box = paramBoxes[focusedRow]
        val x = box.x
        var y = box.y + box.height + 2
        val end = minOf(paramSuggestions.size, paramSuggestionScroll + maxVisibleSuggestions)
        for (index in paramSuggestionScroll until end) {
            val suggestion = paramSuggestions[index]
            val hovered = mouseX in x..(x + box.width) && mouseY in y..(y + 13)
            val selected = index == selectedParamSuggestionIndex
            val color = when {
                hovered -> 0xCC224422.toInt()
                selected -> 0xCC1A331A.toInt()
                else -> 0xCC000000.toInt()
            }
            graphics.fill(x, y, x + box.width, y + 13, color)
            graphics.drawString(font, suggestion, x + 3, y + 3, 0xE0FFE0, true)
            y += 13
        }
    }

    private fun paramSuggestionAt(mouseX: Int, mouseY: Int): Int? {
        if (page != ControllerPage.PARAMS) return null
        val focusedRow = focusedEnumParamRowIndex() ?: return null
        if (paramSuggestions.isEmpty()) return null
        val box = paramBoxes[focusedRow]
        val x = box.x
        var y = box.y + box.height + 2
        val end = minOf(paramSuggestions.size, paramSuggestionScroll + maxVisibleSuggestions)
        for (index in paramSuggestionScroll until end) {
            if (mouseX in x..(x + box.width) && mouseY in y..(y + 13)) return index
            y += 13
        }
        return null
    }

    private fun acceptSelectedParamSuggestion(): Boolean {
        val focusedRow = focusedEnumParamRowIndex() ?: return false
        val index = selectedParamSuggestionIndex.takeIf { it in paramSuggestions.indices } ?: return false
        paramBoxes[focusedRow].value = paramSuggestions[index]
        updateParamSuggestion()
        return true
    }

    private fun moveSelectedParamSuggestion(delta: Int) {
        if (paramSuggestions.isEmpty()) return
        selectedParamSuggestionIndex = if (selectedParamSuggestionIndex in paramSuggestions.indices) {
            (selectedParamSuggestionIndex + delta).coerceIn(0, paramSuggestions.lastIndex)
        } else {
            0
        }
        ensureSelectedParamSuggestionVisible()
    }

    private fun ensureSelectedParamSuggestionVisible() {
        if (selectedParamSuggestionIndex !in paramSuggestions.indices) {
            paramSuggestionScroll = 0
            return
        }
        if (selectedParamSuggestionIndex < paramSuggestionScroll) {
            paramSuggestionScroll = selectedParamSuggestionIndex
        }
        if (selectedParamSuggestionIndex >= paramSuggestionScroll + maxVisibleSuggestions) {
            paramSuggestionScroll = selectedParamSuggestionIndex - maxVisibleSuggestions + 1
        }
        paramSuggestionScroll = paramSuggestionScroll.coerceIn(0, (paramSuggestions.size - maxVisibleSuggestions).coerceAtLeast(0))
    }

    private fun focusedEnumParamIndex(): Int? {
        val row = focusedEnumParamRowIndex() ?: return null
        return actualParamIndex(row)
    }

    private fun focusedEnumParamRowIndex(): Int? {
        val row = paramBoxes.indexOfFirst { it.isFocused }
        if (row !in paramBoxes.indices) return null
        val index = actualParamIndex(row)
        if (index !in currentParamSpecs.indices) return null
        return row.takeIf { currentParamSpecs[index].isEditor(TestOptionParamEditorKind.ENUM) }
    }

    private fun toggleParamMode(row: Int) {
        val index = actualParamIndex(row)
        if (index !in paramAbsoluteModes.indices) return
        paramAbsoluteModes[index] = !paramAbsoluteModes[index]
        updateParamButtonLabels(row)
    }

    private fun updateParamButtonLabels(row: Int) {
        val index = actualParamIndex(row)
        if (row !in paramModeButtons.indices || index !in paramAbsoluteModes.indices) return
        paramModeButtons[row].message = Component.literal(if (paramAbsoluteModes[index]) "绝对" else "相对")
    }

    private fun startParamPick(row: Int) {
        val index = actualParamIndex(row)
        val spec = currentParamSpecs.getOrNull(index) ?: return
        if (!spec.pickable) return
        TestControllerPickClient.begin(
            screenPacket = packet,
            packet = updatePacket(),
            kind = TestControllerPickKind.PARAM_POSITION,
            precisionUnlocked = precisionUnlocked,
            paramOptionIndex = currentOptionIndex(),
            paramId = spec.id,
            paramComponentCount = spec.componentCount.coerceAtLeast(2),
            paramAbsolute = paramAbsoluteModes.getOrElse(index) {
                positionModeOf(spec.defaultValue, spec) == TestOptionParamPositionMode.ABSOLUTE
            }
        )
        onClose()
    }

    private fun applyNextPaletteColor(row: Int) = snapColorToPalette(row)

    private fun snapColorToPalette(row: Int) {
        val index = actualParamIndex(row)
        val spec = currentParamSpecs.getOrNull(index)?.takeIf { it.color } ?: return
        val current = parseColorComponents(rowParamValue(row, spec), spec.componentCount) ?: return
        val nearest = COLOR_PALETTE.minBy { palette -> colorDistance(current, palette) }
        writeRowParamValue(row, spec, formatColor(nearest, spec.componentCount, current.getOrNull(3)))
    }

    private fun renderColorSwatch(graphics: GuiGraphics, x: Int, y: Int, color: Int?) {
        graphics.fill(x, y, x + 14, y + 14, 0xFF111111.toInt())
        graphics.fill(x + 1, y + 1, x + 13, y + 13, color ?: 0xFF444444.toInt())
    }

    private fun renderColorPalette(
        graphics: GuiGraphics,
        row: Int,
        spec: EncodedTestOptionParamSpec,
        left: Int,
        rowY: Int
    ) {
        val current = parseColorComponents(rowParamValue(row, spec), spec.componentCount)
        COLOR_PALETTE.forEachIndexed { index, color ->
            val x = paletteCellX(left, index)
            val y = paletteCellY(rowY, index)
            val selected = current != null && colorDistance(current, color) <= 0.0001f
            graphics.fill(x - 1, y - 1, x + PALETTE_CELL_SIZE + 1, y + PALETTE_CELL_SIZE + 1, if (selected) 0xFFFFFFFF.toInt() else 0xFF111111.toInt())
            graphics.fill(x, y, x + PALETTE_CELL_SIZE, y + PALETTE_CELL_SIZE, color.toColorInt())
        }
    }

    private fun colorPaletteAt(mouseX: Int, mouseY: Int): Pair<Int, List<Float>>? {
        if (page != ControllerPage.PARAMS) return null
        val left = width / 2 - 170
        repeat(visibleParamRowCount()) { row ->
            val spec = currentParamSpecs.getOrNull(actualParamIndex(row)) ?: return@repeat
            if (!spec.color) return@repeat
            val rowY = PARAM_LIST_TOP + row * PARAM_ROW_HEIGHT
            COLOR_PALETTE.forEachIndexed { index, color ->
                val x = paletteCellX(left, index)
                val y = paletteCellY(rowY, index)
                if (mouseX in (x - 1)..(x + PALETTE_CELL_SIZE + 1) &&
                    mouseY in (y - 1)..(y + PALETTE_CELL_SIZE + 1)
                ) {
                    return row to color
                }
            }
        }
        return null
    }

    private fun applyPaletteColor(row: Int, color: List<Float>) {
        val index = actualParamIndex(row)
        val spec = currentParamSpecs.getOrNull(index)?.takeIf { it.color } ?: return
        val current = parseColorComponents(rowParamValue(row, spec), spec.componentCount)
        writeRowParamValue(row, spec, formatColor(color, spec.componentCount, current?.getOrNull(3)))
    }

    private fun paletteCellX(left: Int, index: Int): Int {
        return left + PALETTE_LEFT_OFFSET + (index % PALETTE_COLUMNS) * (PALETTE_CELL_SIZE + PALETTE_CELL_GAP)
    }

    private fun paletteCellY(rowY: Int, index: Int): Int {
        return rowY + 1 + (index / PALETTE_COLUMNS) * (PALETTE_CELL_SIZE + PALETTE_CELL_GAP)
    }

    private fun colorOfParam(index: Int, spec: EncodedTestOptionParamSpec): Int? {
        return parseColorComponents(rowParamValue(index, spec), spec.componentCount)?.toColorInt()
    }

    private fun rowParamValue(row: Int, spec: EncodedTestOptionParamSpec): String {
        if (!usesComponentBoxes(spec)) {
            return paramBoxes.getOrNull(row)?.value.orEmpty()
        }
        return paramComponentBoxes.getOrNull(row)
            .orEmpty()
            .take(vectorComponentCount(spec))
            .joinToString(",") { it.value.trim() }
    }

    private fun writeRowParamValue(row: Int, spec: EncodedTestOptionParamSpec, value: String) {
        if (!usesComponentBoxes(spec)) {
            paramBoxes[row].value = value
            clearComponentBoxes(row)
            return
        }
        paramBoxes[row].value = ""
        val components = componentTexts(value, spec)
        val boxes = paramComponentBoxes.getOrNull(row).orEmpty()
        boxes.forEachIndexed { index, box ->
            box.value = components.getOrNull(index).orEmpty()
        }
    }

    private fun clearComponentBoxes(row: Int) {
        paramComponentBoxes.getOrNull(row).orEmpty().forEach { box ->
            box.value = ""
            box.setSuggestion(null)
        }
    }

    private fun componentTexts(value: String, spec: EncodedTestOptionParamSpec): List<String> {
        if (spec.color) {
            parseColorComponents(value, spec.componentCount)?.let { components ->
                return components.take(vectorComponentCount(spec)).map { formatFloat(it) }
            }
        }
        val text = stripPositionMode(value).trim()
        if (text.isBlank()) return emptyList()
        return text
            .replace("(", " ")
            .replace(")", " ")
            .replace("[", " ")
            .replace("]", " ")
            .replace(";", " ")
            .replace(",", " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(vectorComponentCount(spec))
    }

    private fun updateVisibleComponentSuggestions() {
        paramComponentBoxes.forEachIndexed { row, boxes ->
            val spec = currentParamSpecs.getOrNull(actualParamIndex(row))
            if (spec == null || !usesComponentBoxes(spec) || page != ControllerPage.PARAMS) {
                boxes.forEach { it.setSuggestion(null) }
                return@forEachIndexed
            }
            boxes.forEachIndexed { componentIndex, box ->
                box.setSuggestion(if (box.value.isBlank()) componentLabel(spec, componentIndex) else null)
            }
        }
    }

    private fun componentLabel(spec: EncodedTestOptionParamSpec, index: Int): String {
        return spec.componentLabels.getOrNull(index)?.uppercase(Locale.ROOT)
            ?: when (index) {
                0 -> "X"
                1 -> "Y"
                2 -> "Z"
                3 -> "W"
                else -> ""
            }
    }

    private fun usesComponentBoxes(spec: EncodedTestOptionParamSpec): Boolean {
        return spec.isEditor(TestOptionParamEditorKind.VECTOR) && spec.componentCount > 1
    }

    private fun vectorComponentCount(spec: EncodedTestOptionParamSpec): Int {
        return spec.componentCount.coerceIn(2, PARAM_MAX_COMPONENT_BOXES)
    }

    private fun encodeParamValue(spec: EncodedTestOptionParamSpec, index: Int, rawValue: String): String {
        val trimmed = rawValue.trim()
        if (!spec.pickable) {
            return trimmed
        }
        val mode = if (paramAbsoluteModes.getOrElse(index) { false }) {
            TestOptionParamPositionMode.ABSOLUTE
        } else {
            TestOptionParamPositionMode.RELATIVE
        }
        return "${mode.id}:$trimmed"
    }

    private fun positionModeOf(rawValue: String, spec: EncodedTestOptionParamSpec): TestOptionParamPositionMode {
        val prefix = rawValue.substringBefore(':', "").lowercase(Locale.ROOT)
        return when (prefix) {
            TestOptionParamPositionMode.RELATIVE.id, "rel" -> TestOptionParamPositionMode.RELATIVE
            TestOptionParamPositionMode.ABSOLUTE.id, "abs" -> TestOptionParamPositionMode.ABSOLUTE
            else -> TestOptionParamPositionMode.fromId(spec.defaultPositionMode)
        }
    }

    private fun stripPositionMode(rawValue: String): String {
        val colon = rawValue.indexOf(':')
        if (colon <= 0) return rawValue
        val prefix = rawValue.substring(0, colon).lowercase(Locale.ROOT)
        return when (prefix) {
            TestOptionParamPositionMode.RELATIVE.id,
            TestOptionParamPositionMode.ABSOLUTE.id,
            "rel",
            "abs" -> rawValue.substring(colon + 1)
            else -> rawValue
        }
    }

    private fun parseColorComponents(rawValue: String, componentCount: Int): List<Float>? {
        val text = stripPositionMode(rawValue).trim()
        if (text.isBlank()) return null
        parseHexColor(text, componentCount)?.let { return it }
        val parts = text
            .replace("(", " ")
            .replace(")", " ")
            .replace("[", " ")
            .replace("]", " ")
            .replace(";", " ")
            .replace(",", " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        if (parts.size < minOf(componentCount, 4)) return null
        val values = parts.take(componentCount.coerceIn(3, 4)).map { it.toFloatOrNull() ?: return null }
        val normalized = if (values.any { it > 1f }) values.map { (it / 255f).coerceIn(0f, 1f) } else values.map { it.coerceIn(0f, 1f) }
        return normalized
    }

    private fun parseHexColor(text: String, componentCount: Int): List<Float>? {
        if (componentCount !in 3..4) return null
        val hex = when {
            text.startsWith("#") -> text.substring(1)
            text.startsWith("0x", ignoreCase = true) -> text.substring(2)
            else -> return null
        }
        if (hex.length != 6 && hex.length != 8) return null
        val value = hex.toLongOrNull(16) ?: return null
        val components = if (hex.length == 6) {
            listOf(
                ((value shr 16) and 0xFF).toInt(),
                ((value shr 8) and 0xFF).toInt(),
                (value and 0xFF).toInt(),
                255
            )
        } else {
            listOf(
                ((value shr 24) and 0xFF).toInt(),
                ((value shr 16) and 0xFF).toInt(),
                ((value shr 8) and 0xFF).toInt(),
                (value and 0xFF).toInt()
            )
        }
        return components.take(componentCount).map { (it / 255f).coerceIn(0f, 1f) }
    }

    private fun colorDistance(first: List<Float>, second: List<Float>): Float {
        return (first.getOrElse(0) { 0f } - second[0]).let { it * it } +
                (first.getOrElse(1) { 0f } - second[1]).let { it * it } +
                (first.getOrElse(2) { 0f } - second[2]).let { it * it }
    }

    private fun formatColor(color: List<Float>, componentCount: Int, alpha: Float?): String {
        val components = ArrayList<Float>()
        components.add(color[0])
        components.add(color[1])
        components.add(color[2])
        if (componentCount >= 4) {
            components.add(alpha ?: color.getOrElse(3) { 1f })
        }
        return components.joinToString(",") { formatFloat(it.coerceIn(0f, 1f)) }
    }

    private fun List<Float>.toColorInt(): Int {
        val r = (getOrElse(0) { 0f }.coerceIn(0f, 1f) * 255f).roundToInt()
        val g = (getOrElse(1) { 0f }.coerceIn(0f, 1f) * 255f).roundToInt()
        val b = (getOrElse(2) { 0f }.coerceIn(0f, 1f) * 255f).roundToInt()
        val a = (getOrElse(3) { 1f }.coerceIn(0f, 1f) * 255f).roundToInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun EncodedTestOptionParamSpec.isEditor(kind: TestOptionParamEditorKind): Boolean {
        return editorKind.equals(kind.name, ignoreCase = true)
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

    private fun formatFloat(value: Float): String {
        return formatDouble(value.toDouble())
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

    private fun visibleParamRowCapacity(): Int {
        val availableHeight = height - PARAM_LIST_TOP - PARAM_BOTTOM_RESERVED
        return (availableHeight / PARAM_ROW_HEIGHT).coerceIn(1, PARAM_MAX_VISIBLE_ROWS)
    }

    private fun layoutWidgets() {
        if (!::groupBox.isInitialized) return
        val left = width / 2 - 170
        val mainPage = page == ControllerPage.MAIN
        val paramPage = page == ControllerPage.PARAMS
        val indexVisible = mainPage && mode == BlockTestMode.INDEX
        val offsetY = if (indexVisible) 128 else 90
        val forwardY = offsetY + 28
        val boxY = forwardY + 28
        val buttonsY = boxY + 34

        groupBox.setX(left + 92)
        groupBox.setY(34)
        groupBox.visible = mainPage
        groupBox.active = mainPage
        modeButton.setX(left + 92)
        modeButton.setY(62)
        modeButton.visible = mainPage
        modeButton.active = mainPage
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
        paramsButton.setX(left + 316)
        paramsButton.setY(90)
        paramsButton.message = Component.literal("\u53c2\u6570(${currentParamSpecs.size})")
        paramsButton.visible = indexVisible && currentParamSpecs.isNotEmpty()
        paramsButton.active = paramsButton.visible

        val visibleParamRows = if (paramPage) visibleParamRowCount() else 0
        paramBoxes.forEachIndexed { row, box ->
            val visible = row < visibleParamRows
            val actualIndex = actualParamIndex(row)
            val spec = currentParamSpecs.getOrNull(actualIndex)
            val rowY = PARAM_LIST_TOP + row * PARAM_ROW_HEIGHT
            val vectorInputs = spec?.let(::usesComponentBoxes) == true
            box.setX(left + 92)
            box.setY(rowY)
            box.visible = visible && !vectorInputs
            box.active = box.visible
            layoutComponentBoxes(row, rowY, left, visible && vectorInputs, spec)
            paramModeButtons[row].setX(left + 248)
            paramModeButtons[row].setY(rowY)
            paramModeButtons[row].visible = visible && spec?.pickable == true && spec.allowAbsolute
            paramModeButtons[row].active = paramModeButtons[row].visible
            paramPickButtons[row].setX(left + 286)
            paramPickButtons[row].setY(rowY)
            paramPickButtons[row].visible = visible && spec?.pickable == true
            paramPickButtons[row].active = paramPickButtons[row].visible
            paramPaletteButtons[row].setX(left + 248)
            paramPaletteButtons[row].setY(rowY)
            paramPaletteButtons[row].visible = false
            paramPaletteButtons[row].active = false
            paramSnapButtons[row].setX(left + 290)
            paramSnapButtons[row].setY(rowY)
            paramSnapButtons[row].visible = visible && spec?.color == true
            paramSnapButtons[row].active = paramSnapButtons[row].visible
        }

        setRow(offsetY, offsetXBox, offsetYBox, offsetZBox, offsetPickButton)
        setRow(forwardY, forwardXBox, forwardYBox, forwardZBox, forwardPickButton)
        setRow(boxY, widthBox, heightBox, depthBox, precisionButton)
        listOf(
            offsetXBox, offsetYBox, offsetZBox,
            forwardXBox, forwardYBox, forwardZBox,
            widthBox, heightBox, depthBox
        ).forEach {
            it.visible = mainPage
            it.active = mainPage
        }
        listOf(offsetPickButton, forwardPickButton, precisionButton).forEach {
            it.visible = mainPage
            it.active = mainPage
        }

        if (paramPage) {
            val bottomY = height - 28
            backButton.setX(left)
            backButton.setY(bottomY)
            saveButton.setX(left + 68)
            saveButton.setY(bottomY)
            startButton.setX(left + 150)
            startButton.setY(bottomY)
            stopButton.setX(left + 244)
            stopButton.setY(bottomY)
        } else {
            saveButton.setX(left + 44)
            saveButton.setY(buttonsY)
            startButton.setX(left + 126)
            startButton.setY(buttonsY)
            stopButton.setX(left + 220)
            stopButton.setY(buttonsY)
        }
        backButton.visible = paramPage
        backButton.active = paramPage
        saveButton.visible = true
        saveButton.active = true
        startButton.visible = true
        startButton.active = true
        stopButton.visible = true
        stopButton.active = true
    }

    private fun layoutComponentBoxes(
        row: Int,
        rowY: Int,
        left: Int,
        visible: Boolean,
        spec: EncodedTestOptionParamSpec?
    ) {
        val boxes = paramComponentBoxes.getOrNull(row).orEmpty()
        val componentCount = spec?.let(::vectorComponentCount) ?: 0
        val gap = 3
        val inputWidth = if (componentCount > 0) {
            ((PARAM_INPUT_WIDTH - gap * (componentCount - 1)) / componentCount).coerceAtLeast(24)
        } else {
            PARAM_INPUT_WIDTH
        }
        boxes.forEachIndexed { index, box ->
            box.setX(left + 92 + index * (inputWidth + gap))
            box.setY(rowY)
            box.width = inputWidth
            box.visible = visible && index < componentCount
            box.active = box.visible
            box.setSuggestion(if (box.visible && spec != null && box.value.isBlank()) componentLabel(spec, index) else null)
        }
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

    private enum class ControllerPage {
        MAIN,
        PARAMS
    }

    companion object {
        private const val PARAM_ROW_HEIGHT = 24
        private const val PARAM_LIST_TOP = 78
        private const val PARAM_BOTTOM_RESERVED = 60
        private const val PARAM_MAX_VISIBLE_ROWS = 10
        private const val PARAM_CONTENT_WIDTH = 334
        private const val PARAM_INPUT_WIDTH = 150
        private const val PARAM_MAX_COMPONENT_BOXES = 4
        private const val PALETTE_LEFT_OFFSET = 248
        private const val PALETTE_COLUMNS = 4
        private const val PALETTE_CELL_SIZE = 7
        private const val PALETTE_CELL_GAP = 1

        private val COLOR_PALETTE: List<List<Float>> = listOf(
            listOf(1f, 1f, 1f, 1f),
            listOf(0.75f, 0.75f, 0.75f, 1f),
            listOf(0.35f, 0.35f, 0.35f, 1f),
            listOf(0f, 0f, 0f, 1f),
            listOf(1f, 0f, 0f, 1f),
            listOf(0f, 1f, 0f, 1f),
            listOf(0f, 0f, 1f, 1f),
            listOf(1f, 0.85f, 0.25f, 1f),
            listOf(0f, 1f, 1f, 1f),
            listOf(1f, 0f, 1f, 1f),
            listOf(0.35f, 0.70f, 1f, 1f),
            listOf(1f, 0.5f, 0f, 1f)
        )
    }
}
