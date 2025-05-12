package cn.coostack.cooparticlesapi.utils.helper.impl

import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import cn.coostack.cooparticlesapi.particles.Controlable
import cn.coostack.cooparticlesapi.particles.control.group.ControlableParticleGroup
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.helper.BezierValueScaleHelper

class GroupBezierValueScaleHelper(
    scaleTick: Int,
    minScale: Double,
    maxScale: Double,
    controlPoint1: RelativeLocation,
    controlPoint2: RelativeLocation
) :
    BezierValueScaleHelper(scaleTick, minScale, maxScale, controlPoint1, controlPoint2) {
    lateinit var group: ControlableParticleGroup
    override fun loadControler(controler: Controlable<*>) {
        if (controler !is ControlableParticleGroup) {
            return
        }
        group = controler
        group.scale(minScale)
    }

    override fun getLoadedGroup(): Controlable<*>? {
        if (!::group.isInitialized) {
            return null
        }
        return group
    }

    override fun getGroupScale(): Double {
        return group.scale
    }

    override fun scale(scale: Double) {
        group.scale(scale.coerceIn(minScale, maxScale))
    }
}