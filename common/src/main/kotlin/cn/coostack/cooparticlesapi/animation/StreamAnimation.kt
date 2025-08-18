package cn.coostack.cooparticlesapi.animation

import cn.coostack.cooparticlesapi.animation.animate.Animate
import java.util.LinkedList

class StreamAnimation {
    var time = 0
    var displayed = false
    var canceled = false
        private set
    val context = AnimateContext(this)
    val animateQueue = LinkedList<Animate>()
    var currentAnimate: Animate? = null
    var displayDelay = 0

    fun addAnimate(animate: Animate): StreamAnimation {
        animateQueue.addLast(animate)
        return this
    }

    fun next(): StreamAnimation {
        if (!hasNext()) {
            return this
        }
        if (currentAnimate != null) {
            // 判断是否是正常结束
            val normalOver = currentAnimate!!.over
            if (normalOver && currentAnimate!!.displayed && !currentAnimate!!.canceled) {
                currentAnimate!!.cancel(context)
            }
        }
        currentAnimate = animateQueue.poll()
        currentAnimate!!.beforeStart(context)
        return this
    }

    fun hasNext(): Boolean = animateQueue.isNotEmpty()

    fun cancel(): StreamAnimation {
        if (!displayed) return this
        if (canceled) return this
        canceled = true

        val current = currentAnimate ?: return this
        if (!current.displayed || current.over) {
            return this
        }
        current.cancel(context)
        return this
    }

    fun display(): StreamAnimation {
        if (displayed) return this
        displayed = true
        displayDelay = 0
        return this
    }

    fun tick() {
        if (!displayed) return
        if (canceled) return
        time++
        if (hasNext() && currentAnimate == null) {
            next()
        }
        val currentAnimate = currentAnimate ?: return
        if (displayDelay-- <= 0 && !currentAnimate.displayed) {
            currentAnimate.display(context)
        }
        if (currentAnimate.displayed && !currentAnimate.over && !currentAnimate.canceled) {
            currentAnimate.onTick(context)
        }

        if (currentAnimate.isFinished(context)) {
            currentAnimate.over(context)
            this.currentAnimate = null
            next()
        }

    }

}