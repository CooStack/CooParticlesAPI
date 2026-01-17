package cn.coostack.cooparticlesapi.animation

import java.util.function.Predicate

class AnimateNode {
    /**
     * 并行的
     */
    val animates = HashSet<AnimateAction>()

    val cancelPredicates = HashSet<Predicate<AnimateNode>>()

    var timestrap = 0

    fun onStart() {
        for (action in animates) {
            action.done = false
            action.onStart()
        }
    }

    fun addAction(action: AnimateAction): AnimateNode {
        animates.add(action)
        return this
    }

    /**
     * 如果条件符合 就cancel这个animate node
     *
     * @param predicate
     * @return
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
                it.onDone()
            }
        }
    }

}