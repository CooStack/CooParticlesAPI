package cn.coostack.cooparticlesapi.animation.events

import cn.coostack.cooparticlesapi.animation.AnimateContext
import cn.coostack.cooparticlesapi.animation.animate.Animate


fun interface FinishPredicate {
    /**
     * 测试这个动画是否完成播放
     */
    fun test(context: AnimateContext, animate: Animate): Boolean
}