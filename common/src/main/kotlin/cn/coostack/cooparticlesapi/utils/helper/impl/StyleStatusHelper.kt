package cn.coostack.cooparticlesapi.utils.helper.impl

import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import cn.coostack.cooparticlesapi.particles.Controlable
import cn.coostack.cooparticlesapi.utils.helper.StatusHelper

class StyleStatusHelper : StatusHelper() {
    lateinit var group: ParticleGroupStyle
    private var init = false
    override fun changeStatus(status: Int) {
        // 客户端无需同步
        if (group.client) {
            return
        }
        group.change(toArgsPairs().toMap())
    }


    override fun initHelper() {
        if (!::group.isInitialized) {
            return
        }
        if (init) {
            return
        }
        init = true
        group.addPreTickAction {
            if (displayStatus != 2) {
                return@addPreTickAction
            }
            current++
            if (current >= closedInternal) {
                group.remove()
            }
        }
    }

    override fun loadControler(controler: Controlable<*>) {
        if (controler !is ParticleGroupStyle) return
        group = controler
        initHelper()
    }
}