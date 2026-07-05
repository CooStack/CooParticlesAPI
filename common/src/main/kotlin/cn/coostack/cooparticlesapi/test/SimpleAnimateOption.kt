package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.animation.Animate
import cn.coostack.cooparticlesapi.animation.AnimateManager
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.test.api.TestOption

class SimpleAnimateOption(val animate: Animate, var testingTick: Int = 100) : TestOption {
    override fun paramTarget(): Any {
        return animate
    }

    override fun start() {
        AnimateManager.displayAnimateServer(animate)
    }

    override fun stop() {
        animate.cancel()
    }

    override fun isValid(): Boolean {
        return !animate.done && (testingTick > 0 || testingTick == -1)
    }

    override fun onFailed() {
    }

    override fun onSuccess() {
    }

    override fun optionID(): String {
        return "animate"
    }

    override fun doTick() {
        if (testingTick != -1) testingTick--
    }
}
