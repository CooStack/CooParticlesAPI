package cn.coostack.cooparticlesapi.utils.helper

import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffers
import cn.coostack.cooparticlesapi.network.particle.style.SequencedParticleStyle
import java.util.function.Predicate

class SequencedAnimationHelper<T : SequencedParticleStyle> {
    private val animationConditions = ArrayList<Pair<Predicate<T>, Int>>()
    lateinit var style: T
    var animationIndex = 0
        private set

    /**
     * @param displayAnimatePredicate 执行该动画时必须要满足的条件
     * @param nextCount 当满足上面给予的条件时, 会生成的粒子/ 粒子组的个数 (addMultiple / removeMultiple)
     */
    fun addAnimate(displayAnimatePredicate: Predicate<T>, nextCount: Int): SequencedAnimationHelper<T> {
        animationConditions.add(displayAnimatePredicate to nextCount)
        return this
    }

    fun loadStyle(style: T): SequencedAnimationHelper<T> {
        this.style = style
        style.addPreTickAction {
            if (animationIndex >= animationConditions.size) {
                return@addPreTickAction
            }
            val (predicate, add) = animationConditions[animationIndex]
            if (predicate.test(style)) {
                if (add > 0) {
                    style.addMultiple(add)
                } else {
                    style.removeMultiple(add)
                }
                animationIndex++
            }
        }
        return this
    }

    fun getArgs(): Map<String, ParticleControlerDataBuffer<*>> = mapOf(
        "animation_index" to ParticleControlerDataBuffers.int(animationIndex)
    )

    fun setFromArgs(args: Map<String, ParticleControlerDataBuffer<*>>) {
        args["animation_index"]?.let {
            animationIndex = it.loadedValue as Int
        }
    }

}