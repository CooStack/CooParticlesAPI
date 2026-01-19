package cn.coostack.cooparticlesapi.utils.helper

import cn.coostack.cooparticlesapi.network.particle.composition.SequencedParticleComposition
import java.util.function.Predicate

class SequencedCompositionAnimationHelper<T : SequencedParticleComposition> {
    private val animationConditions = ArrayList<Pair<Predicate<T>, Int>>()
    lateinit var composition: T
    var animationIndex = 0
        private set
    var clientOnly = false

    /**
     * @param displayAnimatePredicate 执行该动画时必须要满足的条件
     * @param nextCount 当满足上面给予的条件时, 会生成的粒子/ 粒子组的个数 (addMultiple / removeMultiple)
     */
    fun addAnimate(nextCount: Int, displayAnimatePredicate: Predicate<T>): SequencedCompositionAnimationHelper<T> {
        animationConditions.add(displayAnimatePredicate to nextCount)
        return this
    }

    /**
     * 只在客户端执行此方法 适用于某些只在客户端生成的style
     */
    fun clientOnly(): SequencedCompositionAnimationHelper<T> {
        clientOnly = true
        return this
    }

    fun loadComposition(composition: T): SequencedCompositionAnimationHelper<T> {
        this.composition = composition
        composition.addPreTickAction {
            val clientDisable = composition.client && !clientOnly
            val serverDisable = clientOnly && !composition.client
            if (clientDisable || serverDisable) return@addPreTickAction
            if (animationIndex >= animationConditions.size) {
                return@addPreTickAction
            }
            val (predicate, add) = animationConditions[animationIndex]
            if (predicate.test(composition)) {
                if (add > 0) {
                    composition.addMultiple(add)
                } else {
                    composition.removeMultiple(add)
                }
                animationIndex++
            }
        }
        return this
    }
}