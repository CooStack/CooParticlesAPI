package cn.coostack.cooparticlesapi.animation

/**
 * 由父时间线推进的顺序分支。
 *
 * 分支不能单独注册到 `AnimateManager`。分支内的 `then` 按声明顺序执行，也可以继续添加
 * 等待、循环、并行和条件选择。
 */
class AnimateBranch internal constructor() {
    private val stages = ArrayList<AnimationStage>()

    /**
     * 在分支末尾追加任意 [AnimateAction]。
     *
     * [delayTicks] 相对分支内上一阶段完成时间计算，负数按 0 处理。
     *
     * @return 当前分支
     */
    fun then(action: AnimateAction, delayTicks: Int = 0): AnimateBranch {
        stages.add(AnimationStage(action, delayTicks.coerceAtLeast(0)))
        return this
    }

    /**
     * 追加一个持续 [ticks] tick 的显式等待阶段。
     *
     * @return 当前分支
     */
    fun waitTicks(ticks: Int): AnimateBranch {
        return then(action(durationTicks = ticks.coerceAtLeast(0)))
    }

    /**
     * 追加条件等待屏障。
     *
     * 到达后每 tick 调用一次 [condition]，返回 `false` 时停留，返回 `true` 时完成。
     * 它只暂停当前分支，不阻塞 Minecraft 线程。条件应为无副作用判定。
     *
     * @return 当前分支
     */
    fun waitUntil(condition: () -> Boolean): AnimateBranch {
        return then(WaitUntilAnimateAction(condition))
    }

    /**
     * 追加固定次数循环。每轮都会重新构建并完整执行 [block] 定义的分支。
     *
     * [times] 为 0 或负数时直接完成。第一轮立即启动；后续轮次在上一轮完成后等待
     * 至少 1 tick。轮次索引从 0 开始。
     *
     * @return 当前分支
     */
    fun thenLoop(
        times: Int,
        intervalTicks: Int = 1,
        block: AnimateBranch.(iterationIndex: Int) -> Unit,
    ): AnimateBranch {
        return then(
            LoopAnimateAction(
                times = times.coerceAtLeast(0),
                intervalTicks = intervalTicks,
                condition = null,
                branchFactory = { index -> branch { block(index) } },
            )
        )
    }

    /**
     * 追加 while 语义的条件循环。
     *
     * 首轮启动前以及每轮间隔结束后各检查一次 [condition]。已经启动的轮次会正常完成，
     * 条件在轮次中途变为 `false` 不会跳过其中 Action 的 `onDone`。
     *
     * @return 当前分支
     */
    fun thenLoopCondition(
        intervalTicks: Int = 1,
        condition: () -> Boolean,
        block: AnimateBranch.(iterationIndex: Int) -> Unit,
    ): AnimateBranch {
        return then(
            LoopAnimateAction(
                times = null,
                intervalTicks = intervalTicks,
                condition = condition,
                branchFactory = { index -> branch { block(index) } },
            )
        )
    }

    /**
     * 并行执行多个 Action，并等待全部完成。
     *
     * @return 当前分支
     */
    fun thenParallel(vararg actions: AnimateAction): AnimateBranch {
        return then(ParallelAnimateAction(actions.toList()))
    }

    /**
     * 并行执行多个顺序分支，并等待最慢分支完成。分支可以继续嵌套并行。
     *
     * @return 当前分支
     */
    fun thenParallel(vararg branches: AnimateBranch): AnimateBranch {
        return then(ParallelAnimateAction(branches.map { it.asAction() }))
    }

    /**
     * 到达时按声明顺序选择第一个成立的 `case`，只判断一次并锁定所选分支。
     * 无匹配且没有 `otherwise` 时直接完成。
     *
     * @return 当前分支
     */
    fun thenWhen(vararg cases: AnimateCase): AnimateBranch {
        return then(ConditionalAnimateAction(cases.toList(), blocked = false))
    }

    /**
     * 阻塞式条件选择。没有 `case` 成立时每 tick 重试，选中后锁定分支。
     *
     * 本方法不接受 `otherwise`，等待只暂停当前分支，不阻塞 Minecraft 线程。
     *
     * @return 当前分支
     */
    fun thenWhenBlocked(vararg cases: AnimateCase): AnimateBranch {
        return then(ConditionalAnimateAction(cases.toList(), blocked = true))
    }

    internal fun asAction(): SequenceAnimateAction {
        return SequenceAnimateAction(stages)
    }
}

/**
 * 创建一个由父时间线推进的顺序分支。
 *
 * @return 已完成配置的分支
 */
fun branch(block: AnimateBranch.() -> Unit): AnimateBranch {
    return AnimateBranch().apply(block)
}

/** 条件选择中的一个候选分支。 */
class AnimateCase internal constructor(
    internal val condition: (() -> Boolean)?,
    internal val branch: AnimateBranch,
)

/**
 * 创建条件候选分支。
 *
 * @param condition 到达选择阶段时使用的无副作用判定
 * @param block 选中后才会启动的分支
 */
fun case(
    condition: () -> Boolean,
    block: AnimateBranch.() -> Unit,
): AnimateCase {
    return AnimateCase(condition, branch(block))
}

/**
 * 创建 `thenWhen` 的兜底分支。
 *
 * `thenWhenBlocked` 不接受此分支。
 */
fun otherwise(block: AnimateBranch.() -> Unit): AnimateCase {
    return AnimateCase(null, branch(block))
}
