package cn.coostack.cooparticlesapi.animation.events

import cn.coostack.cooparticlesapi.animation.AnimateContext
import cn.coostack.cooparticlesapi.animation.animate.Animate


fun interface AnimateEvent {
    fun trigger(context: AnimateContext, animate: Animate)
}