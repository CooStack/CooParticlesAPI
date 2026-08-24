package cn.coostack.cooparticlesapi.test.block.client

import cn.coostack.cooparticlesapi.blocks.defaultTestControllerGroupId
import cn.coostack.cooparticlesapi.network.packet.api.CooClientPacketManager
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketOpenTestControllerScreenS2C
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketReviewTestControllerC2S
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketStartTestControllerC2S
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketStopTestControllerC2S
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketUpdateTestControllerC2S
import cn.coostack.cooparticlesapi.test.api.EncodedTestOptionParamSpec
import cn.coostack.cooparticlesapi.test.api.TestOptionParamCodec
import cn.coostack.cooparticlesapi.test.api.TestOptionParamEditorKind
import cn.coostack.cooparticlesapi.test.api.TestOptionParamPositionMode
import cn.coostack.cooparticlesapi.test.api.formatTestOptionRgbInputs
import cn.coostack.cooparticlesapi.test.api.normalizeTestOptionColorHexInput
import cn.coostack.cooparticlesapi.test.api.parseTestOptionRgbInputs
import cn.coostack.cooparticlesapi.test.block.BlockTestMode
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.lwjgl.glfw.GLFW
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.pow
import kotlin.math.round

/**
 * 编辑测试控制器状态并提供分组、参数补全。
 *
 * 示例：由 [TestControllerClientScreens.openController] 使用服务端状态创建。
 * 禁止在服务端加载此界面或用客户端字段替代服务端状态。
 *
 * @property packet 服务端发送的控制器状态
 * @param openParamPage 是否在初始化后直接显示参数页
 */
