package cn.coostack.cooparticlesapi.animation

/**
 * 用 Lambda 创建一个简单的 [AnimateAction]。
 *
 * [durationTicks] 是 [onTick] 的准确调用次数，负数按 0 处理。0 tick 动作仍会依次执行
 * [onStart] 和 [onDone]。三个回调的接收者都是返回的 Action，可直接读取 `tickCount`
 * 或调用 `cancel()`。
 *
 * @param durationTicks 动作自身持续的 tick 数，默认 1
 * @param onStart 动作启动时调用一次
 * @param onDone 已启动动作结束时调用一次
 * @param onTick 每个活跃 tick 调用一次
 * @return 可传给 `then`、`AnimateNode.addAction` 或组合分支的 Action
 */
fun action(
    durationTicks: Int = 1,
    onStart: AnimateAction.() -> Unit = {},
    onDone: AnimateAction.() -> Unit = {},
    onTick: AnimateAction.() -> Unit = {},
): AnimateAction {
    return LambdaAnimateAction(
        durationTicks = durationTicks.coerceAtLeast(0),
        startAction = onStart,
        doneAction = onDone,
        tickAction = onTick,
    )
}

private class LambdaAnimateAction(
    private val durationTicks: Int,
    private val startAction: AnimateAction.() -> Unit,
    private val doneAction: AnimateAction.() -> Unit,
    private val tickAction: AnimateAction.() -> Unit,
) : AnimateAction() {
    override fun checkDone(): Boolean {
        return tickCount >= durationTicks
    }

    override fun tick() {
        tickAction(this)
        if (!done && tickCount + 1 >= durationTicks) {
            done = true
        }
    }

    override fun onStart() {
        startAction(this)
    }

    override fun onDone() {
        doneAction(this)
    }
}

internal data class AnimationStage(
    val action: AnimateAction,
    val delayTicks: Int,
)

internal class SequenceAnimateAction(
    stages: List<AnimationStage>,
) : AnimateAction(), NestedActionController {
    private val stages = stages.toList()
    private var currentIndex = 0
    private var remainingDelay = 0
    private var currentAction: AnimateAction? = null

    override fun checkDone(): Boolean {
        return currentIndex >= stages.size
    }

    override fun tick() {
        if (currentIndex >= stages.size) {
            return
        }

        var active = currentAction
        if (active == null) {
            if (remainingDelay > 0) {
                remainingDelay--
                return
            }
            active = startCurrentAction()
        }

        active.tickRuntime()
        if (active.done) {
            currentIndex++
            currentAction = null
            if (currentIndex < stages.size) {
                remainingDelay = stages[currentIndex].delayTicks
            } else {
                done = true
            }
        }
    }

    override fun onStart() {
        currentIndex = 0
        currentAction = null
        remainingDelay = stages.firstOrNull()?.delayTicks ?: 0
        if (stages.isNotEmpty() && remainingDelay == 0) {
            startCurrentAction()
        }
    }

    override fun onDone() = Unit

    override fun cancelNestedActions() {
        currentAction?.cancel()
    }

    override fun skipNestedActions() {
        currentAction?.skipRuntime()
    }

    private fun startCurrentAction(): AnimateAction {
        val active = stages[currentIndex].action
        currentAction = active
        active.startRuntime()
        return active
    }
}

internal class ParallelAnimateAction(
    actions: List<AnimateAction>,
) : AnimateAction(), NestedActionController {
    private val actions = actions.toList()

    override fun checkDone(): Boolean {
        return actions.all { it.done }
    }

    override fun tick() {
        actions.forEach {
            if (!it.done) {
                it.tickRuntime()
            }
        }
        if (actions.all { it.done }) {
            done = true
        }
    }

    override fun onStart() {
        for (action in actions) {
            if (done) {
                break
            }
            action.startRuntime()
        }
    }

    override fun onDone() = Unit

    override fun cancelNestedActions() {
        actions.forEach { it.cancel() }
    }

    override fun skipNestedActions() {
        actions.forEach { it.skipRuntime() }
    }
}

