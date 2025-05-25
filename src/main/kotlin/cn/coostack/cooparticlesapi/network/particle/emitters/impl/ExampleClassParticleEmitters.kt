package cn.coostack.cooparticlesapi.network.particle.emitters.impl

import cn.coostack.cooparticlesapi.network.particle.emitters.ClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

class ExampleClassParticleEmitters(pos: Vec3d, world: World?) : ClassParticleEmitters(pos, world) {
    var moveDirection = Vec3d.ZERO
    var templateData = ControlableParticleData()

    companion object {
        const val ID = "example-class-particle-emitters"

        @JvmStatic
        val CODEC = PacketCodec.ofStatic<RegistryByteBuf, ParticleEmitters>(
            { buf, data ->
                data as ExampleClassParticleEmitters
                encodeBase(data, buf)
                buf.writeVec3d(data.moveDirection)
                ControlableParticleData.PACKET_CODEC.encode(buf, data.templateData)
            }, {
                val instance = ExampleClassParticleEmitters(Vec3d.ZERO, null)
                decodeBase(instance, it)
                instance.moveDirection = it.readVec3d()
                instance.templateData = ControlableParticleData.PACKET_CODEC.decode(it)
                instance
            }
        )
    }

    override fun doTick() {
        pos = pos.add(moveDirection)
    }

    override fun genParticles(): Map<ControlableParticleData, RelativeLocation> {
        return PointsBuilder()
            .addBall(2.0, 20)
            .create().associateBy {
                templateData.clone()
                    .apply {
                        this.velocity = it.normalize().multiplyClone(-0.1).toVector()
                    }
            }
    }

    override fun singleParticleAction(
        controler: ParticleControler,
        data: ControlableParticleData,
        spawnPos: Vec3d,
        spawnWorld: World
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

    override fun getCodec(): PacketCodec<RegistryByteBuf, ParticleEmitters> {
        return CODEC
    }
}