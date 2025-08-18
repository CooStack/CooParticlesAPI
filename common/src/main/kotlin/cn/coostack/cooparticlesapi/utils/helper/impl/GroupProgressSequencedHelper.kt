package cn.coostack.cooparticlesapi.utils.helper.impl

import cn.coostack.cooparticlesapi.particles.Controlable
import cn.coostack.cooparticlesapi.particles.control.group.SequencedParticleGroup
import cn.coostack.cooparticlesapi.utils.helper.ProgressSequencedHelper
import kotlin.math.*

class GroupProgressSequencedHelper(
    maxCount: Int,
    progressMaxTick: Int
) : ProgressSequencedHelper(maxCount, progressMaxTick) {
    private var linkedStyle: SequencedParticleGroup? = null
    override fun addMultiple(count: Int) {
        linkedStyle!!.addMultiple(count)
    }

    override fun removeMultiple(count: Int) {
        linkedStyle!!.removeMultiple(count)
    }

    override fun getLoadedStyle() = linkedStyle

    override fun changeStatusBatch(indexes: IntArray, status: Boolean) {
        indexes.forEach {
            linkedStyle!!.setSingleStatus(it, status)
        }
    }


    // 自定义进度同步方法
    fun syncProgressFromServer(current: Int) {
        this.current = current.coerceIn(0, progressMaxTick)
        val targetCount = (current.toDouble() / progressMaxTick * maxCount).roundToInt()
        linkedStyle!!.let {
            val currentActive = it.particleDisplayedCount
            when {
                targetCount > currentActive ->
                    it.addMultiple(targetCount - currentActive)

                targetCount < currentActive ->
                    it.removeMultiple(currentActive - targetCount)
            }
        }
    }

    override fun loadControler(controler: Controlable<*>) {
        controler as SequencedParticleGroup
        linkedStyle = controler
    }
}