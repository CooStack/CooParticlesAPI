package cn.coostack.cooparticlesapi.test.block

import cn.coostack.cooparticlesapi.test.api.TestGroup
import cn.coostack.cooparticlesapi.test.api.TestOption
import cn.coostack.cooparticlesapi.test.api.TestOptionParamSpec
import cn.coostack.cooparticlesapi.test.api.TestOptionPlayerUpdateSupport
import cn.coostack.cooparticlesapi.test.api.TestReviewMode
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import java.util.function.Supplier

class BlockTestGroup(
    val testPlayer: BlockTestPlayer,
    private val id: String
) : TestGroup {
    internal var statusAnnouncer: (String) -> Unit = { message ->
        testPlayer.level.server.sendSystemMessage(Component.literal(message))
    }
    constructor(testPlayer: Player, id: String) : this(BlockTestPlayer(testPlayer), id)

    /**
     * 保留给旧版调用方的测试项复核结果。
     *
     * 示例：旧代码可继续传入 [PASSED] 完成人工复核。
     * 新代码禁止继续扩展此枚举，应改用 [BlockTestOptionResult]。
     *
     * @property displayName 状态行使用的中文名称
     */
    @Deprecated("使用 BlockTestOptionResult")
    enum class OptionResult(val displayName: String) {
        /** 测试项通过。 */
        PASSED("通过"),

        /** 测试项失败。 */
        FAILED("失败"),

        /** 测试项由操作员跳过。 */
        SKIPPED("跳过")
    }

    val options = ArrayList<Supplier<TestOption<*>>>()
    private val optionParamOverrides = linkedMapOf<Int, Map<String, String>>()
    var currentOption: TestOption<*>? = null
        private set
    private var pendingReviewOption: TestOption<*>? = null
    private var activeOptionIndex = -1
    private var nextOptionIndex = 0
    private var finishedAnnounced = false
    var announceGroupFinished: Boolean = true
    var reviewMode: BlockTestReviewMode = BlockTestReviewMode.MANUAL_VISUAL
    /**
     * 在 Option supplier 构造前通知外部重置模拟玩家。
     *
     * 示例：控制器在此回调中把 ONCE 轨道放回当前测试项起点。
     * 禁止在回调中推进测试组或调用当前 Option。
     */
    var optionStartListener: (BlockTestPlayer, Int) -> Unit = { _, _ -> }
    private var lastStatus = "未开始"

    override fun getUser(): Player {
        return testPlayer
    }

    override fun appendOption(sup: Supplier<TestOption<*>>): BlockTestGroup {
        options.add(sup)
        return this
    }

    fun optionCount(): Int = options.size

    fun optionIds(): List<String> {
        return options.map { supplier -> supplier.get().optionID() }
    }

    fun optionParamSpecs(): List<List<TestOptionParamSpec<*>>> {
        return options.map { supplier -> supplier.get().optionParamSpecs() }
    }

    fun setOptionParamOverrides(overrides: Map<Int, Map<String, String>>): BlockTestGroup {
        optionParamOverrides.clear()
        overrides.forEach { (index, values) ->
            optionParamOverrides[index] = LinkedHashMap(values)
        }
        return this
    }

    fun activeIndex(): Int = activeOptionIndex

    fun hasPendingReview(): Boolean = pendingReviewOption != null

    override fun groupID(): String = id

    fun statusLine(): String = buildCurrentStatusLine() ?: lastStatus

    override fun init() {
        activeOptionIndex = -1
        nextOptionIndex = 0
        currentOption = null
        pendingReviewOption = null
        finishedAnnounced = false
        lastStatus = "未开始"
    }

    override fun start() {
        init()
        startNextOption()
    }

    fun startAt(index: Int): Boolean {
        if (options.isEmpty() || index !in options.indices) {
            return false
        }
        init()
        nextOptionIndex = index
        startNextOption()
        return true
    }

    fun singleOption(index: Int): BlockTestGroup? {
        if (options.isEmpty() || index !in options.indices) {
            return null
        }
        return BlockTestGroup(testPlayer, id).also {
            it.announceGroupFinished = announceGroupFinished
            it.reviewMode = reviewMode
            it.statusAnnouncer = statusAnnouncer
            optionParamOverrides[index]?.let { values ->
                it.setOptionParamOverrides(mapOf(0 to values))
            }
        }.appendOption(options[index])
    }

    fun cancel() {
        currentOption?.stop()
        currentOption = null
        pendingReviewOption = null
        activeOptionIndex = -1
        nextOptionIndex = options.size
        lastStatus = "已停止"
        finishedAnnounced = true
    }

    override fun skipCurrent(): TestOption<*>? {
        return advanceCurrent(BlockTestOptionResult.SKIPPED)
    }

    fun completeCurrent(): TestOption<*>? {
        return advanceCurrent(BlockTestOptionResult.PASSED)
    }

    fun failCurrent(): TestOption<*>? {
        return advanceCurrent(BlockTestOptionResult.FAILED)
    }

    override fun isDone(): Boolean {
        return currentOption == null && pendingReviewOption == null && nextOptionIndex >= options.size
    }

    /**
     * 更新当前测试项，并在其 tick 前派发最新的模拟玩家姿态。
     *
     * 示例：控制器先设置玩家位置和 forward，再由本方法调用玩家更新回调与 Option tick。
     * 禁止在等待人工复核时继续派发玩家更新事件。
     */
    override fun doTick() {
        val option = currentOption ?: return
        try {
            TestOptionPlayerUpdateSupport.dispatch(option, testPlayer)
            option.doTick()
        } catch (e: Exception) {
            onOptionFailure(e, option)
            option.stop()
            currentOption = null
            finalizeOption(option, BlockTestOptionResult.FAILED, announce = false)
            startNextOption()
            return
        }

        if (!option.isValid()) {
            option.stop()
            currentOption = null
            if (option.reviewMode() == TestReviewMode.MANUAL_VISUAL &&
                reviewMode == BlockTestReviewMode.MANUAL_VISUAL
            ) {
                pendingReviewOption = option
                lastStatus = buildCurrentStatusLine() ?: "等待人工复核"
                return
            }
            finalizeOption(option, BlockTestOptionResult.PASSED, announce = false)
            startNextOption()
        }
    }

    private fun advanceCurrent(result: BlockTestOptionResult): TestOption<*>? {
        val option = currentOption ?: pendingReviewOption ?: return null
        if (currentOption != null) {
            option.stop()
        }
        currentOption = null
        pendingReviewOption = null
        finalizeOption(option, result, announce = true)
        startNextOption()
        return option
    }

    private fun startNextOption() {
        if (nextOptionIndex >= options.size) {
            if (isDone() && !finishedAnnounced) {
                if (announceGroupFinished) {
                    onGroupFinished()
                } else {
                    lastStatus = "测试 $id 已经全部完成"
                }
                finishedAnnounced = true
            }
            return
        }
        activeOptionIndex = nextOptionIndex
        optionStartListener(testPlayer, activeOptionIndex)
        val option = options[nextOptionIndex].get()
        nextOptionIndex++
        currentOption = option
        option.applyOptionParams(optionParamOverrides[activeOptionIndex].orEmpty())
        option.start()
        lastStatus = buildCurrentStatusLine() ?: "运行中"
    }

    private fun finalizeOption(option: TestOption<*>, result: BlockTestOptionResult, announce: Boolean) {
        when (result) {
            BlockTestOptionResult.PASSED -> {
                option.onSuccess()
                onOptionSuccess(option)
            }
            BlockTestOptionResult.FAILED, BlockTestOptionResult.SKIPPED -> option.onFailed()
        }
        lastStatus = "[测试 ${activeOptionIndex + 1}/${options.size}] ${option.optionID()} -> ${result.displayName}"
        if (announce) {
            statusAnnouncer("[方块测试 $id] $lastStatus")
        }
    }

    override fun onOptionFailure(t: Throwable, option: TestOption<*>) {
        val message = "测试项: ${option.optionID()} tick 异常: ${t.message ?: t::class.java.name}"
        lastStatus = message
        statusAnnouncer("[方块测试 $id] $message")
    }

    override fun onOptionSuccess(option: TestOption<*>) {
        // 方块测试在控制器界面中保留紧凑的状态行。
    }

    override fun onGroupFinished() {
        lastStatus = "测试 $id 已经全部完成"
        statusAnnouncer("[方块测试] $lastStatus")
    }

    private fun buildCurrentStatusLine(): String? {
        val option = currentOption ?: pendingReviewOption ?: return null
        val reviewTag = when {
            pendingReviewOption != null -> "待复核"
            option.reviewMode() == TestReviewMode.MANUAL_VISUAL -> "视觉"
            else -> "自动"
        }
        return "[测试 ${activeOptionIndex + 1}/${options.size}] [$reviewTag] ${option.optionID()}"
    }
}
