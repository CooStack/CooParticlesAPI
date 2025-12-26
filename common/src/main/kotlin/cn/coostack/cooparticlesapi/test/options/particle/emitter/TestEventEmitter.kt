package cn.coostack.cooparticlesapi.test.options.particle.emitter

import cn.coostack.cooparticlesapi.annotations.emitter.EmitterAutoRegister
import cn.coostack.cooparticlesapi.annotations.emitter.EmitterField
import cn.coostack.cooparticlesapi.annotations.emitter.handle.ParticleEmittersHelper
import cn.coostack.cooparticlesapi.network.particle.emitters.ClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

@EmitterAutoRegister
class TestEventEmitter(pos: Vec3, world: Level?) : ClassParticleEmitters(pos, world) {
    @EmitterField
    var templateData = ControlableParticleData()

    @EmitterField
    var shootDirection: Vec3 = Vec3.ZERO

    companion object {
        const val ID = "test-event-particle-emitters"
    }

    override fun update(emitters: ParticleEmitters) {
        super.update(emitters)
    }

    override fun doTick() {
    }

    override fun genParticles(lerpProgress: Float): List<Pair<ControlableParticleData, RelativeLocation>> {
        return listOf(
            templateData.clone().apply {
                velocity = this@TestEventEmitter.shootDirection
            } to RelativeLocation()
        )
    }

    override fun singleParticleAction(
        controler: ParticleControler,
        data: ControlableParticleData,
        spawnPos: RelativeLocation,
        spawnWorld: Level,
        particleLerpProgress: Float,
        posLerpProgress: Float,
    ) {
        controler.addPreTickAction {
            updatePhysics(loc, data, this)
        }
    }

    override fun getEmittersID(): String {
        return ID
    }

    override fun getCodec(): StreamCodec<FriendlyByteBuf, ParticleEmitters> {
        return ParticleEmittersHelper.generateCodec(this)
    }
}