internal class WaitUntilAnimateAction(
    private val condition: () -> Boolean,
) : AnimateAction() {
    private var satisfied = false

    override fun checkDone(): Boolean {
        return satisfied
    }

    override fun tick() {
        satisfied = condition()
        done = satisfied
    }

    override fun onStart() {
        satisfied = false
    }

    override fun onDone() = Unit
}

internal class ConditionalAnimateAction(
    cases: List<AnimateCase>,
    private val blocked: Boolean,
) : AnimateAction(), NestedActionController {
    private data class Choice(
        val condition: (() -> Boolean)?,
        val action: SequenceAnimateAction,
    )

    private val choices = cases.map {
        Choice(
            condition = it.condition,
            action = it.branch.asAction(),
        )
    }
    private var selectionResolved = false
    private var selectedAction: SequenceAnimateAction? = null

    init {
        if (blocked) {
            require(cases.none { it.condition == null }) {
                "thenWhenBlocked does not accept otherwise"
            }
        } else {
            require(cases.count { it.condition == null } <= 1) {
                "thenWhen accepts at most one otherwise"
            }
        }
    }

    override fun checkDone(): Boolean {
        if (!selectionResolved) {
            return false
        }
        return selectedAction?.done != false
    }

    override fun tick() {
        if (!selectionResolved) {
            selectBranch()
            if (!selectionResolved) {
                return
            }
        }
        selectedAction?.tickRuntime()
        if (selectionResolved && selectedAction?.done != false) {
            done = true
        }
    }

    override fun onStart() {
        selectionResolved = false
        selectedAction = null
        if (!blocked) {
            selectBranch()
        }
    }

    override fun onDone() = Unit

    override fun cancelNestedActions() {
        selectedAction?.cancel()
    }

    override fun skipNestedActions() {
        selectedAction?.skipRuntime()
    }

    private fun selectBranch() {
        val matched = choices.firstOrNull { choice ->
            choice.condition?.invoke() == true
        } ?: choices.firstOrNull { it.condition == null }

        if (matched == null && blocked) {
            return
        }

        selectionResolved = true
        selectedAction = matched?.action
        selectedAction?.startRuntime()
    }
}

internal class LoopAnimateAction(
    private val times: Int?,
    intervalTicks: Int,
    private val condition: (() -> Boolean)?,
    private val branchFactory: (Int) -> AnimateBranch,
) : AnimateAction(), NestedActionController {
    private val intervalTicks = intervalTicks.coerceAtLeast(1)
    private var iterationIndex = 0
    private var remainingInterval = 0
    private var currentIteration: SequenceAnimateAction? = null
    private var finished = false

    override fun checkDone(): Boolean {
        return finished
    }

    override fun tick() {
        var current = currentIteration
        if (current == null) {
            if (remainingInterval > 0) {
                remainingInterval--
                if (remainingInterval > 0) {
                    return
                }
            }
            if (condition?.invoke() == false) {
                finished = true
                done = true
                return
            }
            current = startIteration()
        }

        current.tickRuntime()
        if (!current.done) {
            return
        }

        currentIteration = null
        iterationIndex++
        if (times != null && iterationIndex >= times) {
            finished = true
            done = true
        } else {
            remainingInterval = intervalTicks
        }
    }

    override fun onStart() {
        iterationIndex = 0
        remainingInterval = 0
        currentIteration = null
        finished = times != null && times <= 0
        if (!finished) {
            if (condition?.invoke() == false) {
                finished = true
            } else {
                startIteration()
            }
        }
    }

    override fun onDone() = Unit

    override fun cancelNestedActions() {
        currentIteration?.cancel()
    }

    override fun skipNestedActions() {
        currentIteration?.skipRuntime()
    }

    private fun startIteration(): SequenceAnimateAction {
        val iteration = branchFactory(iterationIndex).asAction()
        currentIteration = iteration
        iteration.startRuntime()
        return iteration
    }
}
