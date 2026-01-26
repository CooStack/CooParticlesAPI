package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.test.api.TestOption

class SimpleEmitterOption(val testEmitters: ParticleEmitters, var testingTick: Int = 100) : TestOption {
    var ticking: SimpleEmitterOption.() -> Unit = {}
    override fun start() {
        ParticleEmittersManager.spawnEmitters(testEmitters)
    }

    override fun stop() {
        testEmitters.cancelled = true
    }

    override fun isValid(): Boolean {
        return !testEmitters.cancelled && (testingTick > 0 || testingTick == -1)
    }

    override fun onFailed() {
    }

    override fun onSuccess() {
    }

    override fun optionID(): String {
        return "emitter: ${testEmitters.getEmittersID()}"
    }

    override fun doTick() {
        if (testingTick != -1) testingTick--
        ticking()
    }
}