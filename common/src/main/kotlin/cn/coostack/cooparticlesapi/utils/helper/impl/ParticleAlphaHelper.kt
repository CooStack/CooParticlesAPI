package cn.coostack.cooparticlesapi.utils.helper.impl

import cn.coostack.cooparticlesapi.particles.Controlable
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.utils.helper.AlphaHelper

class ParticleAlphaHelper(minAlpha: Double, maxAlpha: Double, alphaTick: Int) :
    AlphaHelper(minAlpha, maxAlpha, alphaTick) {

    var controler: ParticleControler? = null
    override fun getLoadedGroup(): Controlable<*>? {
        return controler
    }

    override fun getCurrentAlpha(): Double {
        return controler?.particle?.particleAlpha?.toDouble() ?: 1.0
    }

    override fun setAlpha(alpha: Double) {
        controler!!.particle.particleAlpha = alpha.toFloat()
    }

    override fun loadControler(controler: Controlable<*>) {
        if (controler !is ParticleControler) {
            return
        }
        this.controler = controler
    }
}