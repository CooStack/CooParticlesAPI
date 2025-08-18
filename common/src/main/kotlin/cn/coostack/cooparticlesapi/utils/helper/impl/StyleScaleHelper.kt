package cn.coostack.cooparticlesapi.utils.helper.impl

import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import cn.coostack.cooparticlesapi.particles.Controlable
import cn.coostack.cooparticlesapi.utils.helper.ScaleHelper

class StyleScaleHelper(minScale: Double, maxScale: Double, scaleTick: Int) :
    ScaleHelper(minScale, maxScale, scaleTick) {
    lateinit var group: ParticleGroupStyle
    override fun loadControler(controler: Controlable<*>) {
        controler as ParticleGroupStyle
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