package cn.coostack.cooparticlesapi.blocks

import cn.coostack.cooparticlesapi.test.TestManager
import cn.coostack.cooparticlesapi.test.block.BlockTestAnimationTrack
import cn.coostack.cooparticlesapi.test.block.BlockTestAnimationTrackCodec
import cn.coostack.cooparticlesapi.test.block.BlockTestGroup
import cn.coostack.cooparticlesapi.test.block.BlockTestMode
import cn.coostack.cooparticlesapi.test.block.BlockTestOptionResult
import cn.coostack.cooparticlesapi.test.block.BlockTestPlaybackMode
import cn.coostack.cooparticlesapi.test.block.BlockTestPlayer
import cn.coostack.cooparticlesapi.test.api.TestOptionParamSpec
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

class TestControllerBlockEntity(
    pos: BlockPos,
    state: BlockState
) : BlockEntity(CooBlockEntityTypes.TEST_CONTROLLER.get(), pos, state) {
    var groupId: String = defaultTestControllerGroupId().toString()
    var mode: BlockTestMode = BlockTestMode.SEQUENTIAL
    var selectedIndex: Int = 0
    var repeatIndex: Boolean = false
    var repeatDelayTicks: Int = 0
    var playerOffset: Vec3 = Vec3.ZERO
    var playerForward: Vec3 = BlockTestPlayer.DEFAULT_FORWARD
    var playerPositionDynamic: Boolean = false
    var playerForwardDynamic: Boolean = false
    var playerPositionTrack: BlockTestAnimationTrack = BlockTestAnimationTrack.create(Vec3.ZERO)
    var playerForwardTrack: BlockTestAnimationTrack = BlockTestAnimationTrack.create(BlockTestPlayer.DEFAULT_FORWARD)
    var playerBoxWidth: Double = 0.6
    var playerBoxHeight: Double = 1.8
    var playerBoxDepth: Double = 0.6

    private val optionParamValueOverrides = linkedMapOf<Int, MutableMap<String, String>>()
    private var buildFailureStatus: String = "无法创建测试组"
    private var positionAnimationBaseTicks: Long = 0L
    private var positionAnimationEpoch: Long = 0L
    private var positionAnimationAdvancing: Boolean = false
    private var forwardAnimationBaseTicks: Long = 0L
    private var forwardAnimationEpoch: Long = 0L
    private var forwardAnimationAdvancing: Boolean = false
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

    /** @return 当前服务端可创建的方块测试组资源 ID */
    fun registeredGroupIds(): List<ResourceLocation> {
        val serverLevel = level as? ServerLevel ?: return emptyList()
        return TestManager.registeredBlockIds(createTestPlayer(serverLevel))
    }

    /** @return 当前测试组的测试项数量 */
    fun optionCount(): Int {
        val serverLevel = level as? ServerLevel ?: return 0
        val id = resolvedGroupId() ?: return 0
        return TestManager.optionCount(id, createTestPlayer(serverLevel))
    }

    /** @return 当前测试组的测试项 ID */
    fun optionIds(): List<String> {
        val serverLevel = level as? ServerLevel ?: return emptyList()
        val id = resolvedGroupId() ?: return emptyList()
        return TestManager.optionIds(id, createTestPlayer(serverLevel))
    }

    /**
     * @param groupId 待查询的测试组资源 ID
     * @return 该测试组的测试项 ID
     */
    fun optionIds(groupId: ResourceLocation): List<String> {
        val serverLevel = level as? ServerLevel ?: return emptyList()
        return TestManager.optionIds(groupId, createTestPlayer(serverLevel))
    }

    /** @return 当前测试组每个测试项的参数定义 */
    fun optionParamSpecs(): List<List<TestOptionParamSpec<*>>> {
        val serverLevel = level as? ServerLevel ?: return emptyList()
        val id = resolvedGroupId() ?: return emptyList()
        return TestManager.optionParamSpecs(id, createTestPlayer(serverLevel))
    }

    /**
     * @param groupId 待查询的测试组资源 ID
     * @return 该测试组每个测试项的参数定义
     */
    fun optionParamSpecs(groupId: ResourceLocation): List<List<TestOptionParamSpec<*>>> {
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

    /**
     * 以旧版静态姿态参数更新控制器配置。
     *
     * 示例：`controller.updateConfig("demo", BlockTestMode.SEQUENTIAL, 0, false, 0, Vec3.ZERO,
     * BlockTestPlayer.DEFAULT_FORWARD, 0.6, 1.8, 0.6, 0, emptyMap())`。
     * 禁止使用此重载保存动态轨道；它会把位置和 forward 明确设为静态。
     *
     * @param groupId 测试组 ID
     * @param mode 测试运行模式
     * @param selectedIndex 索引模式选择的测试项下标
     * @param repeatIndex 是否重复索引模式测试项
     * @param repeatDelayTicks 重复前等待的 tick 数
     * @param playerOffset 模拟玩家相对控制器的位置
     * @param playerForward 模拟玩家静态朝向
     * @param playerBoxWidth 模拟玩家碰撞箱宽度
     * @param playerBoxHeight 模拟玩家碰撞箱高度
     * @param playerBoxDepth 模拟玩家碰撞箱深度
     * @param optionParamIndex 参数所属测试项下标
     * @param optionParamValues 测试项参数值
     * @return 配置是否发生变化
     */
    @Deprecated("使用包含动态轨道参数的 updateConfig")
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
        return updateConfig(
            groupId = groupId,
            mode = mode,
            selectedIndex = selectedIndex,
            repeatIndex = repeatIndex,
            repeatDelayTicks = repeatDelayTicks,
            playerOffset = playerOffset,
            playerForward = playerForward,
            playerPositionDynamic = false,
            playerForwardDynamic = false,
            playerPositionTrack = BlockTestAnimationTrack.create(playerOffset),
            playerForwardTrack = BlockTestAnimationTrack.create(BlockTestPlayer.normalizeOrDefault(playerForward)),
            playerBoxWidth = playerBoxWidth,
            playerBoxHeight = playerBoxHeight,
            playerBoxDepth = playerBoxDepth,
            optionParamIndex = optionParamIndex,
            optionParamValues = optionParamValues
        )
    }

    fun updateConfig(
        groupId: String,
        mode: BlockTestMode,
        selectedIndex: Int,
        repeatIndex: Boolean,
        repeatDelayTicks: Int,
        playerOffset: Vec3,
        playerForward: Vec3,
        playerPositionDynamic: Boolean,
        playerForwardDynamic: Boolean,
        playerPositionTrack: BlockTestAnimationTrack,
        playerForwardTrack: BlockTestAnimationTrack,
        playerBoxWidth: Double,
        playerBoxHeight: Double,
        playerBoxDepth: Double,
        optionParamIndex: Int,
        optionParamValues: Map<String, String>
    ): Boolean {
        val nextGroupId = resolveGroupId(groupId)?.toString().orEmpty()
        val nextSelectedIndex = selectedIndex.coerceAtLeast(0)
        val nextRepeatDelayTicks = repeatDelayTicks.coerceAtLeast(0)
        val nextPlayerOffset = finiteVec3(playerOffset, Vec3.ZERO)
        val nextPlayerForward = BlockTestPlayer.normalizeOrDefault(playerForward)
        val nextPlayerPositionTrack = playerPositionTrack.copy().also { it.normalize(nextPlayerOffset) }
        val nextPlayerForwardTrack = playerForwardTrack.copy().also { it.normalize(nextPlayerForward) }
        val nextPlayerBoxWidth = finiteDimension(playerBoxWidth, 0.6)
        val nextPlayerBoxHeight = finiteDimension(playerBoxHeight, 1.8)
        val nextPlayerBoxDepth = finiteDimension(playerBoxDepth, 0.6)
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
                this.playerOffset != nextPlayerOffset ||
                this.playerForward != nextPlayerForward ||
                this.playerPositionDynamic != playerPositionDynamic ||
                this.playerForwardDynamic != playerForwardDynamic ||
                BlockTestAnimationTrackCodec.encode(this.playerPositionTrack) != BlockTestAnimationTrackCodec.encode(nextPlayerPositionTrack) ||
                BlockTestAnimationTrackCodec.encode(this.playerForwardTrack) != BlockTestAnimationTrackCodec.encode(nextPlayerForwardTrack) ||
                this.playerBoxWidth != nextPlayerBoxWidth ||
                this.playerBoxHeight != nextPlayerBoxHeight ||
                this.playerBoxDepth != nextPlayerBoxDepth ||
                paramsChanged

        this.groupId = nextGroupId
        this.mode = mode
        this.selectedIndex = nextSelectedIndex
        this.repeatIndex = repeatIndex
        this.repeatDelayTicks = nextRepeatDelayTicks
        this.playerOffset = nextPlayerOffset
        this.playerForward = nextPlayerForward
        this.playerPositionDynamic = playerPositionDynamic
        this.playerForwardDynamic = playerForwardDynamic
        this.playerPositionTrack = nextPlayerPositionTrack
        this.playerForwardTrack = nextPlayerForwardTrack
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
        if (changed) {
            syncClientState()
        }
        return changed
    }

    fun startTest(): Boolean {
        if (level !is ServerLevel) return false
        resetAllAnimationClocks()
        val started = runLoop.start()
        if (!started) {
            positionAnimationAdvancing = false
            forwardAnimationAdvancing = false
            syncClientState()
            setHidden(false)
        } else {
            setHidden(true)
        }
        setChanged()
        return started
    }

    fun stopTest(resetHidden: Boolean = true) {
        freezeAnimation()
        runLoop.stop()
        if (resetHidden) {
            setHidden(false)
        }
        setChanged()
    }

    fun reviewCurrent(result: BlockTestOptionResult): Boolean {
        if (level !is ServerLevel) return false
        val wasRunning = runLoop.isRunning()
        val reviewed = runLoop.reviewCurrent(result)
        if (reviewed) {
            if (wasRunning && !runLoop.isRunning()) {
                freezeAnimation()
            }
            setHidden(runLoop.isRunning())
            setChanged()
        }
        return reviewed
    }

    /**
     * 接受旧版嵌套枚举并执行人工复核。
     *
     * 示例：`controller.reviewCurrent(BlockTestGroup.OptionResult.PASSED)`。
     * 新代码禁止依赖此兼容入口，应传入 [BlockTestOptionResult]。
     *
     * @param result 旧版测试项结果
     * @return 当前待复核项是否已处理
    */
    @Deprecated("使用 BlockTestOptionResult")
    @Suppress("DEPRECATION")
    fun reviewCurrent(result: BlockTestGroup.OptionResult): Boolean {
        val migrated = when (result) {
            BlockTestGroup.OptionResult.PASSED -> BlockTestOptionResult.PASSED
            BlockTestGroup.OptionResult.FAILED -> BlockTestOptionResult.FAILED
            BlockTestGroup.OptionResult.SKIPPED -> BlockTestOptionResult.SKIPPED
        }
        return reviewCurrent(migrated)
    }

    fun tickServer() {
        if (level !is ServerLevel) return
        val wasRunning = runLoop.isRunning()
        runLoop.activeTestPlayer()?.let { player ->
            applyAnimationPose(player)
        }
        if (runLoop.tick()) {
            setChanged()
        }
        if (wasRunning && !runLoop.isRunning()) {
            freezeAnimation()
        }
        setHidden(runLoop.isRunning())
    }

    override fun setRemoved() {
        runLoop.cancel(clearWait = false)
        super.setRemoved()
    }

    override fun loadAdditional(tag: CompoundTag, provider: HolderLookup.Provider) {
        super.loadAdditional(tag, provider)
        val savedGroupId = tag.getString("groupId")
        groupId = if (savedGroupId.isBlank()) {
            defaultTestControllerGroupId().toString()
        } else {
            resolveGroupId(savedGroupId)?.toString().orEmpty()
        }
        mode = BlockTestMode.fromId(tag.getString("mode"))
        selectedIndex = tag.getInt("selectedIndex").coerceAtLeast(0)
        repeatIndex = tag.getBoolean("repeatIndex")
        repeatDelayTicks = tag.getInt("repeatDelayTicks").coerceAtLeast(0)
        playerOffset = readVec3(tag, "playerOffset", Vec3.ZERO)
        playerForward = BlockTestPlayer.normalizeOrDefault(readVec3(tag, "playerForward", BlockTestPlayer.DEFAULT_FORWARD))
        playerPositionDynamic = tag.getBoolean("playerPositionDynamic")
        playerForwardDynamic = tag.getBoolean("playerForwardDynamic")
        playerPositionTrack = BlockTestAnimationTrackCodec.readFrom(tag, "playerPositionTrack", playerOffset)
        playerForwardTrack = BlockTestAnimationTrackCodec.readFrom(tag, "playerForwardTrack", playerForward)
        playerBoxWidth = readDimension(tag, "playerBoxWidth", 0.6)
        playerBoxHeight = readDimension(tag, "playerBoxHeight", 1.8)
        playerBoxDepth = readDimension(tag, "playerBoxDepth", 0.6)
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
        if (tag.contains("positionAnimationEpoch")) {
            positionAnimationBaseTicks = tag.getLong("positionAnimationBaseTicks").coerceAtLeast(0L)
            positionAnimationEpoch = tag.getLong("positionAnimationEpoch")
            positionAnimationAdvancing = tag.getBoolean("positionAnimationAdvancing")
            forwardAnimationBaseTicks = tag.getLong("forwardAnimationBaseTicks").coerceAtLeast(0L)
            forwardAnimationEpoch = tag.getLong("forwardAnimationEpoch")
            forwardAnimationAdvancing = tag.getBoolean("forwardAnimationAdvancing")
        } else {
            positionAnimationBaseTicks = 0L
            positionAnimationEpoch = 0L
            positionAnimationAdvancing = false
            forwardAnimationBaseTicks = 0L
            forwardAnimationEpoch = 0L
            forwardAnimationAdvancing = false
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
        tag.putBoolean("playerPositionDynamic", playerPositionDynamic)
        tag.putBoolean("playerForwardDynamic", playerForwardDynamic)
        BlockTestAnimationTrackCodec.writeTo(tag, "playerPositionTrack", playerPositionTrack)
        BlockTestAnimationTrackCodec.writeTo(tag, "playerForwardTrack", playerForwardTrack)
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

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> {
        return ClientboundBlockEntityDataPacket.create(this)
    }

    override fun getUpdateTag(provider: HolderLookup.Provider): CompoundTag {
        return saveWithoutMetadata(provider).also(::writeAnimationRuntime)
    }

    private fun buildRuntimeGroup(): BlockTestGroup? {
        val serverLevel = level as? ServerLevel ?: return null
        val player = createTestPlayer(serverLevel)
        val id = resolvedGroupId()
        val built = id?.let { TestManager.buildBlock(it, player) } ?: run {
            buildFailureStatus = "未知 TestGroupID: $groupId"
            return null
        }
        built.setOptionParamOverrides(optionParamValueOverrides)
        val runtimeGroup = when (mode) {
            BlockTestMode.INDEX -> built.singleOption(selectedIndex) ?: run {
                buildFailureStatus = "索引越界: $selectedIndex / ${built.optionCount()}"
                return null
            }
            else -> built
        }
        runtimeGroup.optionStartListener = { player, _ ->
            (player as? BlockTestPlayer)?.let(::resetOnceAnimations)
        }
        return runtimeGroup
    }

    /** @return 当前配置解析后的测试组资源 ID */
    private fun resolvedGroupId(): ResourceLocation? {
        return resolveGroupId(groupId)
    }

    /**
     * 解析完整资源 ID，并兼容 path 唯一的旧版无命名空间存档。
     *
     * @param value 网络或存档中的测试组 ID
     * @return 有效且无歧义的资源 ID；无法解析时返回 `null`
     */
    private fun resolveGroupId(value: String): ResourceLocation? {
        val input = value.trim()
        if (input.isBlank()) {
            return null
        }
        if (':' in input) {
            return ResourceLocation.tryParse(input)
        }
        return TestManager.registeredIds().singleOrNull { id -> id.path == input }
    }

    private fun createTestPlayer(serverLevel: ServerLevel): BlockTestPlayer {
        return BlockTestPlayer(serverLevel, worldPosition).also {
            it.offset = playerOffset
            it.setForward(playerForward)
            it.boxWidth = playerBoxWidth
            it.boxHeight = playerBoxHeight
            it.boxDepth = playerBoxDepth
            applyAnimationPose(it)
        }
    }

    /**
     * 计算客户端预览使用的位置轨道 elapsed tick。
     *
     * 示例：渲染事件传入 `event.delta.getGameTimeDeltaPartialTick(true)`。
     * 禁止把结果写回方块实体；它是服务端时钟的只读镜像。
     *
     * @param partialTick 当前帧的小数 tick
     * @return 位置轨道 elapsed tick
     */
    fun positionAnimationElapsedTicks(partialTick: Float = 0f): Double {
        return elapsedTicks(
            positionAnimationBaseTicks,
            positionAnimationEpoch,
            positionAnimationAdvancing,
            partialTick
        )
    }

    /**
     * 计算客户端预览使用的 forward 轨道 elapsed tick。
     *
     * 示例：箭头渲染使用该值采样 forward 轨道。
     * 禁止把它当作位置轨道的 tick 计数。
     *
     * @param partialTick 当前帧的小数 tick
     * @return forward 轨道 elapsed tick
     */
    fun forwardAnimationElapsedTicks(partialTick: Float = 0f): Double {
        return elapsedTicks(
            forwardAnimationBaseTicks,
            forwardAnimationEpoch,
            forwardAnimationAdvancing,
            partialTick
        )
    }

    private fun resetAllAnimationClocks() {
        val currentLevel = level ?: return
        positionAnimationBaseTicks = 0L
        positionAnimationEpoch = currentLevel.gameTime
        positionAnimationAdvancing = playerPositionDynamic
        forwardAnimationBaseTicks = 0L
        forwardAnimationEpoch = currentLevel.gameTime
        forwardAnimationAdvancing = playerForwardDynamic
        syncClientState()
    }

    private fun resetOnceAnimations(player: BlockTestPlayer) {
        val currentLevel = level ?: return
        var changed = false
        if (playerPositionDynamic &&
            (playerPositionTrack.playbackMode == BlockTestPlaybackMode.ONCE || !positionAnimationAdvancing)
        ) {
            positionAnimationBaseTicks = 0L
            positionAnimationEpoch = currentLevel.gameTime
            positionAnimationAdvancing = true
            changed = true
        }
        if (playerForwardDynamic &&
            (playerForwardTrack.playbackMode == BlockTestPlaybackMode.ONCE || !forwardAnimationAdvancing)
        ) {
            forwardAnimationBaseTicks = 0L
            forwardAnimationEpoch = currentLevel.gameTime
            forwardAnimationAdvancing = true
            changed = true
        }
        applyAnimationPose(player)
        if (changed) {
            syncClientState()
        }
    }

    private fun freezeAnimation() {
        if (!positionAnimationAdvancing && !forwardAnimationAdvancing) {
            return
        }
        positionAnimationBaseTicks = positionAnimationElapsedTicks().toLong().coerceAtLeast(0L)
        forwardAnimationBaseTicks = forwardAnimationElapsedTicks().toLong().coerceAtLeast(0L)
        positionAnimationAdvancing = false
        forwardAnimationAdvancing = false
        syncClientState()
    }

    private fun applyAnimationPose(player: BlockTestPlayer) {
        player.offset = if (playerPositionDynamic) {
            playerPositionTrack.sample(positionAnimationElapsedTicks())
        } else {
            playerOffset
        }
        player.setForward(
            if (playerForwardDynamic) {
                BlockTestPlayer.normalizeOrDefault(playerForwardTrack.sample(forwardAnimationElapsedTicks()))
            } else {
                playerForward
            }
        )
    }

    private fun writeAnimationRuntime(tag: CompoundTag) {
        tag.putLong("positionAnimationBaseTicks", positionAnimationBaseTicks)
        tag.putLong("positionAnimationEpoch", positionAnimationEpoch)
        tag.putBoolean("positionAnimationAdvancing", positionAnimationAdvancing)
        tag.putLong("forwardAnimationBaseTicks", forwardAnimationBaseTicks)
        tag.putLong("forwardAnimationEpoch", forwardAnimationEpoch)
        tag.putBoolean("forwardAnimationAdvancing", forwardAnimationAdvancing)
    }

    private fun elapsedTicks(baseTicks: Long, epoch: Long, advancing: Boolean, partialTick: Float): Double {
        if (!advancing) {
            return baseTicks.toDouble()
        }
        val currentLevel = level ?: return baseTicks.toDouble()
        val sinceEpoch = (currentLevel.gameTime - epoch).coerceAtLeast(0L)
        return baseTicks + sinceEpoch.toDouble() + partialTick.coerceIn(0f, 1f)
    }

    private fun syncClientState() {
        setChanged()
        val currentLevel = level as? ServerLevel ?: return
        val state = currentLevel.getBlockState(worldPosition)
        currentLevel.sendBlockUpdated(worldPosition, state, state, 3)
    }

    private fun finiteVec3(value: Vec3, fallback: Vec3): Vec3 {
        return if (value.x.isFinite() && value.y.isFinite() && value.z.isFinite()) value else fallback
    }

    private fun finiteDimension(value: Double, fallback: Double): Double {
        return if (value.isFinite()) value.coerceIn(0.05, 16.0) else fallback
    }

    private fun setHidden(hidden: Boolean) {
        val currentLevel = level ?: return
        val currentState = currentLevel.getBlockState(worldPosition)
        if (currentState.block is TestControllerBlock && currentState.getValue(TestControllerBlock.HIDDEN) != hidden) {
            currentLevel.setBlock(worldPosition, currentState.setValue(TestControllerBlock.HIDDEN, hidden), 3)
        }
    }

    private fun readVec3(tag: CompoundTag, key: String, fallback: Vec3): Vec3 {
        if (!tag.contains("${key}X") || !tag.contains("${key}Y") || !tag.contains("${key}Z")) {
            return fallback
        }
        return finiteVec3(
            Vec3(tag.getDouble("${key}X"), tag.getDouble("${key}Y"), tag.getDouble("${key}Z")),
            fallback
        )
    }

    /**
     * 从存档读取有限的碰撞箱尺寸。
     *
     * 示例：缺少 `playerBoxWidth` 时返回配置默认值。
     * 禁止让 `NaN`、无穷大或非正数进入碰撞箱计算。
     *
     * @param tag 来源 NBT
     * @param key 尺寸字段名
     * @param fallback 字段缺失或异常时的回退值
     * @return 限制到有效范围的尺寸
     */
    private fun readDimension(tag: CompoundTag, key: String, fallback: Double): Double {
        return if (tag.contains(key)) finiteDimension(tag.getDouble(key), fallback) else fallback
    }

    private fun writeVec3(tag: CompoundTag, key: String, value: Vec3) {
        tag.putDouble("${key}X", value.x)
        tag.putDouble("${key}Y", value.y)
        tag.putDouble("${key}Z", value.z)
    }
}
