package cn.coostack.cooparticlesapi.animation.animate

import cn.coostack.cooparticlesapi.animation.events.FinishPredicate
import java.util.function.Consumer

class AnimateBuilder {
    private var predicate: FinishPredicate = FinishPredicate { _, _ -> true }
    private val animate = Animate(predicate)

    /**
     * 对java的适配
     * 使用Consumer
     */
    fun scopeConsumer(function: Consumer<Animate>): AnimateBuilder {
        function.accept(animate)
        return this
    }

    /**
     * 使用kotlin receiver
     * 直接使用this作为作用域
     * 方便设置Animate的变量
     */
    fun scope(function: Animate.() -> Unit): AnimateBuilder {
        /**
         * 方便设置其他参数
         */
        function(animate)
        return this
    }

    fun predicate(finishPredicate: FinishPredicate): AnimateBuilder {
        predicate = finishPredicate
        return this
    }

    fun build(): Animate {
        animate.predicate = predicate
        return animate
    }
}