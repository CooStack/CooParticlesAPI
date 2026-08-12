package cn.coostack.cooparticlesapi.test.options.particle.emitter

import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForce
import cn.coostack.cooparticlesapi.network.particle.emitters.AutoTransformableCParticleEmitter
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableCParticleData
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.math.PI

@CooAutoRegister
class TestTransformGPUEmitter(pos: Vec3, world: Level?) : AutoTransformableCParticleEmitter(pos, world) {
    override fun doTick() {
        rotateEmitter(PI / 64)
    }

    override fun cparticleForces(): List<CParticleForce> {
        return listOf(
            CParticleForce.ExpDrag(0.05, 0.0, 0.0)
        )
    }

    override fun genParticles(lerpProgress: Float): List<Pair<ControlableCParticleData, RelativeLocation>> {
        return PointsBuilder()
            .addBall(0.1, 5)
            .createWithoutClone()
            .map {
                ControlableCParticleData().apply {
                    velocity = it.normalize().toVector()
                } to it
            }
    }
}
