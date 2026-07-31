package cn.coostack.cooparticlesapi.animation

import cn.coostack.cooparticlesapi.api.controler.Tickable
import java.util.function.Predicate

/**
 * 动画时间线。
 *
 * 普通编排使用 [then]：前一个阶段完成后，后一个阶段才能启动。传入自定义
 * [AnimateAction] 或 [action] 创建的简单 Action，调度行为完全相同。
 *
 * 底层 Node API 仍然保留：`addNode` 添加并行根节点，Node 内的 Action 并行，子 Node
 * 在父 Node 的全部 Action 完成后启动。Node 图不负责并行汇合；需要等待多个分支时，
 * 使用 [thenParallel]。
 *
 * 下面的代码块中，框架能力保留真实 API 名称并使用 `// API:` 解释调度行为；调用者提供的
 * 自定义方法统一写成 `doSth(...)`，其用途、调用时机和次数由前置注释说明。
 *
 * ## 一个 Action 是怎样运行的
 *
 * ```kotlin
 * val calls = mutableListOf<String>()
 * val animate = Animate()
 *     // API: then 把一个 Action 接到链式时间线末尾。
 *     // API: action 用 Lambda 创建一个真实的 AnimateAction。
 *     .then(
 *         action(
 *             durationTicks = 2,
 *             // 阶段启动与结束时的调用者回调。
 *             onStart = { calls += "start" },
 *             onDone = { calls += "done" },
 *         ) {
 *             // 调用者逻辑，每个活跃 tick 执行一次。
 *             calls += "tick:$tickCount"
 *         }
 *     )
 *
 * // API: start 只重置时间线并入队，此时还没有调用 Action.onStart。
 * animate.start()
 * // API: 第一次 tick 调用 onStart，然后执行 onTick(tickCount = 0)。
 * animate.tick()
 * // API: 第二次 tick 执行 onTick(tickCount = 1)，随后调用 onDone。
 * animate.tick()
 *
 * // 最终 calls == ["start", "tick:0", "tick:1", "done"]
 * ```
 *
 * `durationTicks = 0` 时，阶段启动后只执行 `onStart -> onDone`，不会执行 `onTick`。
 * `action { ... }` 默认持续 1 tick。
 *
 * ## then 中执行固定 tick 数
 *
 * ```kotlin
 * val animate = Animate()
 *     // API: then 只负责把 Action 接到前一阶段之后。
 *     // API: durationTicks 属于 action 工厂，表示 onTick 的执行次数。
 *     .then(
 *         action(
 *             durationTicks = 60,
 *             // 阶段启动时执行一次调用者方法。
 *             onStart = { doSth() },
 *             // 正常完成，或启动后被 cancel、skip 时，执行一次调用者方法。
 *             onDone = { doSth() },
 *         ) {
 *             // 调用者逻辑准确执行 60 次，tickCount 依次为 0..59。
 *             doSth(tickCount)
 *         }
 *     )
 *     // API: 上面的 60 次 onTick 和 onDone 完成后，才启动这里。
 *     .then(action {
 *         // action 默认持续 1 tick，所以这个调用者方法只执行一次。
 *         doSth()
 *     })
 * ```
 *
 * 自定义 `CustomAnimateAction()` 没有 `durationTicks` 参数。它仍然可以直接传给 [then]，
 * 但持续时间由它自己的 `tick()` 和 `checkDone()` 实现决定。
 *
 * ## 顺序、延迟和等待
 *
 * ```kotlin
 * val animate = Animate()
 *     // 调用者实现的复杂 AnimateAction。
 *     // API: then 会等待它完整完成。
 *     .then(CustomAnimateAction())
 *     // API: delayTicks 从上一个阶段完成时开始计算。
 *     // API: 等待 5 tick 后，才调用这个 Action 的 onStart。
 *     .then(
 *         action(durationTicks = 60) {
 *             // 调用者提供的逐 tick 更新逻辑。
 *             doSth(tickCount)
 *         },
 *         delayTicks = 5,
 *     )
 *     // API: 增加一个独立的固定等待阶段。
 *     .waitTicks(10)
 *     // API: 到达这里后每 tick 调用一次 doSth()，该方法必须返回 Boolean。
 *     // API: 条件为 true 后才允许后续 Action 启动。
 *     .waitUntil {
 *         doSth()
 *     }
 *     .then(action {
 *         // 条件变为 true 后才启动；action 默认持续 1 tick，所以 doSth() 只执行一次。
 *         doSth()
 *     })
 * ```
 *
 * `delayTicks` 是阶段之间的启动延迟，不是 Action 持续时间。`waitUntil` 只暂停动画时间线，
 * 不会阻塞 Minecraft 线程，因此条件应是快速且无副作用的查询。
 *
 * ## 固定次数循环
 *
 * ```kotlin
 * val animate = Animate()
 *     // API: thenLoop 重复一个完整 branch，而不是重复调用一段裸 Lambda。
 *     // API: block 会为第 0、1、2 轮分别执行一次。
 *     .thenLoop(times = 3, intervalTicks = 5) { iterationIndex ->
 *         then(
 *             // API: 每一轮都会新建并启动这个 Action。
 *             action(
 *                 durationTicks = 20,
 *                 // 每轮 Action 启动时执行一次调用者方法。
 *                 onStart = { doSth(iterationIndex) },
 *                 // 每轮 Action 结束时执行一次调用者方法。
 *                 onDone = { doSth(iterationIndex) },
 *             ) {
 *                 // 调用者逻辑每轮执行 20 次，tickCount 每轮从 0 开始。
 *                 doSth(iterationIndex, tickCount)
 *             }
 *         )
 *     }
 *     // API: 三轮全部完成后才启动；最后一轮后没有额外 intervalTicks。
 *     .then(action {
 *         // 全部循环结束后才启动；action 默认持续 1 tick，所以 doSth() 只执行一次。
 *         doSth()
 *     })
 *
 * // 实际顺序：
 * // 第 0 轮 onStart -> 20 次 onTick -> onDone
 * // 等待 5 tick
 * // 第 1 轮 onStart -> 20 次 onTick -> onDone
 * // 等待 5 tick
 * // 第 2 轮 onStart -> 20 次 onTick -> onDone
 * // 循环外后续 Action 中的 doSth()
 * ```
 *
 * 一轮循环体是完整的 [AnimateBranch]，里面可以放多个顺序 `then`，也可以继续使用等待、
 * 并行和条件分支。`times = 0` 时不会构建循环体，直接进入外层后续阶段。
 *
 * ## 条件循环
 *
 * ```kotlin
 * val animate = Animate()
 *     // API: 按 while 语义重复一个完整 branch。
 *     // API: 第一轮前和每轮间隔结束后调用 doSth()，该方法必须返回 Boolean。
 *     .thenLoopCondition(
 *         intervalTicks = 2,
 *         condition = { doSth() },
 *     ) { iterationIndex ->
 *         then(
 *             // API: 已经启动的本轮会完整执行 20 tick。
 *             action(
 *                 durationTicks = 20,
 *                 // 条件中途变成 false 时，这个调用者方法仍会在本轮结束时执行一次。
 *                 onDone = { doSth(iterationIndex) },
 *             ) {
 *                 // 调用者提供的本轮逐 tick 逻辑。
 *                 doSth(iterationIndex, tickCount)
 *             }
 *         )
 *     }
 *     // API: 条件为 false，且已启动的当前轮完成后，才进入这里。
 *     .then(action {
 *         // 条件循环结束后才启动；action 默认持续 1 tick，所以 doSth() 只执行一次。
 *         doSth()
 *     })
 * ```
 *
 * 这是 while 语义：第一次检查就是 `false` 时执行 0 轮。如果某一轮执行期间条件变成
 * `false`，当前轮不会被截断，仍会执行完整的 `onTick` 和 `onDone`；框架只是不再启动下一轮。
 *
 * ## 普通并行和顺序分支并行
 *
 * ```kotlin
 * val animate = Animate()
 *     // API: 同时启动两个 Action，并等待较慢的右侧 Action 完成。
 *     // 两个 doSth() 分别在自己的每个活跃 tick 执行 10 次和 20 次。
 *     .thenParallel(
 *         action(durationTicks = 10) { doSth() },
 *         action(durationTicks = 20) { doSth() },
 *     )
 *     // API: 左右两个 Action 都完成后才启动。
 *     .then(action {
 *         // 并行汇合后才启动；action 默认持续 1 tick，所以 doSth() 只执行一次。
 *         doSth()
 *     })
 *     // API: branch 可以保存顺序结构；两个 branch 会同时启动并在末尾汇合。
 *     .thenParallel(
 *         branch {
 *             // 两个 action 均默认持续 1 tick；第一个 doSth() 完成后才执行第二个。
 *             then(action { doSth() })
 *             then(action { doSth() })
 *         },
 *         branch {
 *             // B 分支先用默认的 1 tick action 执行一次 doSth()。
 *             then(action { doSth() })
 *             // API: 上一个 Action 完成后，同时启动 B1 和 B2，并等待两者完成。
 *             // B1、B2 的 action 都默认持续 1 tick，各执行一次 doSth()。
 *             thenParallel(
 *                 branch { then(action { doSth() }) },
 *                 branch { then(action { doSth() }) },
 *             )
 *         },
 *     )
 *     // API: A 分支和整个 B 分支都完成后才启动。
 *     .then(action {
 *         // 所有分支汇合后才启动；action 默认持续 1 tick，所以 doSth() 只执行一次。
 *         doSth()
 *     })
 * ```
 *
 * [branch] 是由当前 Animate 推进的子时间线，不能单独交给 `AnimateManager`。嵌套
 * `thenParallel` 使用组合 Action 汇合，不依赖共享 Node。
 *
 * ## 一次性条件选择和阻塞式条件选择
 *
 * ```kotlin
 * val animate = Animate()
 *     // API: thenWhen 到达时只选择一次，按声明顺序选择第一个成立的 case。
 *     // 每个分支内的 action 默认持续 1 tick，只有选中分支的 doSth() 会执行一次。
 *     .thenWhen(
 *         // 条件 doSth() 必须返回 Boolean；为 true 时只启动这个分支。
 *         case({ doSth() }) {
 *             then(action { doSth() })
 *         },
 *         // 仅前一个条件不成立时，才调用这里的条件 doSth()。
 *         case({ doSth() }) {
 *             then(action { doSth() })
 *         },
 *         // API: otherwise 是可选的兜底分支。
 *         otherwise {
 *             then(action { doSth() })
 *         },
 *     )
 *     // API: 没有条件成立时停留，每 tick 重新检查。
 *     // API: 选中后锁定分支，不再改选。
 *     // 每个分支内的 action 默认持续 1 tick，只有锁定分支的 doSth() 会执行一次。
 *     .thenWhenBlocked(
 *         // 条件 doSth() 必须返回 Boolean；匹配前不会启动分支内的 Action。
 *         case({ doSth() }) {
 *             then(action { doSth() })
 *         },
 *         // 只有前一个条件为 false 时，当前 tick 才调用这里的条件 doSth()。
 *         case({ doSth() }) {
 *             then(action { doSth() })
 *         },
 *     )
 *     // API: 两次条件选择的已选分支都完成后才启动。
 *     .then(action {
 *         // 已选分支完成后才启动；action 默认持续 1 tick，所以 doSth() 只执行一次。
 *         doSth()
 *     })
 * ```
 *
 * `thenWhen` 没有匹配且没有 `otherwise` 时直接跳过。`thenWhenBlocked` 不允许
 * `otherwise`，否则就失去了等待条件成立的意义。未选中的分支不会执行任何生命周期回调。
 *
 * ## cancel、skip 和原始 Node 混用
 *
 * ```kotlin
 * // API: cancel 结束已启动 Action 并调用 onDone，不进入外层后续 then。
 * animate.cancel()
 * // API: skip 结束当前阶段并调用 onDone，随后继续外层后续 then。
 * animate.skip()
 *
 * Animate()
 *     // API: 添加调用者提供的 rootA，根节点间隔相对 Animate 启动时间计算。
 *     .addNode(rootA, interval = 10)
 *     // API: 添加调用者提供的 rootB。多个根节点互相并行。
 *     .addNode(rootB)
 *     // API: addNode 不改变 then 游标，所以下面属于单独的 then 链。
 *     // action 默认持续 1 tick，因此 doSth() 在该链式阶段执行一次。
 *     .then(action { doSth() })
 * ```
 *
 * 原始 `addNode` 不会改变 `then` 的顺序游标。建议在 [start] 前完成链式配置。现有 Node 图
 * 也不提供可靠的 DAG/all-parent join；需要汇合时应使用 [thenParallel]。
 */