class TestControllerScreen internal constructor(
    private val packet: PacketOpenTestControllerScreenS2C,
    openParamPage: Boolean = false,
    private val sharedHistory: TestControllerUndoHistory<TestControllerPacketDrafts.TestControllerConfigSnapshot>? = null,
    private val initialPrecisionUnlocked: Boolean = false,
) : Screen(Component.literal("测试方块")) {
    /** 当前测试组的 MOD 命名空间输入。 */
    private lateinit var modIdBox: EditBox

    /** 当前测试组在 MOD 命名空间下的 path 输入。 */
    private lateinit var groupPathBox: EditBox
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
    /**
     * 位置通道的静态或动态选择按钮。
     * 示例：主页面显示“动态”；禁止把它放到独立动态页。
     */
    private lateinit var positionModeButton: Button

    /**
     * forward 通道的静态或动态选择按钮。
     * 示例：主页面显示“静态”；禁止用它修改位置通道。
     */
    private lateinit var forwardModeButton: Button

    /**
     * 位置通道启用动态时显示的曲线编辑按钮。
     * 示例：点击后打开位置曲线；禁止在静态状态显示。
     */
    private lateinit var positionEditButton: Button

    /**
     * forward 通道启用动态时显示的曲线编辑按钮。
     * 示例：点击后打开 forward 曲线；禁止在静态状态显示。
     */
    private lateinit var forwardEditButton: Button
    private lateinit var saveButton: Button
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var reviewPassButton: Button
    private lateinit var reviewFailButton: Button
    private lateinit var reviewSkipButton: Button
    private lateinit var paramsButton: Button
    private lateinit var backButton: Button
    private lateinit var colorHexBox: EditBox
    private lateinit var undoButton: Button
    private lateinit var redoButton: Button

    private val paramBoxes = ArrayList<EditBox>()
    private val paramComponentBoxes = ArrayList<List<EditBox>>()
    private val paramModeButtons = ArrayList<Button>()
    private val paramPickButtons = ArrayList<Button>()
    private val colorRgbBoxes = ArrayList<EditBox>()
    private val paramAbsoluteModes = ArrayList<Boolean>()
    private val paramValues = LinkedHashMap<String, String>()
    /** 服务端按注册顺序同步的合法方块测试组 ID。 */
    private val registeredGroupIds = packet.registeredIds.mapNotNull(ResourceLocation::tryParse)
    private val maxVisibleSuggestions = 3
    private var mode = BlockTestMode.fromId(packet.mode)
    private var repeatIndex = packet.repeatIndex
    private var precisionUnlocked = initialPrecisionUnlocked
    private val history = sharedHistory ?: TestControllerUndoHistory(
        initial = TestControllerPacketDrafts.snapshotFrom(packet)
    )
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
    private var colorPickerRow = -1
    private var colorPickerHue = 0f
    private var colorPickerSaturation = 0f
    private var colorPickerValue = 1f
    private var colorPickerAlpha = 1f
    private var colorPickerDrag = ColorPickerDrag.NONE
    private var updatingColorHex = false
    private var updatingColorRgb = false
    /** 防止自动写入唯一 path 时再次进入输入响应回调。 */
    private var updatingGroupInputs = false

    /**
     * 标记当前是否正由缩放后的 [Screen.render] 绘制内容控件。
     *
     * 示例：该标记为 `true` 时跳过 [Screen.render] 内部的第二次背景绘制。
     * 禁止在一次 render 调用结束后保留该状态。
     */
    private var renderingScaledContent = false

    /**
     * 当前真实 GUI 尺寸对应的内容画布。
     *
     * 示例：自动 GUI scale 产生窄视口时，控件仍在较大的内容坐标系中排版。
     * 禁止把它用于未缩放的背景绘制。
     */
    private lateinit var viewport: TestControllerScreenViewport

    /**
     * 内容布局使用的虚拟宽度。
     *
     * 示例：主界面用它居中字段和判断动态控件是否同行；禁止用真实 [width] 替代。
     */
    private val layoutWidth: Int
        get() = viewport.width

    /**
     * 内容布局使用的虚拟高度。
     *
     * 示例：参数页用它计算可见行数和底部按钮位置；禁止用真实 [height] 替代。
     */
    private val layoutHeight: Int
        get() = viewport.height

    /**
     * 保存主界面尚未提交的位置与 forward 动态配置。
     *
     * 示例：切换位置为动态后，打开曲线编辑器会把该状态带入草稿。
     * 禁止把该草稿当作服务端已经保存的配置。
     */
    private val animationDraft = TestControllerPacketDrafts.draftFrom(packet)

    /**
     * 创建主界面、参数页和动态通道控件。
     *
     * 示例：位置与 Forward 行右侧分别显示静态或动态选择按钮。
     * 禁止在初始化阶段发送更新包；用户点击保存或开始后才提交。
     */
    override fun init() {
        viewport = TestControllerScreenViewport.calculate(width, height)
        val left = layoutWidth / 2 - 170
        var y = 34
        paramBoxes.clear()
        paramComponentBoxes.clear()
        paramModeButtons.clear()
        paramPickButtons.clear()
        colorRgbBoxes.clear()
        val initialGroupId = ResourceLocation.tryParse(packet.groupId) ?: defaultTestControllerGroupId()
        modIdBox = editBox(left + 52, y, 100, initialGroupId.namespace)
        groupPathBox = editBox(left + 190, y, 122, initialGroupId.path)
        modIdBox.setMaxLength(256)
        groupPathBox.setMaxLength(256)
        modIdBox.setResponder { updateGroupSuggestions(completeSinglePath = true) }
        groupPathBox.setResponder { updateGroupSuggestions() }
        addRenderableWidget(modIdBox)
        addRenderableWidget(groupPathBox)

        undoButton = Button.builder(Component.literal("撤回")) {
            recordCurrentState()
            restoreHistory(history.undo())
        }.bounds(layoutWidth - 108, 8, 48, 20).build()
        redoButton = Button.builder(Component.literal("重做")) {
            recordCurrentState()
            restoreHistory(history.redo())
        }.bounds(layoutWidth - 56, 8, 48, 20).build()
        addRenderableWidget(undoButton)
        addRenderableWidget(redoButton)

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

        positionModeButton = Button.builder(Component.literal(animationModeLabel(animationDraft.positionDynamic))) {
            animationDraft.positionDynamic = !animationDraft.positionDynamic
            updateAnimationButtons()
            layoutWidgets()
        }.bounds(0, 0, ANIMATION_MODE_WIDTH, 20).build()
        addRenderableWidget(positionModeButton)

        forwardModeButton = Button.builder(Component.literal(animationModeLabel(animationDraft.forwardDynamic))) {
            animationDraft.forwardDynamic = !animationDraft.forwardDynamic
            updateAnimationButtons()
            layoutWidgets()
        }.bounds(0, 0, ANIMATION_MODE_WIDTH, 20).build()
        addRenderableWidget(forwardModeButton)

        positionEditButton = Button.builder(Component.literal("编辑")) {
            openCurveEditor(TestControllerTrackKind.POSITION)
        }.bounds(0, 0, ANIMATION_EDIT_WIDTH, 20).build()
        addRenderableWidget(positionEditButton)

        forwardEditButton = Button.builder(Component.literal("编辑")) {
            openCurveEditor(TestControllerTrackKind.FORWARD)
        }.bounds(0, 0, ANIMATION_EDIT_WIDTH, 20).build()
        addRenderableWidget(forwardEditButton)

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

        reviewPassButton = reviewButton("通过", PacketReviewTestControllerC2S.PASS, left + 44, y, 74)
        reviewFailButton = reviewButton("失败", PacketReviewTestControllerC2S.FAIL, left + 126, y, 86)
        reviewSkipButton = reviewButton("跳过", PacketReviewTestControllerC2S.SKIP, left + 220, y, 86)
        addRenderableWidget(reviewPassButton)
        addRenderableWidget(reviewFailButton)
        addRenderableWidget(reviewSkipButton)

        backButton = Button.builder(Component.literal("\u8fd4\u56de")) {
            syncVisibleParamValuesToState()
            page = ControllerPage.MAIN
            updateParamSuggestion()
            layoutWidgets()
        }.bounds(left, y, 58, 20).build()
        addRenderableWidget(backButton)

        colorHexBox = editBox(0, 0, COLOR_HEX_INPUT_WIDTH, "")
        colorHexBox.setMaxLength(10)
        colorHexBox.setResponder(::onColorHexChanged)
        addRenderableWidget(colorHexBox)
        repeat(3) {
            val rgbBox = editBox(0, 0, COLOR_RGB_INPUT_WIDTH, "")
            rgbBox.setMaxLength(3)
            rgbBox.setFilter { value ->
                value.isEmpty() || value.length <= 3 &&
                        value.all { character -> character in '0'..'9' } &&
                        value.toIntOrNull()?.let { it in 0..255 } == true
            }
            rgbBox.setResponder { onColorRgbChanged() }
            colorRgbBoxes.add(rgbBox)
            addRenderableWidget(rgbBox)
        }

        updateGroupSuggestions()
        updateIndexSuggestion()
        updateParamBoxes(syncVisible = false)
        updateAnimationButtons()
        layoutWidgets()
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (Screen.hasControlDown()) {
            if (keyCode == GLFW.GLFW_KEY_Z && Screen.hasShiftDown()) {
                recordCurrentState()
                restoreHistory(history.redo())
                return true
            }
            if (keyCode == GLFW.GLFW_KEY_Z) {
                recordCurrentState()
                restoreHistory(history.undo())
                return true
            }
            if (keyCode == GLFW.GLFW_KEY_Y) {
                recordCurrentState()
                restoreHistory(history.redo())
                return true
            }
        }
        if (colorPickerRow >= 0) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeColorPicker()
                return true
            }
            if (keyCode == GLFW.GLFW_KEY_TAB) {
                moveColorInputFocus(if (modifiers and GLFW.GLFW_MOD_SHIFT != 0) -1 else 1)
                return true
            }
            return colorInputBoxes()
                .firstOrNull { it.isFocused }
                ?.keyPressed(keyCode, scanCode, modifiers)
                ?: false
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && page == ControllerPage.PARAMS) {
            syncVisibleParamValuesToState()
            page = ControllerPage.MAIN
            updateParamSuggestion()
            layoutWidgets()
            return true
        }
        if (focusedGroupBox() != null && suggestions.isNotEmpty()) {
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
        val previousGroupBox = focusedGroupBox()
        val handled = super.keyPressed(keyCode, scanCode, modifiers)
        if (focusedGroupBox() !== previousGroupBox) {
            updateGroupSuggestions()
        }
        return handled
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        val handled = super.charTyped(codePoint, modifiers)
        if (handled) recordCurrentState()
        return handled
    }

    /**
     * 把真实鼠标坐标还原到内容坐标系后处理点击。
     *
     * 示例：缩放后的按钮仍按其视觉边界响应点击。
     * 禁止把真实坐标直接传给内容控件，否则小视口下命中位置会偏移。
     */
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val contentMouseX = viewport.unscale(mouseX)
        val contentMouseY = viewport.unscale(mouseY)
        if (colorPickerRow >= 0) {
            if (handleColorPickerClick(contentMouseX, contentMouseY, button)) {
                return true
            }
            if (isInsideColorPicker(contentMouseX, contentMouseY)) {
                val clickedInput = colorInputBoxes().firstOrNull {
                    it.mouseClicked(contentMouseX, contentMouseY, button)
                }
                if (clickedInput != null) {
                    setFocused(clickedInput)
                }
                return true
            }
            closeColorPicker()
            return true
        }
        val swatchRow = colorSwatchAt(contentMouseX.toInt(), contentMouseY.toInt())
        if (swatchRow != null) {
            openColorPicker(swatchRow)
            return true
        }
        val pickedParamIndex = paramSuggestionAt(contentMouseX.toInt(), contentMouseY.toInt())
        if (pickedParamIndex != null) {
            selectedParamSuggestionIndex = pickedParamIndex
            acceptSelectedParamSuggestion()
            return true
        }
        val pickedIndex = suggestionAt(contentMouseX.toInt(), contentMouseY.toInt())
        if (pickedIndex != null) {
            selectedSuggestionIndex = pickedIndex
            acceptGroupSuggestion(suggestions[pickedIndex])
            return true
        }
        val previousGroupBox = focusedGroupBox()
        val handled = super.mouseClicked(contentMouseX, contentMouseY, button)
        if (focusedGroupBox() !== previousGroupBox) {
            updateGroupSuggestions()
        }
        updateParamSuggestion()
        return handled
    }

    /**
     * 使用内容坐标和内容距离处理拖动。
     *
     * 示例：颜色选择器在缩放后仍跟随鼠标连续变化。
     * 禁止只转换当前位置而保留原始拖动距离。
     */
    override fun mouseDragged(
        mouseX: Double,
        mouseY: Double,
        button: Int,
        dragX: Double,
        dragY: Double
    ): Boolean {
        val contentMouseX = viewport.unscale(mouseX)
        val contentMouseY = viewport.unscale(mouseY)
        val contentDragX = viewport.unscale(dragX)
        val contentDragY = viewport.unscale(dragY)
        if (colorPickerRow >= 0 && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            when (colorPickerDrag) {
                ColorPickerDrag.SATURATION_VALUE -> updateSaturationValue(contentMouseX, contentMouseY)
                ColorPickerDrag.HUE -> updateHue(contentMouseX)
                ColorPickerDrag.NONE -> return super.mouseDragged(
                    contentMouseX,
                    contentMouseY,
                    button,
                    contentDragX,
                    contentDragY,
                )
            }
            return true
        }
        return super.mouseDragged(contentMouseX, contentMouseY, button, contentDragX, contentDragY)
    }

    /**
     * 把释放位置还原到内容坐标系并结束拖动。
     *
     * 示例：缩放后的输入框能收到与点击位置一致的释放事件。
     * 禁止混用真实坐标和内容坐标结束一次拖动。
     */
    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (colorPickerDrag != ColorPickerDrag.NONE) {
            colorPickerDrag = ColorPickerDrag.NONE
            recordCurrentState()
            return true
        }
        return super.mouseReleased(viewport.unscale(mouseX), viewport.unscale(mouseY), button)
    }

    /**
     * 使用内容坐标处理滚轮位置，同时保留滚轮步数。
     *
     * 示例：参数页在缩放后仍能从鼠标所在的内容区域滚动。
     * 禁止缩放 [scrollY]，它表示滚轮步数而不是屏幕距离。
     */
    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (colorPickerRow >= 0) {
            return true
        }
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
        return super.mouseScrolled(viewport.unscale(mouseX), viewport.unscale(mouseY), scrollX, scrollY)
    }

    /**
     * 只在真实坐标阶段绘制背景，缩放内容阶段跳过重复调用。
     *
     * 示例：[render] 显式调用时绘制完整背景，[Screen.render] 随后的内部调用会被忽略。
     * 禁止在 [renderingScaledContent] 为 `true` 时再次执行模糊和菜单背景。
     */
    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (!renderingScaledContent) {
            super.renderBackground(graphics, mouseX, mouseY, partialTick)
        }
    }

    /**
     * 先绘制真实尺寸背景，再在内容坐标系中统一绘制控件和浮层。
     *
     * 示例：`512x288` 视口中的全部内容按同一比例缩小。
     * 禁止缩放背景或漏掉建议框、颜色选择器等浮层。
     */
    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (colorPickerDrag == ColorPickerDrag.NONE) recordCurrentState()
        updateHistoryButtons()
        renderBackground(graphics, mouseX, mouseY, partialTick)
        val contentMouseX = viewport.unscale(mouseX.toDouble()).toInt()
        val contentMouseY = viewport.unscale(mouseY.toDouble()).toInt()
        val contentScale = viewport.scale.toFloat()
        graphics.pose().pushPose()
        graphics.pose().scale(contentScale, contentScale, 1f)
        renderingScaledContent = true
        try {
            super.render(graphics, contentMouseX, contentMouseY, partialTick)
            renderLabels(graphics)
            renderSuggestions(graphics, contentMouseX, contentMouseY)
            renderParamSuggestions(graphics, contentMouseX, contentMouseY)
            renderColorPicker(graphics, contentMouseX, contentMouseY, partialTick)
        } finally {
            renderingScaledContent = false
            graphics.pose().popPose()
        }
    }

    /**
     * 绘制当前页面的字段标签和运行状态。
     *
     * 示例：主界面在向量输入左侧绘制位置偏移、Forward 与碰撞箱标签。
     * 禁止在这里重复绘制动态通道说明，通道状态由同行按钮显示。
     *
     * @param graphics 当前 GUI 绘图上下文
     */
    private fun renderLabels(graphics: GuiGraphics) {
        if (page == ControllerPage.PARAMS) {
            renderParamPageLabels(graphics)
            return
        }
        val left = layoutWidth / 2 - 170
        var y = 16
        graphics.drawString(font, title, left, y, 0xFFFFFF, true)
        y = 38
        graphics.drawString(font, "所属模组", left, y, 0xE0E0E0, true)
        graphics.drawString(font, "组ID", left + 160, y, 0xE0E0E0, true)
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
        val animationRowExtra = if (layoutWidth >= MIN_INLINE_ANIMATION_WIDTH) 0 else ANIMATION_STACK_HEIGHT
        graphics.drawString(font, "位置偏移", left, y, 0xE0E0E0, true)
        y += 28 + animationRowExtra
        graphics.drawString(font, "Forward", left, y, 0xE0E0E0, true)
        y += 28 + animationRowExtra
        graphics.drawString(font, "碰撞箱", left, y, 0xE0E0E0, true)
        graphics.drawString(font, "状态: ${displayStatus()}", left, layoutHeight - 34, 0xF0F0F0, true)
        graphics.drawString(font, "测试项: ${packet.optionCount}", left, layoutHeight - 22, 0xF0F0F0, true)
    }

    /**
     * 按内容画布宽度绘制参数页标题、参数标签和滚动条。
     *
     * 示例：缩放后参数标签仍与输入框对齐。
     * 禁止按真实视口宽度单独居中这些标签。
     */
    private fun renderParamPageLabels(graphics: GuiGraphics) {
        val left = layoutWidth / 2 - 170
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

    /**
     * 创建人工复核按钮，点击时先提交当前界面配置再发送复核结果。
     *
     * 示例：待复核状态下打开“索引重播”后点击“通过”，重播开关与复核结果都会保存。
     * 禁止只发送复核包；待复核时保存按钮被复核按钮取代，未提交的修改会随界面关闭丢失。
     *
     * @param label 按钮文字
     * @param action 复核动作标识
     * @param x 内容坐标系横坐标
     * @param y 内容坐标系纵坐标
     * @param width 按钮宽度
     * @return 已构建的复核按钮
     */
    private fun reviewButton(label: String, action: String, x: Int, y: Int, width: Int): Button {
        return Button.builder(Component.literal(label)) {
            CooClientPacketManager.sendTo(updatePacket())
            CooClientPacketManager.sendTo(
                PacketReviewTestControllerC2S(packet.dimension, packet.blockPos, action)
            )
            onClose()
        }.bounds(x, y, width, 20).build()
    }

    /**
     * 把主界面当前输入与动态草稿组装为待提交更新包。
     *
     * 示例：打开位置曲线前调用本方法可保留尚未保存的分组和碰撞箱输入。
     * 禁止把返回值视为服务端确认结果。
     *
     * @return 包含当前界面完整状态的更新包
     */
    private fun updatePacket(): PacketUpdateTestControllerC2S {
        prepareParamState(syncVisible = true)
        return PacketUpdateTestControllerC2S().also {
            it.dimension = packet.dimension
            it.blockPos = packet.blockPos
            it.groupId = currentGroupId()
            it.mode = mode.id
            it.selectedIndex = indexBox.value.toIntOrNull() ?: 0
            it.repeatIndex = repeatIndex
            it.repeatDelayTicks = delayBox.value.toIntOrNull() ?: 0
            it.offsetX = doubleValue(offsetXBox, 0.0)
            it.offsetY = doubleValue(offsetYBox, 0.0)
            it.offsetZ = doubleValue(offsetZBox, 0.0)
            it.positionDynamic = animationDraft.positionDynamic
            it.positionTrack = animationDraft.positionTrack
            it.forwardX = doubleValue(forwardXBox, 0.0)
            it.forwardY = doubleValue(forwardYBox, 0.0)
            it.forwardZ = doubleValue(forwardZBox, 1.0)
            it.forwardDynamic = animationDraft.forwardDynamic
            it.forwardTrack = animationDraft.forwardTrack
            it.boxWidth = doubleValue(widthBox, 0.6)
            it.boxHeight = doubleValue(heightBox, 1.8)
            it.boxDepth = doubleValue(depthBox, 0.6)
            it.optionParamIndex = currentOptionIndex()
            it.optionParamValues = TestOptionParamCodec.encodeOptionValues(currentParamValues())
        }
    }

    /**
     * 根据当前输入框刷新 MOD_ID 或 path 补全，并同步测试项参数。
     *
     * MOD_ID 完整匹配后才会启用 path 补全；该命名空间只有一个 path 时直接写入。
     */
    private fun updateGroupSuggestions(completeSinglePath: Boolean = false) {
        if (updatingGroupInputs) return
        val modIds = testControllerModIds(registeredGroupIds)
        val namespace = modIds.firstOrNull { modId ->
            modId.equals(modIdBox.value.trim(), ignoreCase = true)
        }
        val paths = testControllerPaths(registeredGroupIds, modIdBox.value)
        val singlePath = testControllerSinglePath(registeredGroupIds, modIdBox.value)
        if (completeSinglePath) {
            updatingGroupInputs = true
            when {
                namespace == null -> groupPathBox.value = ""
                singlePath != null -> groupPathBox.value = singlePath
                paths.none { path -> path.equals(groupPathBox.value.trim(), ignoreCase = true) } -> {
                    groupPathBox.value = ""
                }
            }
            updatingGroupInputs = false
        }
        val box = focusedGroupBox()
        val candidates = when (box) {
            modIdBox -> testControllerModIdSuggestions(registeredGroupIds, modIdBox.value)
            groupPathBox -> paths
            else -> emptyList()
        }
        val input = box?.value?.trim().orEmpty()
        suggestions = searchTestControllerSuggestions(candidates, input)
        selectedSuggestionIndex = if (suggestions.isEmpty()) -1 else 0
        suggestionScroll = 0
        modIdBox.setSuggestion(null)
        groupPathBox.setSuggestion(null)
        val first = suggestions.firstOrNull()
        box?.setSuggestion(
            if (input.isNotBlank() && first?.startsWith(input, ignoreCase = true) == true) {
                first.drop(input.length)
            } else {
                null
            }
        )
        updateIndexSuggestion()
        updateParamBoxes()
    }

    private fun focusedGroupBox(): EditBox? {
        return when {
            modIdBox.isFocused && modIdBox.active -> modIdBox
            groupPathBox.isFocused && groupPathBox.active -> groupPathBox
            else -> null
        }
    }

    private fun acceptGroupSuggestion(suggestion: String) {
        when (focusedGroupBox()) {
            modIdBox -> modIdBox.value = suggestion
            groupPathBox -> groupPathBox.value = suggestion
            else -> return
        }
        updateGroupSuggestions(completeSinglePath = modIdBox.isFocused)
    }

    /** @return 两个输入框组成并规范化后的完整测试组资源 ID */
    private fun currentGroupId(): String {
        return testControllerGroupId(registeredGroupIds, modIdBox.value, groupPathBox.value)
            ?.toString()
            .orEmpty()
    }

    private fun renderSuggestions(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (page != ControllerPage.MAIN) return
        val box = focusedGroupBox() ?: return
        if (suggestions.isEmpty()) return
        val x = box.x
        var y = box.y + box.height + 2
        val end = minOf(suggestions.size, suggestionScroll + maxVisibleSuggestions)
        for (index in suggestionScroll until end) {
            val suggestion = suggestions[index]
            val hovered = mouseX in x..(x + box.width) && mouseY in y..(y + 13)
            val selected = index == selectedSuggestionIndex
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

    private fun suggestionAt(mouseX: Int, mouseY: Int): Int? {
        if (page != ControllerPage.MAIN) return null
        val box = focusedGroupBox() ?: return null
        if (suggestions.isEmpty()) return null
        val x = box.x
        var y = box.y + box.height + 2
        val end = minOf(suggestions.size, suggestionScroll + maxVisibleSuggestions)
        for (index in suggestionScroll until end) {
            if (mouseX in x..(x + box.width) && mouseY in y..(y + 13)) return index
            y += 13
        }
        return null
    }

    private fun acceptSelectedSuggestion(): Boolean {
        val index = selectedSuggestionIndex.takeIf { it in suggestions.indices } ?: return false
        acceptGroupSuggestion(suggestions[index])
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
        return "${mode.id}|${currentGroupId().lowercase(Locale.ROOT)}|${currentOptionIndex()}"
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
        val groupId = currentGroupId()
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
        return optionSpecs.getOrNull(index)
            ?.decodeToString()
            ?.let(TestOptionParamCodec::decodeOptionSpecs)
            ?: emptyList()
    }

    private fun currentOptionParamValues(): Map<String, String> {
        if (mode != BlockTestMode.INDEX) {
            return emptyMap()
        }
        if (!currentGroupId().equals(packet.groupId, ignoreCase = true)) {
            return emptyMap()
        }
        return packet.optionParamValues.getOrNull(currentOptionIndex())
            ?.let(TestOptionParamCodec::decodeOptionValues)
            ?: emptyMap()
    }

    private fun currentGroupOptionParamSpecs(): List<ByteArray> {
        val groupId = currentGroupId()
        if (groupId.isBlank()) {
            return emptyList()
        }
        val index = packet.registeredIds.indexOfFirst { id -> id.equals(groupId, ignoreCase = true) }
        if (index in packet.registeredOptionParamSpecs.indices) {
            return packet.registeredOptionParamSpecs[index]
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

    /**
     * 刷新当前枚举参数的补全列表和行内前缀提示。
     *
     * 示例：输入 `leaf` 时同时显示 `LeafBurst` 与 `SmokeLeaf`，前者优先。
     * 禁止给非枚举参数生成候选或把包含匹配写入行内后缀。
     */
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
        paramSuggestions = searchTestControllerSuggestions(spec.suggestions, input)
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
        val absolute = paramAbsoluteModes.getOrElse(index) {
            positionModeOf(spec.defaultValue, spec) == TestOptionParamPositionMode.ABSOLUTE
        }
        val draft = updatePacket()
        TestControllerPickClient.begin(
            screenPacket = packet,
            packet = draft,
            kind = TestControllerPickKind.PARAM_POSITION,
            precisionUnlocked = precisionUnlocked,
            paramOptionIndex = currentOptionIndex(),
            paramId = spec.id,
            paramComponentCount = spec.componentCount.coerceAtLeast(2),
            paramAbsolute = absolute,
            onPicked = { point, _, _ ->
                val picked = if (absolute) {
                    point
                } else {
                    point.subtract(net.minecraft.world.phys.Vec3.atCenterOf(packet.blockPos))
                }
                val components = if (spec.componentCount == 2) {
                    "${formatDouble(picked.x)},${formatDouble(picked.z)}"
                } else {
                    "${formatDouble(picked.x)},${formatDouble(picked.y)},${formatDouble(picked.z)}"
                }
                val values = LinkedHashMap(TestOptionParamCodec.decodeOptionValues(draft.optionParamValues))
                values[spec.id] = "${if (absolute) "absolute" else "relative"}:$components"
                draft.optionParamIndex = currentOptionIndex()
                draft.optionParamValues = TestOptionParamCodec.encodeOptionValues(values)
                history.record(TestControllerPacketDrafts.snapshotFrom(packet, draft))
                minecraft?.setScreen(TestControllerScreen(
                    TestControllerPacketDrafts.reopenPacket(packet, draft),
                    true,
                    history,
                    precisionUnlocked,
                ))
            },
            onCancelled = {
                minecraft?.setScreen(TestControllerScreen(
                    TestControllerPacketDrafts.reopenPacket(packet, draft),
                    true,
                    history,
                    precisionUnlocked,
                ))
            },
        )
        onClose()
    }

    private fun renderColorSwatch(graphics: GuiGraphics, x: Int, y: Int, color: Int?) {
        graphics.fill(x, y, x + 14, y + 14, 0xFF111111.toInt())
        graphics.fill(x + 1, y + 1, x + 13, y + 13, color ?: 0xFF444444.toInt())
    }

    /**
     * 在内容坐标系中查找鼠标命中的颜色色块。
     *
     * 示例：点击参数行色块会返回对应可见行。
     * 禁止传入未经 [TestControllerScreenViewport.unscale] 转换的坐标。
     */
    private fun colorSwatchAt(mouseX: Int, mouseY: Int): Int? {
        if (page != ControllerPage.PARAMS) return null
        val left = layoutWidth / 2 - 170
        repeat(visibleParamRowCount()) { row ->
            val spec = currentParamSpecs.getOrNull(actualParamIndex(row)) ?: return@repeat
            if (!spec.color) return@repeat
            val rowY = PARAM_LIST_TOP + row * PARAM_ROW_HEIGHT
            if (mouseX in (left + 55)..(left + 71) && mouseY in (rowY + 3)..(rowY + 19)) {
                return row
            }
        }
        return null
    }

    private fun openColorPicker(row: Int) {
        val spec = currentParamSpecs.getOrNull(actualParamIndex(row))?.takeIf { it.color } ?: return
        val current = parseColorComponents(rowParamValue(row, spec), spec.componentCount)
            ?: listOf(1f, 1f, 1f, 1f)
        val hsv = rgbToHsv(current)
        colorPickerRow = row
        colorPickerHue = hsv[0]
        colorPickerSaturation = hsv[1]
        colorPickerValue = hsv[2]
        colorPickerAlpha = current.getOrElse(3) { 1f }
        clearFocus()
        updateColorHexBox()
        updateColorRgbBoxes()
        layoutWidgets()
    }

    private fun closeColorPicker() {
        colorPickerRow = -1
        colorPickerDrag = ColorPickerDrag.NONE
        clearFocus()
        layoutWidgets()
    }

    /**
     * 在内容画布中央绘制颜色选择器和遮罩。
     *
     * 示例：小视口下遮罩随内容画布一起缩放并覆盖完整界面。
     * 禁止使用真实视口宽高绘制内容遮罩。
     */
    private fun renderColorPicker(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val spec = activeColorSpec() ?: return
        val left = colorPickerLeft()
        val top = colorPickerTop()
        val svLeft = left + 12
        val svTop = top + 20
        val hueTop = svTop + COLOR_PICKER_SV_HEIGHT + 8

        graphics.flush()
        graphics.pose().pushPose()
        graphics.pose().translate(0f, 0f, COLOR_PICKER_Z)
        graphics.fill(0, 0, layoutWidth, layoutHeight, 0x88000000.toInt())
        graphics.fill(left - 1, top - 1, left + COLOR_PICKER_WIDTH + 1, top + COLOR_PICKER_HEIGHT + 1, 0xFF9A9A9A.toInt())
        graphics.fill(left, top, left + COLOR_PICKER_WIDTH, top + COLOR_PICKER_HEIGHT, 0xFF202020.toInt())
        graphics.drawString(font, "颜色", left + 12, top + 7, 0xFFFFFFFF.toInt(), false)

        var y = 0
        while (y < COLOR_PICKER_SV_HEIGHT) {
            var x = 0
            val value = 1f - y.toFloat() / (COLOR_PICKER_SV_HEIGHT - 1).toFloat()
            while (x < COLOR_PICKER_SV_WIDTH) {
                val saturation = x.toFloat() / (COLOR_PICKER_SV_WIDTH - 1).toFloat()
                val color = hsvToRgb(colorPickerHue, saturation, value).toColorInt()
                graphics.fill(
                    svLeft + x,
                    svTop + y,
                    svLeft + minOf(x + COLOR_PICKER_STEP, COLOR_PICKER_SV_WIDTH),
                    svTop + minOf(y + COLOR_PICKER_STEP, COLOR_PICKER_SV_HEIGHT),
                    color
                )
                x += COLOR_PICKER_STEP
            }
            y += COLOR_PICKER_STEP
        }

        var hueX = 0
        while (hueX < COLOR_PICKER_SV_WIDTH) {
            val hue = hueX.toFloat() / (COLOR_PICKER_SV_WIDTH - 1).toFloat()
            graphics.fill(
                svLeft + hueX,
                hueTop,
                svLeft + minOf(hueX + COLOR_PICKER_STEP, COLOR_PICKER_SV_WIDTH),
                hueTop + COLOR_PICKER_HUE_HEIGHT,
                hsvToRgb(hue, 1f, 1f).toColorInt()
            )
            hueX += COLOR_PICKER_STEP
        }

        val selectorX = svLeft + (colorPickerSaturation * (COLOR_PICKER_SV_WIDTH - 1)).roundToInt()
        val selectorY = svTop + ((1f - colorPickerValue) * (COLOR_PICKER_SV_HEIGHT - 1)).roundToInt()
        graphics.fill(selectorX - 3, selectorY, selectorX + 4, selectorY + 1, 0xFFFFFFFF.toInt())
        graphics.fill(selectorX, selectorY - 3, selectorX + 1, selectorY + 4, 0xFFFFFFFF.toInt())
        val hueSelectorX = svLeft + (colorPickerHue * (COLOR_PICKER_SV_WIDTH - 1)).roundToInt()
        graphics.fill(hueSelectorX - 1, hueTop - 2, hueSelectorX + 2, hueTop + COLOR_PICKER_HUE_HEIGHT + 2, 0xFFFFFFFF.toInt())

        val preview = pickerColor(spec).toColorInt()
        graphics.fill(left + 12, top + 149, left + 42, top + 173, 0xFF0A0A0A.toInt())
        graphics.fill(left + 14, top + 151, left + 40, top + 171, preview)
        graphics.drawString(font, "HEX", left + 52, top + 140, 0xFFD0D0D0.toInt(), false)
        colorHexBox.render(graphics, mouseX, mouseY, partialTick)
        graphics.drawString(font, "RGB", left + 12, top + 184, 0xFFD0D0D0.toInt(), false)
        RGB_COMPONENT_LABELS.forEachIndexed { index, label ->
            graphics.drawString(
                font,
                label,
                left + COLOR_RGB_INPUT_X + index * COLOR_RGB_INPUT_STEP - 8,
                top + COLOR_RGB_INPUT_Y + 6,
                0xFFD0D0D0.toInt(),
                false
            )
        }
        colorRgbBoxes.forEach { box -> box.render(graphics, mouseX, mouseY, partialTick) }
        graphics.flush()
        graphics.pose().popPose()
    }

    private fun handleColorPickerClick(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
        val svLeft = colorPickerLeft() + 12
        val svTop = colorPickerTop() + 20
        val hueTop = svTop + COLOR_PICKER_SV_HEIGHT + 8
        if (mouseX >= svLeft && mouseX <= svLeft + COLOR_PICKER_SV_WIDTH &&
            mouseY >= svTop && mouseY <= svTop + COLOR_PICKER_SV_HEIGHT
        ) {
            clearFocus()
            colorPickerDrag = ColorPickerDrag.SATURATION_VALUE
            updateSaturationValue(mouseX, mouseY)
            return true
        }
        if (mouseX >= svLeft && mouseX <= svLeft + COLOR_PICKER_SV_WIDTH &&
            mouseY >= hueTop && mouseY <= hueTop + COLOR_PICKER_HUE_HEIGHT
        ) {
            clearFocus()
            colorPickerDrag = ColorPickerDrag.HUE
            updateHue(mouseX)
            return true
        }
        return false
    }

    private fun updateSaturationValue(mouseX: Double, mouseY: Double) {
        val svLeft = colorPickerLeft() + 12
        val svTop = colorPickerTop() + 20
        colorPickerSaturation = ((mouseX - svLeft) / COLOR_PICKER_SV_WIDTH).toFloat().coerceIn(0f, 1f)
        colorPickerValue = (1f - ((mouseY - svTop) / COLOR_PICKER_SV_HEIGHT).toFloat()).coerceIn(0f, 1f)
        applyPickerColor(updateHex = true, updateRgb = true)
    }

    private fun updateHue(mouseX: Double) {
        val svLeft = colorPickerLeft() + 12
        colorPickerHue = ((mouseX - svLeft) / COLOR_PICKER_SV_WIDTH).toFloat().coerceIn(0f, 1f)
        applyPickerColor(updateHex = true, updateRgb = true)
    }

    private fun applyPickerColor(updateHex: Boolean, updateRgb: Boolean) {
        val spec = activeColorSpec() ?: return
        val color = pickerColor(spec)
        writeRowParamValue(colorPickerRow, spec, formatColor(color, spec.componentCount, colorPickerAlpha))
        paramValues[spec.id] = rowParamValue(colorPickerRow, spec)
        if (updateHex) {
            updateColorHexBox()
        }
        if (updateRgb) {
            updateColorRgbBoxes()
        }
    }

    private fun onColorHexChanged(rawValue: String) {
        if (updatingColorHex) return
        val spec = activeColorSpec() ?: return
        val normalized = normalizeTestOptionColorHexInput(rawValue)
        if (normalized != rawValue) {
            updatingColorHex = true
            colorHexBox.value = normalized
            updatingColorHex = false
        }
        val validLength = normalized.length == 6 || spec.componentCount >= 4 && normalized.length == 8
        if (!validLength) return
        val color = parseHexColor(normalized, spec.componentCount) ?: return
        val hsv = rgbToHsv(color)
        colorPickerHue = hsv[0]
        colorPickerSaturation = hsv[1]
        colorPickerValue = hsv[2]
        colorPickerAlpha = color.getOrElse(3) { colorPickerAlpha }
        applyPickerColor(updateHex = false, updateRgb = true)
    }

    private fun onColorRgbChanged() {
        if (updatingColorRgb) return
        val color = parseTestOptionRgbInputs(colorRgbBoxes.map { it.value }) ?: return
        val hsv = rgbToHsv(color)
        colorPickerHue = hsv[0]
        colorPickerSaturation = hsv[1]
        colorPickerValue = hsv[2]
        applyPickerColor(updateHex = true, updateRgb = false)
    }

    private fun updateColorHexBox() {
        val spec = activeColorSpec() ?: return
        val color = pickerColor(spec)
        val componentCount = if (spec.componentCount >= 4) 4 else 3
        val hex = color.take(componentCount).joinToString("") { component ->
            String.format(Locale.ROOT, "%02X", (component.coerceIn(0f, 1f) * 255f).roundToInt())
        }
        updatingColorHex = true
        colorHexBox.value = hex
        updatingColorHex = false
    }

    private fun updateColorRgbBoxes() {
        val spec = activeColorSpec() ?: return
        val values = formatTestOptionRgbInputs(pickerColor(spec))
        updatingColorRgb = true
        colorRgbBoxes.forEachIndexed { index, box ->
            box.value = values[index]
        }
        updatingColorRgb = false
    }

    private fun colorInputBoxes(): List<EditBox> {
        return listOf(colorHexBox) + colorRgbBoxes
    }

    private fun moveColorInputFocus(offset: Int) {
        val inputs = colorInputBoxes()
        val focusedIndex = inputs.indexOfFirst { it.isFocused }
        val nextIndex = if (focusedIndex < 0) {
            if (offset < 0) inputs.lastIndex else 0
        } else {
            (focusedIndex + offset + inputs.size) % inputs.size
        }
        setFocused(inputs[nextIndex])
    }

    private fun pickerColor(spec: EncodedTestOptionParamSpec): List<Float> {
        val rgb = hsvToRgb(colorPickerHue, colorPickerSaturation, colorPickerValue)
        return if (spec.componentCount >= 4) rgb + colorPickerAlpha else rgb
    }

    private fun activeColorSpec(): EncodedTestOptionParamSpec? {
        if (colorPickerRow !in paramBoxes.indices) return null
        return currentParamSpecs.getOrNull(actualParamIndex(colorPickerRow))?.takeIf { it.color }
    }

    private fun isInsideColorPicker(mouseX: Double, mouseY: Double): Boolean {
        val left = colorPickerLeft()
        val top = colorPickerTop()
        return mouseX >= left && mouseX <= left + COLOR_PICKER_WIDTH &&
                mouseY >= top && mouseY <= top + COLOR_PICKER_HEIGHT
    }

    /**
     * 返回颜色选择器在内容画布中的左边界。
     *
     * 示例：内容画布变宽时选择器仍保持居中；禁止按真实视口宽度计算。
     */
    private fun colorPickerLeft(): Int = (layoutWidth - COLOR_PICKER_WIDTH) / 2

    /**
     * 返回颜色选择器在内容画布中的上边界。
     *
     * 示例：内容画布变高时选择器仍保持居中；禁止按真实视口高度计算。
     */
    private fun colorPickerTop(): Int = (layoutHeight - COLOR_PICKER_HEIGHT) / 2

    private fun rgbToHsv(color: List<Float>): List<Float> {
        val r = color.getOrElse(0) { 0f }.coerceIn(0f, 1f)
        val g = color.getOrElse(1) { 0f }.coerceIn(0f, 1f)
        val b = color.getOrElse(2) { 0f }.coerceIn(0f, 1f)
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val hue = when {
            delta <= 0.00001f -> 0f
            max == r -> ((g - b) / delta / 6f).let { if (it < 0f) it + 1f else it }
            max == g -> ((b - r) / delta + 2f) / 6f
            else -> ((r - g) / delta + 4f) / 6f
        }
        val saturation = if (max <= 0.00001f) 0f else delta / max
        return listOf(hue.coerceIn(0f, 1f), saturation.coerceIn(0f, 1f), max)
    }

    private fun hsvToRgb(hue: Float, saturation: Float, value: Float): List<Float> {
        val normalizedHue = ((hue % 1f) + 1f) % 1f
        val scaledHue = normalizedHue * 6f
        val sector = floor(scaledHue.toDouble()).toInt().coerceIn(0, 5)
        val fraction = scaledHue - sector
        val p = value * (1f - saturation)
        val q = value * (1f - fraction * saturation)
        val t = value * (1f - (1f - fraction) * saturation)
        return when (sector) {
            0 -> listOf(value, t, p)
            1 -> listOf(q, value, p)
            2 -> listOf(p, value, t)
            3 -> listOf(p, q, value)
            4 -> listOf(t, p, value)
            else -> listOf(value, p, q)
        }
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
            else -> text
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
        if (packet.pendingReview) {
            return testControllerStatusWithOptionId(
                "等待人工复核",
                packet.currentIndex,
                packet.optionCount,
                packet.optionIds.getOrNull(packet.currentIndex - 1).orEmpty()
            )
        }
        if (packet.running) {
            return if (packet.currentIndex > 0 && packet.optionCount > 0) {
                testControllerRunningStatus(
                    packet.currentIndex,
                    packet.optionCount,
                    packet.optionIds.getOrNull(packet.currentIndex - 1).orEmpty()
                )
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

    /**
     * 根据内容画布高度计算参数页可创建的输入行数。
     *
     * 示例：缩放后较高的虚拟画布可以保留更多参数行。
     * 禁止按真实视口高度裁掉本可见的行。
     */
    private fun visibleParamRowCapacity(): Int {
        val availableHeight = layoutHeight - PARAM_LIST_TOP - PARAM_BOTTOM_RESERVED
        return (availableHeight / PARAM_ROW_HEIGHT).coerceIn(1, PARAM_MAX_VISIBLE_ROWS)
    }

    /**
     * 根据主页面、参数页和测试模式摆放并启用控件。
     *
     * 示例：位置通道为动态时，其同行编辑按钮可见；静态时只保留模式按钮。
     * 禁止通过改变按钮尺寸表达 hover 或动态状态，以免向量行发生位移。
     */
    private fun layoutWidgets() {
        if (!::modIdBox.isInitialized || !::groupPathBox.isInitialized) return
        val left = layoutWidth / 2 - 170
        val mainPage = page == ControllerPage.MAIN
        val paramPage = page == ControllerPage.PARAMS
        val indexVisible = mainPage && mode == BlockTestMode.INDEX
        val reviewVisible = mainPage && packet.pendingReview
        val offsetY = if (indexVisible) 128 else 90
        val animationInline = layoutWidth >= MIN_INLINE_ANIMATION_WIDTH
        val animationRowExtra = if (animationInline) 0 else ANIMATION_STACK_HEIGHT
        val forwardY = offsetY + 28 + animationRowExtra
        val boxY = forwardY + 28 + animationRowExtra
        val buttonsY = boxY + 34

        undoButton.setX(layoutWidth - 108)
        undoButton.setY(8)
        redoButton.setX(layoutWidth - 56)
        redoButton.setY(8)
        undoButton.visible = true
        redoButton.visible = true
        updateHistoryButtons()

        modIdBox.setX(left + 52)
        modIdBox.setY(34)
        modIdBox.visible = mainPage
        modIdBox.active = mainPage
        groupPathBox.setX(left + 190)
        groupPathBox.setY(34)
        groupPathBox.visible = mainPage
        groupPathBox.active = mainPage && isTestControllerModIdValid(modIdBox.value)
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
        }

        colorHexBox.setX(colorPickerLeft() + 52)
        colorHexBox.setY(colorPickerTop() + 151)
        colorHexBox.width = COLOR_HEX_INPUT_WIDTH
        colorHexBox.visible = colorPickerRow >= 0
        colorHexBox.active = colorPickerRow >= 0
        colorRgbBoxes.forEachIndexed { index, box ->
            box.setX(colorPickerLeft() + COLOR_RGB_INPUT_X + index * COLOR_RGB_INPUT_STEP)
            box.setY(colorPickerTop() + COLOR_RGB_INPUT_Y)
            box.width = COLOR_RGB_INPUT_WIDTH
            box.visible = colorPickerRow >= 0
            box.active = colorPickerRow >= 0
        }

        setRow(offsetY, offsetXBox, offsetYBox, offsetZBox, offsetPickButton)
        setRow(forwardY, forwardXBox, forwardYBox, forwardZBox, forwardPickButton)
        setRow(boxY, widthBox, heightBox, depthBox, precisionButton)
        val animationControlsX = left + if (animationInline) ANIMATION_CONTROLS_X else ANIMATION_STACK_X
        val animationControlsOffsetY = if (animationInline) 0 else ANIMATION_STACK_OFFSET_Y
        positionModeButton.setX(animationControlsX)
        positionModeButton.setY(offsetY + animationControlsOffsetY)
        forwardModeButton.setX(animationControlsX)
        forwardModeButton.setY(forwardY + animationControlsOffsetY)
        positionEditButton.setX(animationControlsX + ANIMATION_MODE_WIDTH + ANIMATION_CONTROL_GAP)
        positionEditButton.setY(offsetY + animationControlsOffsetY)
        forwardEditButton.setX(animationControlsX + ANIMATION_MODE_WIDTH + ANIMATION_CONTROL_GAP)
        forwardEditButton.setY(forwardY + animationControlsOffsetY)
        positionModeButton.visible = mainPage
        positionModeButton.active = mainPage
        forwardModeButton.visible = mainPage
        forwardModeButton.active = mainPage
        positionEditButton.visible = mainPage && animationDraft.positionDynamic
        positionEditButton.active = positionEditButton.visible
        forwardEditButton.visible = mainPage && animationDraft.forwardDynamic
        forwardEditButton.active = forwardEditButton.visible
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
            val bottomY = layoutHeight - 28
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
        reviewPassButton.setX(left + 44)
        reviewPassButton.setY(buttonsY)
        reviewFailButton.setX(left + 126)
        reviewFailButton.setY(buttonsY)
        reviewSkipButton.setX(left + 220)
        reviewSkipButton.setY(buttonsY)
        backButton.visible = paramPage
        backButton.active = paramPage
        val commandButtonsVisible = paramPage || !reviewVisible
        saveButton.visible = commandButtonsVisible
        saveButton.active = commandButtonsVisible
        startButton.visible = commandButtonsVisible
        startButton.active = commandButtonsVisible
        stopButton.visible = commandButtonsVisible
        stopButton.active = commandButtonsVisible
        listOf(reviewPassButton, reviewFailButton, reviewSkipButton).forEach {
            it.visible = reviewVisible
            it.active = reviewVisible
        }
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

    /**
     * 在内容画布中摆放一行三轴输入框和操作按钮。
     *
     * 示例：位置、Forward 和碰撞箱三行共享相同横向对齐。
     * 禁止用真实视口宽度计算该行中心。
     */
    private fun setRow(y: Int, first: EditBox, second: EditBox, third: EditBox, button: Button) {
        val left = layoutWidth / 2 - 170
        first.setX(left + 92)
        first.setY(y)
        second.setX(left + 166)
        second.setY(y)
        third.setX(left + 240)
        third.setY(y)
        button.setX(left + 312)
        button.setY(y)
    }

    /**
     * 刷新两个通道选择按钮的静态或动态文字。
     *
     * 示例：点击位置模式后，按钮会立即从“静态”切换为“动态”。
     * 禁止在这里改变编辑按钮尺寸或发送网络包。
     */
    private fun updateAnimationButtons() {
        if (!::positionModeButton.isInitialized) return
        positionModeButton.message = Component.literal(animationModeLabel(animationDraft.positionDynamic))
        forwardModeButton.message = Component.literal(animationModeLabel(animationDraft.forwardDynamic))
    }

    /**
     * 返回动态通道选择按钮使用的短标签。
     *
     * 示例：启用动态轨道时返回“动态”。
     * 禁止附加通道名；位置与 Forward 已由当前行标签说明。
     *
     * @param dynamic 当前通道是否启用动态轨道
     * @return “静态”或“动态”
     */
    private fun animationModeLabel(dynamic: Boolean): String = if (dynamic) "动态" else "静态"

    /**
     * 打开指定通道的曲线编辑器，并在返回时恢复完整主界面草稿。
     *
     * 示例：编辑位置曲线后按 Esc，主界面的分组与碰撞箱输入仍保留。
     * 禁止在打开编辑器时提前向服务端提交草稿。
     *
     * @param kind 要编辑的位置或 forward 通道
     */
    private fun openCurveEditor(kind: TestControllerTrackKind) {
        val draft = updatePacket()
        history.record(TestControllerPacketDrafts.snapshotFrom(packet, draft))
        minecraft?.setScreen(
            TestControllerCurveEditorScreen(packet, draft, kind, history) {
                minecraft?.setScreen(
                    TestControllerScreen(
                        TestControllerPacketDrafts.reopenPacket(packet, draft),
                        page == ControllerPage.PARAMS,
                        history,
                        precisionUnlocked,
                    )
                )
            }
        )
    }

    /** 将当前控件值记录为一条配置历史，供主界面和曲线编辑器共享。 */
    internal fun recordCurrentState() {
        if (!::modIdBox.isInitialized || !::groupPathBox.isInitialized) return
        history.record(TestControllerPacketDrafts.snapshotFrom(packet, updatePacket()))
    }

    /** 由撤回或重做恢复完整界面快照。 */
    private fun restoreHistory(snapshot: TestControllerPacketDrafts.TestControllerConfigSnapshot?) {
        if (snapshot == null) {
            updateHistoryButtons()
            return
        }
        minecraft?.setScreen(
            TestControllerScreen(snapshot.toPacket(), page == ControllerPage.PARAMS, history, precisionUnlocked)
        )
    }

    private fun updateHistoryButtons() {
        if (!::undoButton.isInitialized) return
        undoButton.active = history.canUndo
        redoButton.active = history.canRedo
    }

    private fun startPick(kind: TestControllerPickKind) {
        val draft = updatePacket()
        TestControllerPickClient.begin(
            screenPacket = packet,
            packet = draft,
            kind = kind,
            precisionUnlocked = precisionUnlocked,
            onPicked = { point, fromBlock, lookAngle ->
                val origin = net.minecraft.world.phys.Vec3.atCenterOf(packet.blockPos)
                val picked = if (kind == TestControllerPickKind.OFFSET) {
                    point.subtract(origin)
                } else if (fromBlock) {
                    point.subtract(origin).let { value ->
                        val length = value.length()
                        if (length <= 1.0E-7) net.minecraft.world.phys.Vec3(0.0, 0.0, 1.0) else value.scale(1.0 / length)
                    }
                } else {
                    lookAngle
                }
                if (kind == TestControllerPickKind.OFFSET) {
                    draft.offsetX = if (precisionUnlocked) picked.x else picked.x.roundToDecimals(6)
                    draft.offsetY = if (precisionUnlocked) picked.y else picked.y.roundToDecimals(6)
                    draft.offsetZ = if (precisionUnlocked) picked.z else picked.z.roundToDecimals(6)
                } else {
                    draft.forwardX = if (precisionUnlocked) picked.x else picked.x.roundToDecimals(6)
                    draft.forwardY = if (precisionUnlocked) picked.y else picked.y.roundToDecimals(6)
                    draft.forwardZ = if (precisionUnlocked) picked.z else picked.z.roundToDecimals(6)
                }
                history.record(TestControllerPacketDrafts.snapshotFrom(packet, draft))
                minecraft?.setScreen(TestControllerScreen(
                    TestControllerPacketDrafts.reopenPacket(packet, draft),
                    page == ControllerPage.PARAMS,
                    history,
                    precisionUnlocked,
                ))
            },
            onCancelled = {
                minecraft?.setScreen(TestControllerScreen(
                    TestControllerPacketDrafts.reopenPacket(packet, draft),
                    false,
                    history,
                    precisionUnlocked,
                ))
            },
        )
        onClose()
    }

    private enum class ControllerPage {
        MAIN,
        PARAMS
    }

    private enum class ColorPickerDrag {
        NONE,
        SATURATION_VALUE,
        HUE
    }

    /**
     * 保存控制器界面的固定布局与输入尺寸。
     *
     * 示例：同行动态按钮使用 [ANIMATION_MODE_WIDTH] 保持尺寸稳定。
     * 禁止在这里保存当前界面的可变草稿。
     */
    companion object {
        /**
         * 动态模式按钮宽度，单位为 GUI 像素。
         * 示例：“静态”与“动态”均使用 42 px；禁止在 hover 时改变该值。
         */
        private const val ANIMATION_MODE_WIDTH = 42

        /**
         * 动态编辑按钮宽度，单位为 GUI 像素。
         * 示例：“编辑”使用 40 px；禁止按通道使用不同宽度。
         */
        private const val ANIMATION_EDIT_WIDTH = 40

        /**
         * 动态按钮组相对主界面左边界的横坐标。
         * 示例：按钮组放在拾取按钮右侧；禁止覆盖向量输入框。
         */
        private const val ANIMATION_CONTROLS_X = 366

        /** 窄视口下把动态按钮放到向量输入行的下一行。 */
        private const val ANIMATION_STACK_X = 92

        /** 窄视口下动态按钮相对向量输入行的垂直偏移。 */
        private const val ANIMATION_STACK_OFFSET_Y = 22

        /** 窄视口下为动态按钮预留的额外行高。 */
        private const val ANIMATION_STACK_HEIGHT = 22

        /** 动态按钮可以保持同行显示的最小 GUI 宽度。 */
        private const val MIN_INLINE_ANIMATION_WIDTH = 564

        /**
         * 动态模式按钮与编辑按钮之间的间距。
         * 示例：两个按钮相隔 4 px；禁止用负值让按钮重叠。
         */
        private const val ANIMATION_CONTROL_GAP = 4
        private const val PARAM_ROW_HEIGHT = 24
        private const val PARAM_LIST_TOP = 78
        private const val PARAM_BOTTOM_RESERVED = 60
        private const val PARAM_MAX_VISIBLE_ROWS = 10
        private const val PARAM_CONTENT_WIDTH = 334
        private const val PARAM_INPUT_WIDTH = 150
        private const val PARAM_MAX_COMPONENT_BOXES = 4
        private const val COLOR_PICKER_WIDTH = 224
        private const val COLOR_PICKER_HEIGHT = 208
        private const val COLOR_PICKER_SV_WIDTH = 200
        private const val COLOR_PICKER_SV_HEIGHT = 96
        private const val COLOR_PICKER_HUE_HEIGHT = 12
        private const val COLOR_PICKER_STEP = 2
        private const val COLOR_PICKER_Z = 400f
        private const val COLOR_HEX_INPUT_WIDTH = 160
        private const val COLOR_RGB_INPUT_X = 52
        private const val COLOR_RGB_INPUT_Y = 178
        private const val COLOR_RGB_INPUT_WIDTH = 42
        private const val COLOR_RGB_INPUT_STEP = 60
        private val RGB_COMPONENT_LABELS = listOf("R", "G", "B")
    }
}
