package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.test.api.TestOption

class TickingEmitterOption(
    val testEmitters: ParticleEmitters,
    var testingTick: Int = 100,
    val ticking: (ParticleEmitters) -> Boolean
) : TestOption {
    override fun paramTarget(): Any {
        return testEmitters
    }

    override fun start() {
        ParticleEmittersManager.spawnEmitters(testEmitters)
    }

    override fun stop() {
        testEmitters.canceled = true
    }

    override fun isValid(): Boolean {
        return !testEmitters.canceled && (testingTick > 0 || testingTick == -1)
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
        if (ticking(testEmitters)) {
            stop()
        }
    }
}
