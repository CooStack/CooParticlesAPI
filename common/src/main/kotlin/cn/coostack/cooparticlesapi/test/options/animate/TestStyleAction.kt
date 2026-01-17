package cn.coostack.cooparticlesapi.test.options.animate

import cn.coostack.cooparticlesapi.animation.AnimateAction
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleManager
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

class TestStyleAction(
    val style: ParticleGroupStyle,
    val spawnWorld: Level,
    val spawnPos: Vec3,
    val tickAction: TestStyleAction.() -> Unit
) :
    AnimateAction() {
    override fun checkDone(): Boolean {
        return !style.valid
    }

    override fun tick() {
        tickAction()
    }

    override fun onStart() {
        ParticleStyleManager.spawnStyle(spawnWorld, spawnPos, style)
    }

    override fun onDone() {
        style.remove()
    }
}