class Animate : Tickable<Animate> {
    private data class PendingNode(
        val node: AnimateNode,
        var waitTick: Int,
    )

    var timestamp = 0
    val nodes = ArrayList<Pair<AnimateNode, Int>>()

    /**
     * 为了兼容旧逻辑保留的字段。
     */
    var currentNode: AnimateNode? = null
    var currentInterval = 0
    var currentIndex = 0

    var done = false
        private set
    var display = false
        private set

    val cancelPredicates = LinkedHashSet<Predicate<Animate>>()

    private val pendingNodes = ArrayDeque<PendingNode>()
    private val activeNodes = LinkedHashSet<AnimateNode>()
    private val queuedNodes = LinkedHashSet<AnimateNode>()
    private val completedNodes = LinkedHashSet<AnimateNode>()
    private val preTickActions = ArrayList<Animate.() -> Unit>()
    private val postTickActions = ArrayList<Animate.() -> Unit>()
    private var thenTail: AnimateNode? = null

    /**
     * 添加一个立即可启动的根节点。
     *
     * 多个根节点互相并行。根节点完成需要等待其中全部 Action；其子节点仍按各自父节点的
     * 完成时间继续推进。本方法不会改变链式 `then` 的顺序游标。
     *
     * @return 当前动画
     */
    fun addNode(node: AnimateNode): Animate {
        return addNode(node, 0)
    }

