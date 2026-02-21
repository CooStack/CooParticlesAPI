package cn.coostack.cooparticlesapi.animation

import cn.coostack.cooparticlesapi.api.controler.Tickable
import java.util.function.Predicate

class Animate : Tickable<Animate> {
    private data class PendingNode(
        val node: AnimateNode,
        var waitTick: Int,
    )

    var timestarp = 0
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

    fun addNode(node: AnimateNode): Animate {
        return addNode(node, 0)
    }

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
        val iterator = activeNodes.iterator()
        while (iterator.hasNext()) {
            val node = iterator.next()
            if (!node.checkDone()) {
                node.cancel()
            }
            completedNow.add(node)
            iterator.remove()
        }

        completedNow.forEach { completeNode(it) }
        startReadyNodes()
        updateLegacyPointers()
        checkDoneState()
    }

    override fun addPreTickAction(action: Animate.() -> Unit): Tickable<Animate> {
        return this
    }

    override fun tick() {
        if (!display || done) return

        startReadyNodes()

        val completedNow = ArrayList<AnimateNode>()
        val iterator = activeNodes.iterator()
        while (iterator.hasNext()) {
            val node = iterator.next()
            if (!node.checkDone()) {
                node.tick()
            }
            if (node.checkDone()) {
                completedNow.add(node)
                iterator.remove()
            }
        }

        completedNow.forEach { completeNode(it) }

        if (cancelPredicates.any { it.test(this) }) {
            cancel()
            return
        }

        updateLegacyPointers()
        checkDoneState()
        timestarp++
    }

    fun start() {
        resetRuntimeState()
        nodes.forEach { (node, interval) ->
            enqueueNode(node, interval)
        }
        done = false
        display = true
    }

    fun cancel() {
        activeNodes.forEach {
            if (!it.checkDone()) {
                it.cancel()
            }
        }
        pendingNodes.clear()
        activeNodes.clear()
        queuedNodes.clear()
        completedNodes.clear()

        currentNode = null
        currentInterval = 0
        currentIndex = 0

        done = true
        display = false
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
            val pending = pendingNodes.removeFirst()
            if (pending.waitTick <= 0) {
                pending.node.onStart()
                activeNodes.add(pending.node)
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
        timestarp = 0
    }
}
