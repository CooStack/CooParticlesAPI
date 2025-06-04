package cn.coostack.cooparticlesapi.test.particle.emitter

import cn.coostack.cooparticlesapi.network.particle.emitters.ClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.ExplodeClassParticleEmitters
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.test.particle.emitter.event.TestEntityHitEventHandler
import cn.coostack.cooparticlesapi.test.particle.emitter.event.TestOnGroundEventHandler
import cn.coostack.cooparticlesapi.test.particle.emitter.event.TestOnLiquidEventHandler
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import kotlinx.serialization.PolymorphicSerializer
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

class TestEventEmitter(pos: Vec3d, world: World?) : ClassParticleEmitters(pos, world) {
    var templateData = ControlableParticleData()
    var shootDirection = Vec3d.ZERO

    companion object {
        const val ID = "test-event-particle-emitters"

        @JvmStatic
        val CODEC = PacketCodec.ofStatic<PacketByteBuf, ParticleEmitters>(
            { buf, data ->
                data as TestEventEmitter
                encodeBase(data, buf)
                ControlableParticleData.PACKET_CODEC.encode(buf, data.templateData)
                buf.writeVec3d(data.shootDirection)
            }, {
                val instance = TestEventEmitter(Vec3d.ZERO, null)
                decodeBase(instance, it)
                instance.templateData = ControlableParticleData.PACKET_CODEC.decode(it)
                instance.shootDirection = it.readVec3d()
                instance
            }
        )
    }


    override fun doTick() {
    }

    override fun genParticles(): Map<ControlableParticleData, RelativeLocation> {
        return mapOf(
            templateData.clone().apply {
                velocity = this@TestEventEmitter.shootDirection
            } to RelativeLocation()
        )
    }

    override fun singleParticleAction(
        controler: ParticleControler,
        data: ControlableParticleData,
        spawnPos: Vec3d,
        spawnWorld: World
    ) {
        controler.addPreTickAction {
            updatePhysics(pos, data)
        }
    }

    override fun getEmittersID(): String {
        return ID
    }

    override fun getCodec(): PacketCodec<PacketByteBuf, ParticleEmitters> {
        return CODEC
    }
}