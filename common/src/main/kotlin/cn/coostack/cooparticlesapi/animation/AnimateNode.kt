package cn.coostack.cooparticlesapi.animation

class AnimateNode {
    /**
     * 并行的
     */
    val animates = HashSet<AnimateAction>()

    var timestrap = 0

    fun onStart() {
        for (action in animates) {
            action.done = false
            action.onStart()
        }
    }


    fun tick() {
        animates.forEach {
            if (it.check() || it.timeStart < timestrap) {
                return@forEach
            }
            it.doTick()
            if (it.check()) {
                it.onDone()
            }
        }
        timestrap++
    }

    fun checkDone(): Boolean {
        return animates.all { it.check() }
    }

}