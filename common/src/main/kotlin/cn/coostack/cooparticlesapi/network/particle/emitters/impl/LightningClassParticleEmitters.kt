package cn.coostack.cooparticlesapi.network.particle.emitters.impl

import cn.coostack.cooparticlesapi.network.particle.emitters.ClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.PhysicConstant
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import cn.coostack.cooparticlesapi.utils.helper.emitters.LinearResistanceHelper
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.random.Random

class LightningClassParticleEmitters(pos: Vec3, world: Level?) : ClassParticleEmitters(pos, world) {
    var templateData = ControlableParticleData()

    companion object {
        const val ID = "lightning-class-particle-emitters"

        @JvmStatic
        val CODEC = StreamCodec.of<FriendlyByteBuf, ParticleEmitters>(
            { buf, data ->
                data as LightningClassParticleEmitters
                encodeBase(data, buf)
                ControlableParticleData.PACKET_CODEC.encode(buf, data.templateData)
            }, {
                val instance = LightningClassParticleEmitters(Vec3.ZERO, null)
                decodeBase(instance, it)
                instance.templateData = ControlableParticleData.PACKET_CODEC.decode(it)
                instance
            }
        )
    }

    override fun doTick() {
    }

    val random = Random(System.currentTimeMillis())
    override fun genParticles(): List<Pair<ControlableParticleData, RelativeLocation>> {
        return Math3DUtil.getLightningEffectPoints(
            RelativeLocation(
                random.nextDouble(-50.0, 50.0),
                random.nextDouble(-10.0, 10.0),
                random.nextDouble(-50.0, 50.0),
            ), 10,3
        ).map { templateData.clone() to it }
    }

    override fun singleParticleAction(
        controler: ParticleControler,
        data: ControlableParticleData,
        spawnPos: RelativeLocation,
        spawnWorld: Level,
        currentProgress: Float
    ) {
    }


    override fun getEmittersID(): String {
        return ID
    }

    override fun getCodec(): StreamCodec<FriendlyByteBuf, ParticleEmitters> {
        return CODEC
    }
}