    /**
     * 添加一个延迟根节点。
     *
     * 多个根节点互相并行，[interval] 相对本次 [start] 的启动时间计算，负数按 0 处理。
     * 根节点的全部 Action 完成后该节点才完成。本方法不会改变链式 `then` 的顺序游标。
     *
     * @return 当前动画
     */
    fun addNode(node: AnimateNode, interval: Int): Animate {
        val safeInterval = interval.coerceAtLeast(0)
        node.setStartInterval(safeInterval)
        nodes.add(node to safeInterval)

        if (display && !done) {
            enqueueNode(node, safeInterval)
        }
        return this
    }

    /**
     * 在链式时间线末尾追加任意 [AnimateAction]。
     *
     * Action 在上一链式阶段完成后启动，[delayTicks] 相对上一阶段完成时间计算，负数按 0
     * 处理。第一个链式阶段的延迟相对 [start] 计算。自定义 Action 与 [action] 工厂产物
     * 使用完全相同的调度行为。
     *
     * 原始 [addNode] 不会移动链式游标：它添加独立并行根节点，后续 `then` 仍接在此前的
     * 链式阶段后面。
     *
     * @return 当前动画
     */
    fun then(action: AnimateAction, delayTicks: Int = 0): Animate {
        val stage = AnimateNode().addAction(action)
        val tail = thenTail
        if (tail == null) {
            addNode(stage, delayTicks)
        } else {
            tail.addNode(stage, delayTicks)
        }
        thenTail = stage
        return this
    }

