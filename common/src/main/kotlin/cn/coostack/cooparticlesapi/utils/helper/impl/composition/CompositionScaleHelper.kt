package cn.coostack.cooparticlesapi.utils.helper.impl.composition

import cn.coostack.cooparticlesapi.network.particle.composition.ParticleComposition
import cn.coostack.cooparticlesapi.particles.Controlable

import cn.coostack.cooparticlesapi.utils.helper.ScaleHelper


class CompositionScaleHelper(minScale: Double, maxScale: Double, scaleTick: Int) :
    ScaleHelper(minScale, maxScale, scaleTick) {
    var composition: ParticleComposition? = null
    override fun getLoadedGroup(): Controlable<*>? {
        return composition
    }

    override fun getGroupScale(): Double {
        return composition?.scale ?: 1.0
    }

    override fun scale(scale: Double) {
        composition?.scale(scale)
    }

    override fun loadControler(controler: Controlable<*>) {
        if (controler !is ParticleComposition) {
            return
        }
        composition = controler
        composition!!.scale(minScale)
    }
}