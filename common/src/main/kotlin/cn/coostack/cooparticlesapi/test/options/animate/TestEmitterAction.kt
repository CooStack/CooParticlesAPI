package cn.coostack.cooparticlesapi.test.options.animate

import cn.coostack.cooparticlesapi.animation.AnimateAction
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager

class TestEmitterAction(val textEmitter: ParticleEmitters, val tickAction: TestEmitterAction.() -> Unit) :
    AnimateAction() {
    override fun checkDone(): Boolean {
        return textEmitter.canceled
    }

    override fun tick() {
        tickAction()
    }

    override fun onStart() {
        ParticleEmittersManager.spawnEmitters(textEmitter)
    }

    override fun onDone() {
        textEmitter.canceled = true
    }
}