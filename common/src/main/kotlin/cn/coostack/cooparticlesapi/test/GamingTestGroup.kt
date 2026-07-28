package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.test.api.TestGroup
import cn.coostack.cooparticlesapi.test.api.TestOption
import cn.coostack.cooparticlesapi.test.api.TestReviewMode
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import java.util.function.Supplier

class GamingTestGroup(val testPlayer: Player, val id: String) : TestGroup {
    enum class OptionResult(val displayName: String) {
        PASSED("通过"),
        FAILED("失败"),
        SKIPPED("跳过")
    }

    val options = ArrayList<Supplier<TestOption<*>>>()
    var currentOption: TestOption<*>? = null
    private var pendingReviewOption: TestOption<*>? = null
    private var activeOptionIndex = -1
    private var nextOptionIndex = 0
    private var actionBarCooldown = 0

    override fun getUser(): Player {
        return testPlayer
    }

    override fun appendOption(sup: Supplier<TestOption<*>>): GamingTestGroup {
        options.add(sup)
        return this
    }

    override fun init() {
        activeOptionIndex = -1
        nextOptionIndex = 0
        currentOption = null
        pendingReviewOption = null
        actionBarCooldown = 0
    }

    override fun start() {
        init()
        startNextOption()
    }

    fun cancel() {
        currentOption?.stop()
        currentOption = null
        pendingReviewOption = null
        activeOptionIndex = -1
        nextOptionIndex = options.size
        actionBarCooldown = 0
    }

    override fun skipCurrent(): TestOption<*>? {
        return advanceCurrent(OptionResult.SKIPPED)
    }

    override fun isDone(): Boolean {
        return currentOption == null && pendingReviewOption == null && nextOptionIndex >= options.size
    }

    override fun doTick() {
        pushStatusHintIfNeeded()
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
                pushStatusHint(force = true)
                sendManualReviewPrompt(option)
                return
            }
            finalizeOption(option, OptionResult.PASSED, announce = false)
            startNextOption()
        }
    }

    override fun onOptionFailure(t: Throwable, option: TestOption<*>) {
        testPlayer.sendSystemMessage(
            Component.literal(
                """
                    测试项: ${option.optionID()} 在进行tick操作时发生了异常
                    异常: ${t.stackTraceToString()}
                """.trimIndent()
            )
        )
    }

    override fun onOptionSuccess(option: TestOption<*>) {
        testPlayer.sendSystemMessage(
            Component.literal(
                "测试项: ${option.optionID()} 执行完成"
            )
        )
    }

    override fun onGroupFinished() {
        // 当前测试项已经结束。
        testPlayer.sendSystemMessage(
            Component.literal(
                "测试 $id 已经全部完成"
            )
        )
    }


    override fun groupID(): String {
        return id
    }

    fun completeCurrent(): TestOption<*>? {
        return advanceCurrent(OptionResult.PASSED)
    }

    fun failCurrent(): TestOption<*>? {
        return advanceCurrent(OptionResult.FAILED)
    }

    fun jumpRelative(offset: Int): TestOption<*>? {
        if (options.isEmpty()) {
            return null
        }
        val anchor = when {
            activeOptionIndex >= 0 -> activeOptionIndex
            nextOptionIndex > 0 -> nextOptionIndex - 1
            else -> 0
        }
        val target = (anchor + offset).coerceIn(0, options.lastIndex)
        return jumpTo(target)
    }

    fun jumpToFirst(): TestOption<*>? {
        return jumpTo(0)
    }

    fun jumpToLast(): TestOption<*>? {
        if (options.isEmpty()) {
            return null
        }
        return jumpTo(options.lastIndex)
    }

    private fun jumpTo(index: Int): TestOption<*>? {
        if (options.isEmpty()) {
            return null
        }
        val target = index.coerceIn(0, options.lastIndex)
        val old = currentOption ?: pendingReviewOption
        if (old != null) {
            currentOption?.stop()
            currentOption = null
            pendingReviewOption = null
            finalizeOption(old, OptionResult.SKIPPED, announce = false)
        }
        activeOptionIndex = -1
        nextOptionIndex = target
        sendJumpMessage(target, old)
        startNextOption()
        return old
    }

    private fun advanceCurrent(result: OptionResult): TestOption<*>? {
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
            if (isDone()) {
                onGroupFinished()
            }
            return
        }
        activeOptionIndex = nextOptionIndex
        val option = options[nextOptionIndex].get()
        nextOptionIndex++
        currentOption = option
        option.applyOptionParams(emptyMap())
        option.start()
        actionBarCooldown = 0
        sendOptionStarted(option)
    }

    private fun finalizeOption(option: TestOption<*>, result: OptionResult, announce: Boolean) {
        when (result) {
            OptionResult.PASSED -> option.onSuccess()
            OptionResult.FAILED, OptionResult.SKIPPED -> option.onFailed()
        }
        if (announce) {
            testPlayer.sendSystemMessage(
                Component.literal(
                    "[测试 ${activeOptionIndex + 1}/${options.size}] ${option.optionID()} -> ${result.displayName}"
                )
            )
        }
    }

    private fun sendOptionStarted(option: TestOption<*>) {
        val mode = when (option.reviewMode()) {
            TestReviewMode.AUTO -> "自动"
            TestReviewMode.MANUAL_VISUAL -> "人工视觉"
        }
        testPlayer.sendSystemMessage(
            Component.literal(
                "[测试 ${activeOptionIndex + 1}/${options.size}] $mode | ${option.optionID()}"
            )
        )
        option.reviewDescription()?.takeIf { it.isNotBlank() }?.let { description ->
            testPlayer.sendSystemMessage(Component.literal("说明: $description"))
        }
        if (option.reviewMode() == TestReviewMode.MANUAL_VISUAL) {
            testPlayer.sendSystemMessage(
                Component.literal(
                    "控制: 右键/下一项键=通过并继续, 上一项键=回退, 潜行右键或双击下一项键=快进+5, 长按下一项键=跳到最后"
                )
            )
        }
        pushStatusHint(force = true)
    }

    private fun sendManualReviewPrompt(option: TestOption<*>) {
        testPlayer.sendSystemMessage(
            Component.literal(
                "[测试 ${activeOptionIndex + 1}/${options.size}] 待人工复核 | ${option.optionID()}"
            )
        )
        option.reviewDescription()?.takeIf { it.isNotBlank() }?.let { description ->
            testPlayer.sendSystemMessage(Component.literal("复核项: $description"))
        }
        testPlayer.sendSystemMessage(
            Component.literal(
                "操作: 右键/下一项键=记为通过并继续, 回退键=回到上一项, 潜行右键或双击下一项键=快进+5"
            )
        )
        pushStatusHint(force = true)
    }

    private fun sendJumpMessage(targetIndex: Int, previous: TestOption<*>?) {
        val jumpedFrom = previous?.optionID()?.let { "，离开: $it" } ?: ""
        testPlayer.sendSystemMessage(
            Component.literal(
                "跳转到测试 ${targetIndex + 1}/${options.size}$jumpedFrom"
            )
        )
    }

    private fun pushStatusHintIfNeeded() {
        if (actionBarCooldown > 0) {
            actionBarCooldown--
            return
        }
        pushStatusHint(force = false)
    }

    private fun pushStatusHint(force: Boolean) {
        val message = buildCurrentStatusLine() ?: return
        testPlayer.displayClientMessage(Component.literal(message), true)
        actionBarCooldown = if (force) 10 else 20
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