    /**
     * 追加一个持续 [ticks] tick 的显式等待阶段。
     *
     * @return 当前动画
     */
    fun waitTicks(ticks: Int): Animate {
        return then(action(durationTicks = ticks.coerceAtLeast(0)))
    }

    /**
     * 追加条件等待屏障。
     *
     * 到达后每 tick 调用一次 [condition]。返回 `false` 时停留，返回 `true` 时完成并进入
     * 后续阶段。条件应为无副作用判定；等待只暂停本时间线，不阻塞 Minecraft 线程。
     *
     * @return 当前动画
     */
    fun waitUntil(condition: () -> Boolean): Animate {
        return then(WaitUntilAnimateAction(condition))
    }

    /**
     * 追加固定次数循环。每轮重新构建并完整执行 [block] 定义的分支。
     *
     * [times] 为 0 或负数时直接完成。第一轮立即启动，轮次索引从 0 开始；上一轮完成后
     * 等待至少 1 tick 才启动下一轮，最后一轮后没有额外间隔。
     *
     * @return 当前动画
     */
    fun thenLoop(
        times: Int,
        intervalTicks: Int = 1,
        block: AnimateBranch.(iterationIndex: Int) -> Unit,
    ): Animate {
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
     * 首轮前以及每轮间隔结束后各检查一次 [condition]。条件变为 `false` 时不再启动新一轮，
     * 但已经启动的轮次会正常完成并执行其中 Action 的 `onDone`。
     *
     * @return 当前动画
     */
    fun thenLoopCondition(
        intervalTicks: Int = 1,
        condition: () -> Boolean,
        block: AnimateBranch.(iterationIndex: Int) -> Unit,
    ): Animate {
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
     * 并行执行多个 Action，并等待全部完成后再进入后续链式阶段。
     *
     * @return 当前动画
     */
    fun thenParallel(vararg actions: AnimateAction): Animate {
        return then(ParallelAnimateAction(actions.toList()))
    }

    /**
     * 并行执行多个顺序分支，并等待最慢分支完成。分支可以嵌套并行。
     *
     * @return 当前动画
     */
    fun thenParallel(vararg branches: AnimateBranch): Animate {
        return then(ParallelAnimateAction(branches.map { it.asAction() }))
    }

    /**
     * 追加一次性条件选择。
     *
     * 到达时按声明顺序选择第一个条件成立的 `case` 并锁定。无匹配时执行 `otherwise`；
     * 没有 `otherwise` 则直接跳过。后续阶段会等待所选分支完整结束。
     *
     * @return 当前动画
     */
    fun thenWhen(vararg cases: AnimateCase): Animate {
        return then(ConditionalAnimateAction(cases.toList(), blocked = false))
    }

    /**
     * 追加阻塞式条件选择。
     *
     * 没有 `case` 成立时停留并每 tick 重试；选中第一个成立分支后锁定，不再判断。
     * 本方法不接受 `otherwise`，等待只暂停动画时间线，不阻塞 Minecraft 线程。
     *
     * @return 当前动画
     */
    fun thenWhenBlocked(vararg cases: AnimateCase): Animate {
        return then(ConditionalAnimateAction(cases.toList(), blocked = true))
    }

    /**
     * 当条件满足时取消当前动画。
     */
    fun addCancelPredicate(predicate: Predicate<Animate>): Animate {
        cancelPredicates.add(predicate)
        return this
    }

    /**
     * 跳过当前所有活跃节点，并继续推进到它们的子节点。
     */
    fun skip() {
        if (!display || done) {
            return
        }

        val completedNow = ArrayList<AnimateNode>()
        val activeSnapshot = activeNodes.toList()
        for (node in activeSnapshot) {
            if (!activeNodes.remove(node)) {
                continue
            }
            node.skip()
            if (!display || done) {
                return
            }
            completedNow.add(node)
        }

        completedNow.forEach { completeNode(it) }
        startReadyNodes()
        updateLegacyPointers()
        checkDoneState()
    }

    override fun addPreTickAction(action: Animate.() -> Unit): Tickable<Animate> {
        preTickActions.add(action)
        return this
    }

    override fun addPreTickActionPost(action: Animate.() -> Unit): Tickable<Animate> {
        postTickActions.add(action)
        return this
    }

    override fun tick() {
        if (!display || done) return
        val stableSize = preTickActions.size
        var index = 0
        while (index < stableSize) {
            preTickActions[index](this)
            index++
        }
        if (!display || done) {
            return
        }

        startReadyNodes()
        if (!display || done) {
            return
        }

        val completedNow = ArrayList<AnimateNode>()
        val activeSnapshot = activeNodes.toList()
        for (node in activeSnapshot) {
            if (node !in activeNodes) {
                continue
            }
            node.tick()
            if (!display || done) {
                return
            }
            if (node.checkDone()) {
                completedNow.add(node)
                activeNodes.remove(node)
            }
        }

        completedNow.forEach { completeNode(it) }

        if (cancelPredicates.any { it.test(this) }) {
            cancel()
            return
        }

        updateLegacyPointers()
        checkDoneState()
        postTickActions.forEach { it(this) }
        timestamp++
    }

    /**
     * 从头开始播放动画。
     *
     * 本方法重置调度状态；Action 会在节点实际启动时重置并执行 `onStart`。对同一实例再次
     * 调用本方法会重播内置循环、条件选择、并行分支及其嵌套时间线。如果动画仍在播放，
     * 会先结束全部已启动 Action 并执行其 `onDone`。
     */
    fun start() {
        if (display && !done) {
            cancel()
        }
        resetRuntimeState()
        nodes.forEach { (node, interval) ->
            enqueueNode(node, interval)
        }
        done = false
        display = true
    }

    /**
     * 取消当前播放。
     *
     * 所有已启动 Action 会结束并执行一次 `onDone`，待启动节点不会执行生命周期回调，
     * 且不会进入任何后续阶段。
     */
    fun cancel() {
        if (done && !display) {
            return
        }

        done = true
        display = false
        val activeSnapshot = activeNodes.toList()
        activeNodes.clear()
        pendingNodes.clear()
        queuedNodes.clear()
        completedNodes.clear()
        activeSnapshot.forEach { it.cancel() }

        currentNode = null
        currentInterval = 0
        currentIndex = 0
    }

    private fun enqueueNode(node: AnimateNode, interval: Int) {
        if (!queuedNodes.add(node)) {
            return
        }
        pendingNodes.addLast(
            PendingNode(
                node = node,
                waitTick = interval.coerceAtLeast(0),
            )
        )
    }

    private fun startReadyNodes() {
        val pendingSize = pendingNodes.size
        repeat(pendingSize) {
            if (!display || done) {
                return
            }
            val pending = pendingNodes.removeFirst()
            if (pending.waitTick <= 0) {
                activeNodes.add(pending.node)
                pending.node.onStart()
            } else {
                pending.waitTick--
                pendingNodes.addLast(pending)
            }
        }
    }

    private fun completeNode(node: AnimateNode) {
        if (!completedNodes.add(node)) {
            return
        }
        node.nextNodes.forEach { child ->
            enqueueNode(child, child.startInterval)
        }
    }

    private fun updateLegacyPointers() {
        currentNode = activeNodes.firstOrNull()
        currentInterval = currentNode?.startInterval ?: 0
        currentIndex = completedNodes.size
    }

    private fun checkDoneState() {
        if (activeNodes.isEmpty() && pendingNodes.isEmpty()) {
            done = true
            display = false
        }
    }

    private fun resetRuntimeState() {
        pendingNodes.clear()
        activeNodes.clear()
        queuedNodes.clear()
        completedNodes.clear()

        currentNode = null
        currentInterval = 0
        currentIndex = 0
        timestamp = 0
    }
}
