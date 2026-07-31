package cn.coostack.cooparticlesapi.animation

import java.util.function.Predicate

/**
 * 底层动画节点。
 *
 * 一个节点内的 Action 并行执行，子节点则在父节点完成后启动。现有 Node 图不提供
 * 可靠的 DAG 汇合语义；同一个子节点被多个父节点共享时，不能视为 all-parent join。
 */
class AnimateNode {
    /**
     * 当前节点中并行执行的动作集合。
     */
    val animates = LinkedHashSet<AnimateAction>()

    val nextNodes = LinkedHashSet<AnimateNode>()

    val cancelPredicates = LinkedHashSet<Predicate<AnimateNode>>()

    /**
     * 父节点完成后，本节点启动前需要等待的游戏刻数。
     */
    var startInterval = 0
        private set

    var timestrap = 0
    private var stopping = false

    /** 重置并启动本节点内的全部 Action。 */
    fun onStart() {
        timestrap = 0
        stopping = false
        for (action in animates) {
            if (stopping) {
                break
            }
            action.startRuntime()
        }
    }

    /**
     * 设置父节点完成后、本节点启动前的等待 tick 数。
     *
     * 负数按 0 处理。
     *
     * @return 当前节点
     */
    fun setStartInterval(interval: Int): AnimateNode {
        startInterval = interval.coerceAtLeast(0)
        return this
    }

    /**
     * 添加一个新的子节点。
     *
     * 子节点在当前节点的全部 Action 完成后启动；同一父节点下的多个子节点并行。
     * 本重载的间隔为 0。
     *
     * @return 新创建的子节点，不是当前节点
     */
    fun addNode(): AnimateNode {
        val child = AnimateNode()
        nextNodes.add(child)
        return child
    }

    /**
     * 添加一个新的延迟子节点。
     *
     * [interval] 相对当前节点完成时间计算。同一父节点下的多个子节点互为并行分支，
     * 当前节点的全部 Action 完成后才开始计算各自间隔。
     *
     * @return 新创建的子节点，不是当前节点
     */
    fun addNode(interval: Int): AnimateNode {
        val child = AnimateNode().setStartInterval(interval)
        nextNodes.add(child)
        return child
    }

    /**
     * 添加已有节点作为子节点，间隔为 0。
     *
     * 子节点在当前节点完成后启动；多个兄弟子节点并行。共享同一个 [node] 不构成可靠的
     * DAG 汇合，因为框架不会等待它的所有父节点。
     *
     * @return 当前节点。此返回行为与 [addNode] 创建新节点的重载不同
     */
    fun addNode(node: AnimateNode): AnimateNode {
        nextNodes.add(node)
        return this
    }

    /**
     * 添加已有节点作为延迟子节点。
     *
     * [interval] 相对当前节点完成时间计算。同一父节点的多个子节点并行，当前节点完成
     * 需要等待本节点内的全部 Action。共享子节点不提供可靠的 all-parent join。
     *
     * @return 当前节点。此返回行为与 [addNode] 创建新节点的重载不同
     */
    fun addNode(node: AnimateNode, interval: Int): AnimateNode {
        node.setStartInterval(interval)
        return addNode(node)
    }

    /**
     * 向节点添加一个 Action。
     *
     * 同一节点中的所有 Action 并行启动、分别推进。只有全部 Action 都完成后，本节点才算
     * 完成并允许子节点进入等待或启动阶段。
     *
     * @return 当前节点
     */
    fun addAction(action: AnimateAction): AnimateNode {
        animates.add(action)
        return this
    }

    /**
     * 当条件满足时取消当前节点。
     */
    fun addCancelPredicate(predicate: Predicate<AnimateNode>): AnimateNode {
        cancelPredicates.add(predicate)
        return this
    }

    /** 推进节点内所有已到达执行时间的 Action 一个 tick。 */
    fun tick() {
        animates.forEach {
            if (it.timeInterval > timestrap) {
                it.checkRuntime()
                return@forEach
            }
            it.tickRuntime()
        }
        if (cancelPredicates.any { it.test(this) }) {
            cancel()
            return
        }
        timestrap++
    }

    /**
     * 返回节点是否完成，不会再次调用 Action 的 `checkDone()`。
     *
     * @return 节点内全部 Action 是否已经结束
     */
    fun checkDone(): Boolean {
        return animates.all { it.done }
    }

    /** 取消节点内全部 Action；只有已经启动的 Action 会执行 `onDone`。 */
    fun cancel() {
        stopping = true
        animates.forEach { it.cancel() }
    }

    internal fun skip() {
        stopping = true
        animates.forEach { it.skipRuntime() }
    }
}
