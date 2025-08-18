package cn.coostack.cooparticlesapi.animation.animate

import cn.coostack.cooparticlesapi.animation.AnimateContext
import cn.coostack.cooparticlesapi.animation.events.AnimateEvent
import cn.coostack.cooparticlesapi.animation.events.AnimateEventType
import cn.coostack.cooparticlesapi.animation.events.FinishPredicate
import java.util.EnumMap
import java.util.LinkedList

class Animate internal constructor(internal var predicate: FinishPredicate) {
    val events = EnumMap<AnimateEventType, LinkedList<AnimateEvent>>(AnimateEventType::class.java)
    var time: Int = 0

    /**
     * 开始播放
     */
    var displayed = false
        internal set

    /**
     * 正常完成
     */
    var over = false
        internal set

    /**
     * 被中断
     */
    var canceled = false
        private set

    fun addEvent(type: AnimateEventType, event: AnimateEvent): Animate {
        events.getOrPut(type) { LinkedList<AnimateEvent>() }.add(event)
        return this
    }

    fun cancel(context: AnimateContext) {
        if (over || !displayed || canceled) return
        canceled = true
        time = 0
        onCancel(context)
    }


    fun display(context: AnimateContext) {
        if (over || canceled || displayed) return
        displayed = true
        time = 0
        onStart(context)
    }

    fun doTick(context: AnimateContext) {
        if (over || canceled || !displayed) return
        time++
        onTick(context)
    }

    fun over(context: AnimateContext) {
        onFinish(context)
        over = true
    }

    /**
     * 判断动画是否能够结束
     */
    fun isFinished(context: AnimateContext): Boolean = predicate.test(context, this)

    /**
     * 在上一个动画执行结束后, 就会执行这个方法
     */
    fun beforeStart(context: AnimateContext) {
        events[AnimateEventType.BEFORE]?.forEach {
            it.trigger(context, this)
        }
    }

    /**
     * 开始播放时执行
     */
    fun onStart(context: AnimateContext) {
        events[AnimateEventType.START]?.forEach {
            it.trigger(context, this)
        }
    }

    /**
     * tick执行一次
     * 负责动画的变化
     */
    fun onTick(context: AnimateContext) {
        events[AnimateEventType.TICK]?.forEach {
            it.trigger(context, this)
        }
    }

    /**
     * 完成时执行
     */
    fun onFinish(context: AnimateContext) {
        events[AnimateEventType.FINISH]?.forEach {
            it.trigger(context, this)
        }
    }

    /**
     * 被中断时执行
     */
    fun onCancel(context: AnimateContext) {
        events[AnimateEventType.CANCEL]?.forEach {
            it.trigger(context, this)
        }
    }

}