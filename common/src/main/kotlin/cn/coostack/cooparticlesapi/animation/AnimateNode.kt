package cn.coostack.cooparticlesapi.animation

import java.util.function.Predicate

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

    fun onStart() {
        timestrap = 0
        for (action in animates) {
            action.done = false
            action.tickCount = 0
            action.onStart()
        }
    }

    fun setStartInterval(interval: Int): AnimateNode {
        startInterval = interval.coerceAtLeast(0)
        return this
    }

    fun addNode(): AnimateNode {
        val child = AnimateNode()
        nextNodes.add(child)
        return child
    }

    fun addNode(interval: Int): AnimateNode {
        val child = AnimateNode().setStartInterval(interval)
        nextNodes.add(child)
        return child
    }

    fun addNode(node: AnimateNode): AnimateNode {
        nextNodes.add(node)
        return this
    }

    fun addNode(node: AnimateNode, interval: Int): AnimateNode {
        node.setStartInterval(interval)
        return addNode(node)
    }

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

    fun tick() {
        animates.forEach {
            if (it.check() || it.timeInterval > timestrap) {
                return@forEach
            }
            it.doTick()
            if (it.check()) {
                it.onDone()
            }
        }
        if (cancelPredicates.any { it.test(this) }) {
            cancel()
            return
        }
        timestrap++
    }

    fun checkDone(): Boolean {
        return animates.all { it.check() }
    }

    fun cancel() {
        animates.forEach {
            if (!it.check()) {
                it.cancel()
                it.onDone()
            }
        }
    }
}
