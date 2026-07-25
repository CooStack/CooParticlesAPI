package cn.coostack.cooparticlesapi.blocks

import cn.coostack.cooparticlesapi.test.TestManager
import cn.coostack.cooparticlesapi.test.block.BlockTestGroup
import cn.coostack.cooparticlesapi.test.block.BlockTestMode
import cn.coostack.cooparticlesapi.test.block.BlockTestPlayer
import cn.coostack.cooparticlesapi.test.api.TestOptionParamSpec
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

class TestControllerBlockEntity(
    pos: BlockPos,
    state: BlockState
) : BlockEntity(CooBlockEntityTypes.TEST_CONTROLLER.get(), pos, state) {
    var groupId: String = ""
    var mode: BlockTestMode = BlockTestMode.SEQUENTIAL
    var selectedIndex: Int = 0
    var repeatIndex: Boolean = false
    var repeatDelayTicks: Int = 0
    var playerOffset: Vec3 = Vec3.ZERO
    var playerForward: Vec3 = BlockTestPlayer.DEFAULT_FORWARD
    var playerBoxWidth: Double = 0.6
    var playerBoxHeight: Double = 1.8
    var playerBoxDepth: Double = 0.6

    private val optionParamValueOverrides = linkedMapOf<Int, MutableMap<String, String>>()
    private var buildFailureStatus: String = "无法创建测试组"
    private val runLoop = TestControllerRunLoop(
        groupFactory = ::buildRuntimeGroup,
        modeProvider = { mode },
        repeatIndexProvider = { repeatIndex },
        repeatDelayTicksProvider = { repeatDelayTicks },
        buildFailureStatusProvider = { buildFailureStatus }
    )

    fun isRunning(): Boolean {
        return runLoop.isRunning()
    }

    fun statusText(): String {
        return runLoop.statusText()
    }

    fun registeredGroupIds(): List<String> {
        val serverLevel = level as? ServerLevel ?: return emptyList()
        return TestManager.registeredBlockIds(createTestPlayer(serverLevel))
    }

    fun optionCount(): Int {
        val serverLevel = level as? ServerLevel ?: return 0
        return TestManager.optionCount(groupId, createTestPlayer(serverLevel))
    }

    fun optionIds(): List<String> {
        val serverLevel = level as? ServerLevel ?: return emptyList()
        return TestManager.optionIds(groupId, createTestPlayer(serverLevel))
    }

    fun optionIds(groupId: String): List<String> {
        val serverLevel = level as? ServerLevel ?: return emptyList()
        return TestManager.optionIds(groupId, createTestPlayer(serverLevel))
    }

    fun optionParamSpecs(): List<List<TestOptionParamSpec<*>>> {
        val serverLevel = level as? ServerLevel ?: return emptyList()
        return TestManager.optionParamSpecs(groupId, createTestPlayer(serverLevel))
    }

    fun optionParamSpecs(groupId: String): List<List<TestOptionParamSpec<*>>> {
        val serverLevel = level as? ServerLevel ?: return emptyList()
        return TestManager.optionParamSpecs(groupId, createTestPlayer(serverLevel))
    }

    fun optionParamValues(): List<Map<String, String>> {
        return optionParamSpecs().mapIndexed { index, specs ->
            val saved = optionParamValueOverrides[index].orEmpty()
            specs.associate { spec -> spec.id to (saved[spec.id] ?: spec.defaultText()) }
        }
    }

    fun currentIndex(): Int {
        if (!isRunning()) {
            return 0
        }
        if (mode == BlockTestMode.INDEX) {
            return selectedIndex + 1
        }
        return runLoop.currentIndex()
    }

    fun hasPendingReview(): Boolean = runLoop.hasPendingReview()

    fun updateConfig(
        groupId: String,
        mode: BlockTestMode,
        selectedIndex: Int,
        repeatIndex: Boolean,
        repeatDelayTicks: Int,
        playerOffset: Vec3,
        playerForward: Vec3,
        playerBoxWidth: Double,
        playerBoxHeight: Double,
        playerBoxDepth: Double,
        optionParamIndex: Int,
        optionParamValues: Map<String, String>
    ): Boolean {
        val nextGroupId = groupId.trim()
        val nextSelectedIndex = selectedIndex.coerceAtLeast(0)
        val nextRepeatDelayTicks = repeatDelayTicks.coerceAtLeast(0)
        val nextPlayerForward = BlockTestPlayer.normalizeOrDefault(playerForward)
        val nextPlayerBoxWidth = playerBoxWidth.coerceIn(0.05, 16.0)
        val nextPlayerBoxHeight = playerBoxHeight.coerceIn(0.05, 16.0)
        val nextPlayerBoxDepth = playerBoxDepth.coerceIn(0.05, 16.0)
        val groupChanged = this.groupId != nextGroupId
        val normalizedParamIndex = optionParamIndex.coerceAtLeast(0)
        val normalizedParamValues = optionParamValues
            .filterKeys { it.isNotBlank() }
            .mapValues { it.value.trim() }
        val paramsChanged = optionParamValueOverrides[normalizedParamIndex].orEmpty() != normalizedParamValues
        val changed = this.groupId != nextGroupId ||
                this.mode != mode ||
                this.selectedIndex != nextSelectedIndex ||
                this.repeatIndex != repeatIndex ||
                this.repeatDelayTicks != nextRepeatDelayTicks ||
                this.playerOffset != playerOffset ||
                this.playerForward != nextPlayerForward ||
                this.playerBoxWidth != nextPlayerBoxWidth ||
                this.playerBoxHeight != nextPlayerBoxHeight ||
                this.playerBoxDepth != nextPlayerBoxDepth ||
                paramsChanged

        this.groupId = nextGroupId
        this.mode = mode
        this.selectedIndex = nextSelectedIndex
        this.repeatIndex = repeatIndex
        this.repeatDelayTicks = nextRepeatDelayTicks
        this.playerOffset = playerOffset
        this.playerForward = nextPlayerForward
        this.playerBoxWidth = nextPlayerBoxWidth
        this.playerBoxHeight = nextPlayerBoxHeight
        this.playerBoxDepth = nextPlayerBoxDepth
        if (groupChanged) {
            optionParamValueOverrides.clear()
        }
        if (normalizedParamValues.isEmpty()) {
            optionParamValueOverrides.remove(normalizedParamIndex)
        } else {
            optionParamValueOverrides[normalizedParamIndex] = LinkedHashMap(normalizedParamValues)
        }
        setChanged()
        return changed
    }

    fun startTest(): Boolean {
        if (level !is ServerLevel) return false
        val started = runLoop.start()
        if (!started) {
            setHidden(false)
        } else {
            setHidden(true)
        }
        setChanged()
        return started
    }

    fun stopTest(resetHidden: Boolean = true) {
        runLoop.stop()
        if (resetHidden) {
            setHidden(false)
        }
        setChanged()
    }

    fun reviewCurrent(result: BlockTestGroup.OptionResult): Boolean {
        if (level !is ServerLevel) return false
        val reviewed = runLoop.reviewCurrent(result)
        if (reviewed) {
            setHidden(runLoop.isRunning())
            setChanged()
        }
        return reviewed
    }

    fun tickServer() {
        if (level !is ServerLevel) return
        if (runLoop.tick()) {
            setChanged()
        }
        setHidden(runLoop.isRunning())
    }

    override fun setRemoved() {
        runLoop.cancel(clearWait = false)
        super.setRemoved()
    }

    override fun loadAdditional(tag: CompoundTag, provider: HolderLookup.Provider) {
        super.loadAdditional(tag, provider)
        groupId = tag.getString("groupId")
        mode = BlockTestMode.fromId(tag.getString("mode"))
        selectedIndex = tag.getInt("selectedIndex").coerceAtLeast(0)
        repeatIndex = tag.getBoolean("repeatIndex")
        repeatDelayTicks = tag.getInt("repeatDelayTicks").coerceAtLeast(0)
        playerOffset = readVec3(tag, "playerOffset", Vec3.ZERO)
        playerForward = BlockTestPlayer.normalizeOrDefault(readVec3(tag, "playerForward", BlockTestPlayer.DEFAULT_FORWARD))
        playerBoxWidth = tag.getDouble("playerBoxWidth").takeIf { it > 0.0 } ?: 0.6
        playerBoxHeight = tag.getDouble("playerBoxHeight").takeIf { it > 0.0 } ?: 1.8
        playerBoxDepth = tag.getDouble("playerBoxDepth").takeIf { it > 0.0 } ?: 0.6
        optionParamValueOverrides.clear()
        val optionParams = tag.getCompound("optionParams")
        optionParams.getAllKeys().forEach { indexKey ->
            val index = indexKey.toIntOrNull() ?: return@forEach
            val valuesTag = optionParams.getCompound(indexKey)
            val values = linkedMapOf<String, String>()
            valuesTag.getAllKeys().forEach { paramId ->
                values[paramId] = valuesTag.getString(paramId)
            }
            if (values.isNotEmpty()) {
                optionParamValueOverrides[index] = values
            }
        }
        runLoop.restore(tag.getBoolean("shouldAutoRun"), tag.getInt("waitTicks"))
    }

    override fun saveAdditional(tag: CompoundTag, provider: HolderLookup.Provider) {
        super.saveAdditional(tag, provider)
        tag.putString("groupId", groupId)
        tag.putString("mode", mode.id)
        tag.putInt("selectedIndex", selectedIndex)
        tag.putBoolean("repeatIndex", repeatIndex)
        tag.putInt("repeatDelayTicks", repeatDelayTicks)
        tag.putBoolean("shouldAutoRun", runLoop.shouldAutoRun)
        tag.putInt("waitTicks", runLoop.waitTicks.coerceAtLeast(0))
        writeVec3(tag, "playerOffset", playerOffset)
        writeVec3(tag, "playerForward", playerForward)
        tag.putDouble("playerBoxWidth", playerBoxWidth)
        tag.putDouble("playerBoxHeight", playerBoxHeight)
        tag.putDouble("playerBoxDepth", playerBoxDepth)
        val optionParams = CompoundTag()
        optionParamValueOverrides.forEach { (index, values) ->
            val valuesTag = CompoundTag()
            values.forEach { (id, value) -> valuesTag.putString(id, value) }
            optionParams.put(index.toString(), valuesTag)
        }
        tag.put("optionParams", optionParams)
    }

    private fun buildRuntimeGroup(): BlockTestGroup? {
        val serverLevel = level as? ServerLevel ?: return null
        val player = createTestPlayer(serverLevel)
        val built = TestManager.buildBlock(groupId, player) ?: run {
            buildFailureStatus = "未知 TestGroupID: $groupId"
            return null
        }
        built.setOptionParamOverrides(optionParamValueOverrides)
        return when (mode) {
            BlockTestMode.INDEX -> built.singleOption(selectedIndex) ?: run {
                buildFailureStatus = "索引越界: $selectedIndex / ${built.optionCount()}"
                return null
            }
            else -> built
        }
    }

    private fun createTestPlayer(serverLevel: ServerLevel): BlockTestPlayer {
        return BlockTestPlayer(serverLevel, worldPosition).also {
            it.offset = playerOffset
            it.setForward(playerForward)
            it.boxWidth = playerBoxWidth
            it.boxHeight = playerBoxHeight
            it.boxDepth = playerBoxDepth
        }
    }

    private fun setHidden(hidden: Boolean) {
        val currentLevel = level ?: return
        val currentState = currentLevel.getBlockState(worldPosition)
        if (currentState.block is TestControllerBlock && currentState.getValue(TestControllerBlock.HIDDEN) != hidden) {
            currentLevel.setBlock(worldPosition, currentState.setValue(TestControllerBlock.HIDDEN, hidden), 3)
        }
    }

    private fun readVec3(tag: CompoundTag, key: String, fallback: Vec3): Vec3 {
        if (!tag.contains("${key}X")) {
            return fallback
        }
        return Vec3(tag.getDouble("${key}X"), tag.getDouble("${key}Y"), tag.getDouble("${key}Z"))
    }

    private fun writeVec3(tag: CompoundTag, key: String, value: Vec3) {
        tag.putDouble("${key}X", value.x)
        tag.putDouble("${key}Y", value.y)
        tag.putDouble("${key}Z", value.z)
    }
}
