package cn.coostack.cooparticlesapi.utils.helper.impl

import cn.coostack.cooparticlesapi.network.particle.style.SequencedParticleStyle
import cn.coostack.cooparticlesapi.particles.Controlable
import cn.coostack.cooparticlesapi.utils.helper.ProgressSequencedHelper
import kotlin.math.*

class StyleProgressSequencedHelper(
    maxCount: Int,
    progressMaxTick: Int
) : ProgressSequencedHelper(maxCount, progressMaxTick) {
    private var linkedStyle: SequencedParticleStyle? = null
    override fun addMultiple(count: Int) {
        linkedStyle!!.addMultiple(count)
    }

    override fun removeMultiple(count: Int) {
        linkedStyle!!.removeMultiple(count)
    }

    override fun getLoadedStyle() = linkedStyle

    override fun changeStatusBatch(indexes: IntArray, status: Boolean) {
        linkedStyle!!.changeParticlesStatus(indexes, status)
    }


    // 自定义进度同步方法
    fun syncProgressFromServer(current: Int) {
        this.current = current.coerceIn(0, progressMaxTick)
        val targetCount = (current.toDouble() / progressMaxTick * maxCount).roundToInt()
        linkedStyle!!.let {
            val currentActive = it.displayedParticleCount
            when {
                targetCount > currentActive ->
                    it.addMultiple(targetCount - currentActive)

                targetCount < currentActive ->
                    it.removeMultiple(currentActive - targetCount)
            }
        }
    }

    override fun loadControler(controler: Controlable<*>) {
        if (controler !is SequencedParticleStyle) {
            return
        }
        linkedStyle = controler
    }
}