package cn.coostack.cooparticlesapi.blocks

import cn.coostack.cooparticlesapi.test.TestManager
import cn.coostack.cooparticlesapi.test.block.BlockTestGroup
import cn.coostack.cooparticlesapi.test.block.BlockTestMode
import cn.coostack.cooparticlesapi.test.block.BlockTestPlayer
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

    private var activeGroup: BlockTestGroup? = null
    private var waitTicks: Int = 0
    private var shouldAutoRun: Boolean = false
    private var lastStatus: String = "空闲"

    fun isRunning(): Boolean {
        return activeGroup != null || waitTicks > 0 || shouldAutoRun
    }

    fun statusText(): String {
        val group = activeGroup
        return when {
            group != null -> group.statusLine()
            waitTicks > 0 -> "等待 ${waitTicks} tick 后重复"
            shouldAutoRun -> "等待自动启动"
            else -> lastStatus
        }
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

    fun currentIndex(): Int {
        if (!isRunning()) {
            return 0
        }
        if (mode == BlockTestMode.INDEX) {
            return selectedIndex + 1
        }
        return activeGroup?.activeIndex()?.takeIf { it >= 0 }?.plus(1) ?: 0
    }

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
        playerBoxDepth: Double
    ): Boolean {
        val nextGroupId = groupId.trim()
        val nextSelectedIndex = selectedIndex.coerceAtLeast(0)
        val nextRepeatDelayTicks = repeatDelayTicks.coerceAtLeast(0)
        val nextPlayerForward = BlockTestPlayer.normalizeOrDefault(playerForward)
        val nextPlayerBoxWidth = playerBoxWidth.coerceIn(0.05, 16.0)
        val nextPlayerBoxHeight = playerBoxHeight.coerceIn(0.05, 16.0)
        val nextPlayerBoxDepth = playerBoxDepth.coerceIn(0.05, 16.0)
        val changed = this.groupId != nextGroupId ||
                this.mode != mode ||
                this.selectedIndex != nextSelectedIndex ||
                this.repeatIndex != repeatIndex ||
                this.repeatDelayTicks != nextRepeatDelayTicks ||
                this.playerOffset != playerOffset ||
                this.playerForward != nextPlayerForward ||
                this.playerBoxWidth != nextPlayerBoxWidth ||
                this.playerBoxHeight != nextPlayerBoxHeight ||
                this.playerBoxDepth != nextPlayerBoxDepth

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
        setChanged()
        return changed
    }

    fun startTest(): Boolean {
        val serverLevel = level as? ServerLevel ?: return false
        cancelRuntime(resetHidden = false)
        if (groupId.isBlank() || !TestManager.contains(groupId)) {
            shouldAutoRun = false
            lastStatus = "未知 TestGroupID: $groupId"
            setChanged()
            return false
        }
        shouldAutoRun = true
        val started = startFreshGroup(serverLevel)
        if (!started) {
            shouldAutoRun = false
            setHidden(false)
            setChanged()
        }
        return started
    }

    fun stopTest(resetHidden: Boolean = true) {
        shouldAutoRun = false
        cancelRuntime(resetHidden)
        lastStatus = "已停止"
        setChanged()
    }

    fun tickServer() {
        val serverLevel = level as? ServerLevel ?: return
        if (shouldAutoRun && activeGroup == null && waitTicks <= 0) {
            if (!startFreshGroup(serverLevel)) {
                shouldAutoRun = false
                setHidden(false)
                setChanged()
            }
        }
        if (!isRunning() && blockState.getValue(TestControllerBlock.HIDDEN)) {
            setHidden(false)
        }
        if (waitTicks > 0) {
            waitTicks--
            if (waitTicks <= 0) {
                if (!startFreshGroup(serverLevel)) {
                    shouldAutoRun = false
                    setHidden(false)
                    setChanged()
                }
            }
            return
        }

        val group = activeGroup ?: return
        if (group.isDone()) {
            handleGroupDone(serverLevel)
            return
        }
        group.doTick()
        if (group.isDone()) {
            handleGroupDone(serverLevel)
        }
    }

    override fun setRemoved() {
        cancelRuntime(resetHidden = false, clearWait = false)
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
        shouldAutoRun = tag.getBoolean("shouldAutoRun")
        waitTicks = if (shouldAutoRun) tag.getInt("waitTicks").coerceAtLeast(0) else 0
        lastStatus = when {
            waitTicks > 0 -> "等待 ${waitTicks} tick 后重复"
            shouldAutoRun -> "等待自动启动"
            else -> "空闲"
        }
    }

    override fun saveAdditional(tag: CompoundTag, provider: HolderLookup.Provider) {
        super.saveAdditional(tag, provider)
        tag.putString("groupId", groupId)
        tag.putString("mode", mode.id)
        tag.putInt("selectedIndex", selectedIndex)
        tag.putBoolean("repeatIndex", repeatIndex)
        tag.putInt("repeatDelayTicks", repeatDelayTicks)
        tag.putBoolean("shouldAutoRun", shouldAutoRun)
        tag.putInt("waitTicks", waitTicks.coerceAtLeast(0))
        writeVec3(tag, "playerOffset", playerOffset)
        writeVec3(tag, "playerForward", playerForward)
        tag.putDouble("playerBoxWidth", playerBoxWidth)
        tag.putDouble("playerBoxHeight", playerBoxHeight)
        tag.putDouble("playerBoxDepth", playerBoxDepth)
    }

    private fun startFreshGroup(serverLevel: ServerLevel): Boolean {
        val player = createTestPlayer(serverLevel)
        val built = TestManager.buildBlock(groupId, player) ?: run {
            lastStatus = "未知 TestGroupID: $groupId"
            return false
        }
        val group = when (mode) {
            BlockTestMode.INDEX -> built.singleOption(selectedIndex) ?: run {
                lastStatus = "索引越界: $selectedIndex / ${built.optionCount()}"
                setHidden(false)
                setChanged()
                return false
            }
            else -> built
        }
        activeGroup = group
        group.announceGroupFinished = shouldAnnounceGroupFinished()
        waitTicks = 0
        group.start()
        lastStatus = group.statusLine()
        setHidden(true)
        setChanged()
        return true
    }

    private fun handleGroupDone(serverLevel: ServerLevel) {
        activeGroup = null
        when (mode) {
            BlockTestMode.SEQUENTIAL -> {
                shouldAutoRun = false
                lastStatus = "已完成"
                setHidden(false)
            }
            BlockTestMode.LOOP -> {
                shouldAutoRun = true
                lastStatus = "循环重启"
                if (!startFreshGroup(serverLevel)) {
                    shouldAutoRun = false
                    setHidden(false)
                }
            }
            BlockTestMode.INDEX -> {
                if (repeatIndex) {
                    shouldAutoRun = true
                    waitTicks = repeatDelayTicks.coerceAtLeast(0)
                    lastStatus = if (waitTicks > 0) "等待 ${waitTicks} tick 后重复" else "重复重启"
                    if (waitTicks == 0) {
                        if (!startFreshGroup(serverLevel)) {
                            shouldAutoRun = false
                            setHidden(false)
                        }
                    }
                } else {
                    shouldAutoRun = false
                    lastStatus = "已完成索引 $selectedIndex"
                    setHidden(false)
                }
            }
        }
        setChanged()
    }

    private fun shouldAnnounceGroupFinished(): Boolean {
        return when (mode) {
            BlockTestMode.SEQUENTIAL -> true
            BlockTestMode.LOOP -> false
            BlockTestMode.INDEX -> !repeatIndex
        }
    }

    private fun cancelRuntime(resetHidden: Boolean, clearWait: Boolean = true) {
        activeGroup?.cancel()
        activeGroup = null
        if (clearWait) {
            waitTicks = 0
        }
        if (resetHidden) {
            setHidden(false)
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
