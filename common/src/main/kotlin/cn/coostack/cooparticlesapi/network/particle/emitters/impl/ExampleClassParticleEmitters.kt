package cn.coostack.cooparticlesapi.network.particle.emitters.impl

import cn.coostack.cooparticlesapi.network.particle.emitters.ClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

class ExampleClassParticleEmitters(pos: Vec3, world: Level?) : ClassParticleEmitters(pos, world) {
    var moveDirection = Vec3.ZERO
    var templateData = ControlableParticleData()

    companion object {
        const val ID = "example-class-particle-emitters"

        @JvmStatic
        val CODEC = StreamCodec.of<FriendlyByteBuf, ParticleEmitters>(
            { buf, data ->
                data as ExampleClassParticleEmitters
                encodeBase(data, buf)
                buf.writeVec3(data.moveDirection)
                ControlableParticleData.PACKET_CODEC.encode(buf, data.templateData)
            }, {
                val instance = ExampleClassParticleEmitters(Vec3.ZERO, null)
                decodeBase(instance, it)
                instance.moveDirection = it.readVec3()
                instance.templateData = ControlableParticleData.PACKET_CODEC.decode(it)
                instance
            }
        )
    }

    override fun doTick() {
        pos = pos.add(moveDirection)
    }

    override fun genParticles(): List<Pair<ControlableParticleData, RelativeLocation>> {
        return PointsBuilder()
            .addBall(2.0, 20)
            .create().map {
                templateData.clone()
                    .apply {
                        this.velocity = it.normalize().multiplyClone(-0.1).toVector()
                    } to it
            }
    }

    override fun singleParticleAction(
        controler: ParticleControler,
        data: ControlableParticleData,
        spawnPos: RelativeLocation,
        spawnWorld: Level,
        currentProgress: Float
    ) {


    }

    override fun update(emitters: ParticleEmitters) {
        super.update(emitters)
        if (emitters !is ExampleClassParticleEmitters) {
            return
        }
        this.templateData = emitters.templateData
        this.moveDirection = emitters.moveDirection
    }

    override fun getEmittersID(): String {
        return ID
    }

    override fun getCodec(): StreamCodec<FriendlyByteBuf, ParticleEmitters> {
        return CODEC
    }
}