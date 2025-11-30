package cn.coostack.cooparticlesapi.test.particle.emitter

import cn.coostack.cooparticlesapi.network.particle.emitters.ClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

class TestEventEmitter(pos: Vec3, world: Level?) : ClassParticleEmitters(pos, world) {
    var templateData = ControlableParticleData()
    var shootDirection: Vec3 = Vec3.ZERO

    companion object {
        const val ID = "test-event-particle-emitters"

        @JvmStatic
        val CODEC = StreamCodec.of<FriendlyByteBuf, ParticleEmitters>(
            { buf, data ->
                data as TestEventEmitter
                encodeBase(data, buf)
                ControlableParticleData.PACKET_CODEC.encode(buf, data.templateData)
                buf.writeVec3(data.shootDirection)
            }, {
                val instance = TestEventEmitter(Vec3.ZERO, null)
                decodeBase(instance, it)
                instance.templateData = ControlableParticleData.PACKET_CODEC.decode(it)
                instance.shootDirection = it.readVec3()
                instance
            }
        )
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
        return CODEC
    }
}