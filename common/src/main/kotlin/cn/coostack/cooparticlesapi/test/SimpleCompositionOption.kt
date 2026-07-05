package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.network.particle.composition.ParticleComposition
import cn.coostack.cooparticlesapi.network.particle.composition.manager.ParticleCompositionManager
import cn.coostack.cooparticlesapi.test.api.TestOption

class SimpleCompositionOption(val composition: ParticleComposition, var testingTick: Int = 100) : TestOption {
    override fun paramTarget(): Any {
        return composition
    }

    override fun start() {
        ParticleCompositionManager.spawn(composition)
    }

    override fun stop() {
        composition.remove()
    }

    override fun isValid(): Boolean {
        return !composition.canceled && (testingTick > 0 || testingTick == -1)
    }

    override fun onFailed() {
    }

    override fun onSuccess() {
    }

    override fun optionID(): String {
        return "composition: ${composition.controlUUID()} : ${composition::class.java.name}"
    }

    override fun doTick() {
        if (testingTick != -1) testingTick--
    }
}
