package cn.coostack.cooparticlesapi.test.block

import cn.coostack.cooparticlesapi.test.api.TestGroup
import cn.coostack.cooparticlesapi.test.api.TestOption
import cn.coostack.cooparticlesapi.test.api.TestOptionParamSpec
import cn.coostack.cooparticlesapi.test.api.TestReviewMode
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import java.util.function.Supplier

class BlockTestGroup(
    val testPlayer: BlockTestPlayer,
    private val id: String
) : TestGroup {
    constructor(testPlayer: Player, id: String) : this(BlockTestPlayer(testPlayer), id)

    enum class OptionResult(val displayName: String) {
        PASSED("通过"),
        FAILED("失败"),
        SKIPPED("跳过")
    }

    val options = ArrayList<Supplier<TestOption>>()
    private val optionParamOverrides = linkedMapOf<Int, Map<String, String>>()
    var currentOption: TestOption? = null
        private set
    private var pendingReviewOption: TestOption? = null
    private var activeOptionIndex = -1
    private var nextOptionIndex = 0
    private var finishedAnnounced = false
    var announceGroupFinished: Boolean = true
    private var lastStatus = "未开始"

    override fun getUser(): Player {
        return testPlayer
    }

    override fun appendOption(sup: Supplier<TestOption>): BlockTestGroup {
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

    override fun skipCurrent(): TestOption? {
        return advanceCurrent(OptionResult.SKIPPED)
    }

    fun completeCurrent(): TestOption? {
        return advanceCurrent(OptionResult.PASSED)
    }

    fun failCurrent(): TestOption? {
        return advanceCurrent(OptionResult.FAILED)
    }

    override fun isDone(): Boolean {
        return currentOption == null && pendingReviewOption == null && nextOptionIndex >= options.size
    }

    override fun doTick() {
        val option = currentOption ?: return
        try {
            option.doTick()
        } catch (e: Exception) {
            onOptionFailure(e, option)
            option.stop()
            currentOption = null
            finalizeOption(option, OptionResult.FAILED, announce = false)
            startNextOption()
            return
        }

        if (!option.isValid()) {
            option.stop()
            currentOption = null
            if (option.reviewMode() == TestReviewMode.MANUAL_VISUAL) {
                pendingReviewOption = option
                lastStatus = buildCurrentStatusLine() ?: "等待人工复核"
                return
            }
            finalizeOption(option, OptionResult.PASSED, announce = false)
            startNextOption()
        }
    }

    private fun advanceCurrent(result: OptionResult): TestOption? {
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
        val option = options[nextOptionIndex].get()
        nextOptionIndex++
        currentOption = option
        option.applyOptionParams(optionParamOverrides[activeOptionIndex].orEmpty())
        option.start()
        lastStatus = buildCurrentStatusLine() ?: "运行中"
    }

    private fun finalizeOption(option: TestOption, result: OptionResult, announce: Boolean) {
        when (result) {
            OptionResult.PASSED -> {
                option.onSuccess()
                onOptionSuccess(option)
            }
            OptionResult.FAILED, OptionResult.SKIPPED -> option.onFailed()
        }
        lastStatus = "[测试 ${activeOptionIndex + 1}/${options.size}] ${option.optionID()} -> ${result.displayName}"
        if (announce) {
            testPlayer.level.server.sendSystemMessage(Component.literal("[方块测试 $id] $lastStatus"))
        }
    }

    override fun onOptionFailure(t: Throwable, option: TestOption) {
        val message = "测试项: ${option.optionID()} tick 异常: ${t.message ?: t::class.java.name}"
        lastStatus = message
        testPlayer.level.server.sendSystemMessage(Component.literal("[方块测试 $id] $message"))
    }

    override fun onOptionSuccess(option: TestOption) {
        // Block tests keep the compact status line in the controller GUI.
    }

    override fun onGroupFinished() {
        lastStatus = "测试 $id 已经全部完成"
        testPlayer.level.server.sendSystemMessage(Component.literal("[方块测试] $lastStatus"))
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
