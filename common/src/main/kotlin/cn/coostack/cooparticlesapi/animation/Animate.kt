package cn.coostack.cooparticlesapi.animation

import cn.coostack.cooparticlesapi.api.controler.Tickable
import java.util.function.Predicate


class Animate : Tickable<Animate> {
    var timestarp = 0
    val nodes = ArrayList<Pair<AnimateNode, Int>>()
    var currentNode: AnimateNode? = null
    var currentInterval = 0
    var currentIndex = 0
    var done = false
        private set
    var display = false
        private set
    val cancelPredicates = HashSet<Predicate<Animate>>()

    fun addNode(node: AnimateNode): Animate {
        nodes.add(node to 0)
        return this
    }

    fun addNode(node: AnimateNode, interval: Int): Animate {
        nodes.add(node to interval)
        return this
    }

    /**
     * 如果条件符合 就cancel这个animate
     *
     * @param predicate
     * @return
     */
    fun addCancelPredicate(predicate: Predicate<Animate>): Animate {
        cancelPredicates.add(predicate)
        return this
    }

    fun skip() {
        if (!display || done) {
            return
        }
        if (!(currentNode?.checkDone() ?: true)) {
            currentNode?.cancel()
        }
        if (currentIndex >= nodes.size) {
            done = true
            return
        }
        val (node, interval) = nodes[currentIndex++]
        currentNode = node
        node.onStart()
        currentInterval = interval
        timestarp = 0
    }

    override fun addPreTickAction(action: Animate.() -> Unit): Tickable<Animate> {
        return this
    }

    override fun tick() {
        if (!display || done) return
        if (currentNode?.checkDone() ?: true) {
            if (currentInterval < timestarp) {
                skip()
            }
        } else {
            currentNode!!.tick()
        }
        if (cancelPredicates.any { it.test(this) }) {
            cancel()
            return
        }
        timestarp++
    }

    fun start() {
        display = true
    }

    fun cancel() {
        done = true
        nodes.forEach {
            if (!it.first.checkDone()) {
                it.first.cancel()
            }
        }
    }

}