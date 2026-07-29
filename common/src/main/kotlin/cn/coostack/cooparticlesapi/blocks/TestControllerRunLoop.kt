package cn.coostack.cooparticlesapi.blocks

import cn.coostack.cooparticlesapi.test.block.BlockTestGroup
import cn.coostack.cooparticlesapi.test.block.BlockTestMode
import cn.coostack.cooparticlesapi.test.block.BlockTestOptionResult
import cn.coostack.cooparticlesapi.test.block.BlockTestReviewMode
import cn.coostack.cooparticlesapi.test.block.BlockTestPlayer

internal class TestControllerRunLoop(
    private val groupFactory: () -> BlockTestGroup?,
    private val modeProvider: () -> BlockTestMode,
    private val repeatIndexProvider: () -> Boolean,
    private val repeatDelayTicksProvider: () -> Int,
    private val buildFailureStatusProvider: () -> String = { "无法创建测试组" }
) {
    private var activeGroup: BlockTestGroup? = null
    var waitTicks: Int = 0
        private set
    var shouldAutoRun: Boolean = false
        private set
    var lastStatus: String = "空闲"
        private set

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

    fun currentIndex(): Int {
        if (!isRunning()) {
            return 0
        }
        return activeGroup?.activeIndex()?.takeIf { it >= 0 }?.plus(1) ?: 0
    }

    fun hasPendingReview(): Boolean = activeGroup?.hasPendingReview() == true

    /**
     * 返回当前测试组复用的模拟玩家。
     *
     * 示例：控制器 tick 在调用测试项前更新该玩家姿态。
     * 禁止把返回值保存到下一轮测试；测试组结束后引用不再属于运行时。
     *
     * @return 当前组玩家，没有活动组时返回 `null`
     */
    fun activeTestPlayer(): BlockTestPlayer? = activeGroup?.testPlayer

    fun start(): Boolean {
        cancel(clearWait = true)
        shouldAutoRun = true
        if (startFreshGroup()) {
            return true
        }
        shouldAutoRun = false
        return false
    }

    fun stop() {
        shouldAutoRun = false
        cancel(clearWait = true)
        lastStatus = "已停止"
    }

    fun tick(): Boolean {
        val previousShouldAutoRun = shouldAutoRun
        val previousWaitTicks = waitTicks
        if (shouldAutoRun && activeGroup == null && waitTicks <= 0) {
            if (!startFreshGroup()) {
                shouldAutoRun = false
            }
        }
        if (waitTicks > 0) {
            waitTicks--
            if (waitTicks <= 0 && !startFreshGroup()) {
                shouldAutoRun = false
            }
            return persistentStateChanged(previousShouldAutoRun, previousWaitTicks)
        }

        val group = activeGroup
            ?: return persistentStateChanged(previousShouldAutoRun, previousWaitTicks)
        if (!group.isDone()) {
            group.doTick()
        }
        if (group.isDone()) {
            handleGroupDone()
        }
        return persistentStateChanged(previousShouldAutoRun, previousWaitTicks)
    }

    fun reviewCurrent(result: BlockTestOptionResult): Boolean {
        val group = activeGroup ?: return false
        if (!group.hasPendingReview()) return false
        when (result) {
            BlockTestOptionResult.PASSED -> group.completeCurrent()
            BlockTestOptionResult.FAILED -> group.failCurrent()
            BlockTestOptionResult.SKIPPED -> group.skipCurrent()
        } ?: return false
        lastStatus = group.statusLine()
        if (group.isDone()) {
            handleGroupDone()
        }
        return true
    }

    fun cancel(clearWait: Boolean) {
        activeGroup?.cancel()
        activeGroup = null
        if (clearWait) {
            waitTicks = 0
        }
    }

    fun restore(shouldAutoRun: Boolean, waitTicks: Int) {
        activeGroup?.cancel()
        activeGroup = null
        this.shouldAutoRun = shouldAutoRun
        this.waitTicks = if (shouldAutoRun) waitTicks.coerceAtLeast(0) else 0
        lastStatus = when {
            this.waitTicks > 0 -> "等待 ${this.waitTicks} tick 后重复"
            shouldAutoRun -> "等待自动启动"
            else -> "空闲"
        }
    }

    private fun startFreshGroup(): Boolean {
        val group = groupFactory() ?: run {
            lastStatus = buildFailureStatusProvider()
            return false
        }
        group.reviewMode = if (modeProvider() == BlockTestMode.INDEX && repeatIndexProvider()) {
            BlockTestReviewMode.AUTO
        } else {
            BlockTestReviewMode.MANUAL_VISUAL
        }
        group.announceGroupFinished = shouldAnnounceGroupFinished()
        activeGroup = group
        waitTicks = 0
        group.start()
        lastStatus = group.statusLine()
        return true
    }

    private fun handleGroupDone() {
        activeGroup = null
        when (modeProvider()) {
            BlockTestMode.SEQUENTIAL -> {
                shouldAutoRun = false
                lastStatus = "已完成"
            }
            BlockTestMode.LOOP -> {
                shouldAutoRun = true
                lastStatus = "循环重启"
                if (!startFreshGroup()) {
                    shouldAutoRun = false
                }
            }
            BlockTestMode.INDEX -> {
                if (repeatIndexProvider()) {
                    shouldAutoRun = true
                    waitTicks = repeatDelayTicksProvider().coerceAtLeast(0)
                    lastStatus = if (waitTicks > 0) "等待 ${waitTicks} tick 后重复" else "重复重启"
                    if (waitTicks == 0 && !startFreshGroup()) {
                        shouldAutoRun = false
                    }
                } else {
                    shouldAutoRun = false
                    lastStatus = "已完成索引"
                }
            }
        }
    }

    private fun shouldAnnounceGroupFinished(): Boolean {
        return when (modeProvider()) {
            BlockTestMode.SEQUENTIAL -> true
            BlockTestMode.LOOP -> false
            BlockTestMode.INDEX -> !repeatIndexProvider()
        }
    }

    private fun persistentStateChanged(previousShouldAutoRun: Boolean, previousWaitTicks: Int): Boolean {
        return shouldAutoRun != previousShouldAutoRun || waitTicks != previousWaitTicks
    }
}
