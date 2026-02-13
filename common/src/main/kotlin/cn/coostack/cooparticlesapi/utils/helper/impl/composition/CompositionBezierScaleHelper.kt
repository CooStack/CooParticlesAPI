package cn.coostack.cooparticlesapi.utils.helper.impl.composition

import cn.coostack.cooparticlesapi.network.particle.composition.ParticleComposition
import cn.coostack.cooparticlesapi.api.controler.Controlable
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.helper.BezierValueScaleHelper

import cn.coostack.cooparticlesapi.utils.helper.ScaleHelper


class CompositionBezierScaleHelper(
    scaleTick: Int,
    minScale: Double,
    maxScale: Double,
    controlPoint1: RelativeLocation,
    controlPoint2: RelativeLocation
) :
    BezierValueScaleHelper(scaleTick, minScale, maxScale, controlPoint1, controlPoint2) {
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