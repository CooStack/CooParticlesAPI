package cn.coostack.cooparticlesapi.test.options.particle.emitter

import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.network.particle.emitters.AutoParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.particles.ControlableParticle
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.particles.control.RemoveReason
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import net.minecraft.world.level.Level
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

@CooAutoRegister
class TestRespawnEmitter(pos: Vec3, world: Level?) : AutoParticleEmitters(pos, world) {
    override fun doTick() {
    }

    override fun genParticles(lerpProgress: Float): List<Pair<ControlableParticleData, RelativeLocation>> {
        return listOf(
            ControlableParticleData().apply {
                velocity = Vec3(0.0, 0.0, 1.0)
                maxAge = 20
            } to RelativeLocation(),
        )
    }

    override fun singleParticleAction(
        controler: ParticleControler,
        data: ControlableParticleData,
        spawnPos: RelativeLocation,
        spawnWorld: Level,
        particleLerpProgress: Float,
        posLerpProgress: Float
    ) {
    }


    override fun moveSingleParticleWithVelocity(
        particle: ControlableParticle,
        data: ControlableParticleData,
        to: Vec3,
        collide: BlockHitResult
    ) {
        super.moveSingleParticleWithVelocity(particle, data, to, collide)
        if (collide.type != HitResult.Type.MISS) {
            particle.controler.remove(RemoveReason.CALL)
        }
    }

    override fun singleParticleDeathAction(
        oldControler: ParticleControler,
        oldData: ControlableParticleData,
        respawnCount: Int,
        reason: RemoveReason
    ): List<Pair<ControlableParticleData, RelativeLocation>> {
        if (respawnCount > 2) {
            return listOf()
        }
        println("粒子移除 原因: $reason")
        return PointsBuilder()
            .addBall(0.5, 4)
            .createWithoutClone()
            .map {
                oldData.clone().apply {
                    velocity = it.toVector()
                } to it
            }
    }
}