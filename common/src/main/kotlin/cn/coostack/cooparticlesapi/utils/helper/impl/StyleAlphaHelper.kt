package cn.coostack.cooparticlesapi.utils.helper.impl

import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import cn.coostack.cooparticlesapi.particles.Controlable
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.particles.control.group.ControlableParticleGroup
import cn.coostack.cooparticlesapi.utils.helper.AlphaHelper

class StyleAlphaHelper(minAlpha: Double, maxAlpha: Double, alphaTick: Int) :
    AlphaHelper(minAlpha, maxAlpha, alphaTick) {

    var style: ParticleGroupStyle? = null
    var currentAlpha = 1f
    override fun getLoadedGroup(): Controlable<*>? {
        return style
    }

    override fun getCurrentAlpha(): Double {
        return currentAlpha.toDouble()
    }

    override fun setAlpha(alpha: Double) {
        this.currentAlpha = alpha.toFloat()
        fun setControlableAlpha(controlable: Controlable<*>) {
            val value = controlable
            if (value is ParticleControler) {
                value.particle.particleAlpha = currentAlpha
            }
            if (value is ParticleGroupStyle) {
                value.particles.forEach {
                    setControlableAlpha(it.value)
                }
            }
            if (value is ControlableParticleGroup) {
                value.particles.forEach {
                    setControlableAlpha(it.value)
                }
            }
        }
        style!!.particles.forEach {
            setControlableAlpha(it.value)
        }
    }

    override fun loadControler(controler: Controlable<*>) {
        if (controler !is ParticleGroupStyle) {
            return
        }
        this.style = controler
        setAlpha(minAlpha)
    }
}