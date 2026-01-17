package cn.coostack.cooparticlesapi.test.options.particle.emitter

import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.emitter.handle.ParticleEmittersHelper
import cn.coostack.cooparticlesapi.extend.PIF
import cn.coostack.cooparticlesapi.extend.plus
import cn.coostack.cooparticlesapi.extend.times
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
import kotlin.random.Random

@CooAutoRegister
class TestPlusBlendEmitter(pos: Vec3, world: Level?) : ClassParticleEmitters(pos, world) {
    @CodecField
    var template = ControlableParticleData()
        .apply {
            setTextureSheet("ADDITION_BLEND")
        }

    @CodecField
    var shootMovement = Vec3.ZERO

    @CodecField
    var shootStep = 0.01

    val random = Random(System.currentTimeMillis())

    override fun doTick() {
    }

    override fun genParticles(lerpProgress: Float): List<Pair<ControlableParticleData, RelativeLocation>> {
        // 向上喷射
        val res = mutableListOf<Pair<ControlableParticleData, RelativeLocation>>()

        res.addAll(
            PointsBuilder()
                .addDiscreteCircleXZ(1.0, 120, 0.5)
                .rotateTo(shootMovement)
                .create()
                .map {
                    template.clone()
                        .apply {
                            this.velocity = it.toVector() + shootMovement * random.nextDouble(
                                0.1,
                                shootMovement.length().coerceAtLeast(0.2)
                            )
                            this.faceToCamera = false
                            this.yaw = random.nextFloat() * 2 * PIF
                            this.pitch = random.nextFloat() * 2 * PIF
                            this.roll = random.nextFloat() * 2 * PIF
                        } to it
                }
        )

        return res
    }

    override fun singleParticleAction(
        controler: ParticleControler,
        data: ControlableParticleData,
        spawnPos: RelativeLocation,
        spawnWorld: Level,
        particleLerpProgress: Float,
        posLerpProgress: Float
    ) {
        controler.addPreTickAction {
            updatePhysics(this.loc, data, this)
            currentYaw += PIF / 32
            currentRoll += PIF / 64
            currentPitch += PIF / 48
        }
    }

    override fun getEmittersID(): String {
        return "test-additional-emitters"
    }

    override fun getCodec(): StreamCodec<FriendlyByteBuf, ParticleEmitters> {
        return ParticleEmittersHelper.generateCodec(this)
    }

}