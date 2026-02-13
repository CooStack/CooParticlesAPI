package cn.coostack.cooparticlesapi.utils.helper.impl.composition

import cn.coostack.cooparticlesapi.network.particle.composition.ParticleComposition
import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import cn.coostack.cooparticlesapi.api.controler.Controlable
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.particles.control.group.ControlableParticleGroup
import cn.coostack.cooparticlesapi.utils.helper.AlphaHelper

class CompositionAlphaHelper(minAlpha: Double, maxAlpha: Double, alphaTick: Int) :
    AlphaHelper(minAlpha, maxAlpha, alphaTick) {
    private var composition: ParticleComposition? = null
    private var alpha = 1.0
    override fun getLoadedGroup(): Controlable<*>? {
        return composition
    }


    override fun getCurrentAlpha(): Double {
        return alpha
    }

    override fun setAlpha(alpha: Double) {
        this@CompositionAlphaHelper.alpha = alpha
        composition?.let {
            val queue = ArrayDeque<Controlable<*>>()
            queue.add(it)
            while (queue.isNotEmpty()) {
                val controlable = queue.removeFirst()
                val particles = ArrayList<Controlable<*>>()
                if (controlable is ParticleComposition) {
                    particles.addAll(controlable.particles.values)
                }
                if (controlable is ParticleGroupStyle) {
                    particles.addAll(controlable.particles.values)
                }
                if (controlable is ControlableParticleGroup) {
                    particles.addAll(controlable.particles.values)
                }
                setParticlesAlpha(particles, this@CompositionAlphaHelper.alpha, queue)
            }
        }
    }

    override fun loadControler(controler: Controlable<*>) {
        if (controler !is ParticleComposition) {
            return
        }
        composition = controler
    }

    private fun setParticlesAlpha(
        particles: Collection<Controlable<*>>,
        alpha: Double,
        deque: ArrayDeque<Controlable<*>>
    ) {
        particles.forEach {
            if (it is ParticleControler) {
                it.particle.particleAlpha = alpha.toFloat()
            } else {
                deque.add(it)
            }
        }
    }

}