package cn.coostack.cooparticlesapi.utils.helper.impl.composition

import cn.coostack.cooparticlesapi.network.particle.composition.ParticleComposition
import cn.coostack.cooparticlesapi.api.controler.Controlable
import cn.coostack.cooparticlesapi.utils.helper.StatusHelper

/**
 * 自动集成在 composition里
 *
 */
class CompositionStatusHelper : StatusHelper() {
    private var composition: ParticleComposition? = null
    private var init = false
    override fun changeStatus(status: Int) {
        composition?.markNetworkStateDirty()
    }

    fun updateCurrent(current: Int) {
        this.current = current
    }

    override fun initHelper() {
        if (composition == null) {
            return
        }
        if (init) {
            return
        }
        init = true
        composition!!.addPreTickAction {
            if (displayStatus != 2) {
                return@addPreTickAction
            }
            current++
            if (current >= closedInternal) {
                remove()
            }
        }
    }

    override fun loadControler(controler: Controlable<*>) {
        if (controler !is ParticleComposition) {
            return
        }
        composition = controler
    }
}
