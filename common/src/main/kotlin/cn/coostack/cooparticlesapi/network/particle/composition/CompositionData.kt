package cn.coostack.cooparticlesapi.network.particle.composition

import cn.coostack.cooparticlesapi.particles.ControlableParticle
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.particles.impl.ControlableEndRodEffect
import java.util.UUID

class CompositionData : Comparable<CompositionData> {
    var order = 0
    val uuid = UUID.randomUUID()
    val singleParticleHandlers = ArrayList<ControlableParticle.() -> Unit>()
    val particleControlerHandlers = ArrayList<ParticleControler.() -> Unit>()
    var displayerBuilder: (UUID) -> ParticleDisplayer = {
        ParticleDisplayer.withSingle(ControlableEndRodEffect(it))
    }
        private set

    fun setDisplayerSupplier(supplier: (UUID) -> ParticleDisplayer): CompositionData {
        displayerBuilder = supplier
        return this
    }

    fun addParticleInstanceInit(init: ControlableParticle.() -> Unit): CompositionData {
        singleParticleHandlers.add(init)
        return this
    }

    fun addParticleControlerInstanceInit(init: ParticleControler.() -> Unit): CompositionData {
        particleControlerHandlers.add(init)
        return this
    }

    override fun compareTo(other: CompositionData): Int {
        return order - other.order
    }
}