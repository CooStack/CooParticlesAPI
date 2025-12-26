package cn.coostack.cooparticlesapi.test.options.particle.emitter

import cn.coostack.cooparticlesapi.network.particle.emitters.ClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.test.options.particle.emitter.event.TestOnGroundEventHandler
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

class TestPhysicsFixEmitter(pos: Vec3, world: Level?) : ClassParticleEmitters(pos, world) {
    companion object {
        @JvmStatic
        val ID = "TestPhysicsFix"

        @JvmStatic
        val CODEC = StreamCodec.of<FriendlyByteBuf, ParticleEmitters>(
            { buf, emitter ->
                emitter as TestPhysicsFixEmitter
                encodeBase(emitter, buf)
            }, {
                TestPhysicsFixEmitter(Vec3.ZERO, null)
                    .apply {
                        decodeBase(this, it)
                    }
            }
        )
    }

    val clone = ControlableParticleData()

    init {
        addEventHandler(TestOnGroundEventHandler, true)
    }

    override fun doTick() {
    }

    override fun genParticles(lerpProgress: Float): List<Pair<ControlableParticleData, RelativeLocation>> {
        return listOf(clone.clone() to RelativeLocation())
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

    override fun getEmittersID(): String {
        return ID
    }

    override fun getCodec(): StreamCodec<FriendlyByteBuf, ParticleEmitters> {
        return CODEC
    }

}