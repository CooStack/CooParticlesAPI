package cn.coostack.cooparticlesapi.test.options.particle.emitter

import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.extend.asRelative
import cn.coostack.cooparticlesapi.extend.plus
import cn.coostack.cooparticlesapi.extend.random
import cn.coostack.cooparticlesapi.extend.times
import cn.coostack.cooparticlesapi.network.buffer.Vec3dControlerBuffer
import cn.coostack.cooparticlesapi.network.particle.emitters.AutoParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.SimpleRandomParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.command.OrbitMode
import cn.coostack.cooparticlesapi.network.particle.emitters.command.ParticleAttractionCommand
import cn.coostack.cooparticlesapi.network.particle.emitters.command.ParticleCommandQueue
import cn.coostack.cooparticlesapi.network.particle.emitters.command.ParticleDragCommand
import cn.coostack.cooparticlesapi.network.particle.emitters.command.ParticleFlowFieldCommand
import cn.coostack.cooparticlesapi.network.particle.emitters.command.ParticleGravityCommand
import cn.coostack.cooparticlesapi.network.particle.emitters.command.ParticleNoiseCommand
import cn.coostack.cooparticlesapi.network.particle.emitters.command.ParticleOrbitCommand
import cn.coostack.cooparticlesapi.network.particle.emitters.command.ParticleRotationForceCommand
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.random.Random

@CooAutoRegister
class TestCommandEmitter(pos: Vec3, world: Level?) : AutoParticleEmitters(pos, world) {
    @CodecField
    var template = ControlableParticleData()

    @CodecField
    var ballRadius = 3.0

    @CodecField
    var ballOption = SimpleRandomParticleData()

    @CodecField
    var direction = Vec3(0.0, 1.0, 0.0)

    val command = ParticleCommandQueue()
        .add(
            ParticleNoiseCommand()
                .strength(0.05)
                .frequency(0.01)
                .speed(0.05)
                .clampSpeed(15.0)
        )
        .add(
            ParticleDragCommand()
                .damping(0.8)
                .linear(0.005)
                .minSpeed(0.01)
        )
//        .add(
//            ParticleOrbitCommand()
//                .center { this.pos }
//                .radius(16.0)
//                .angularSpeed(0.5)
//                .radialCorrect(0.3)
//                .minDistance(1.0)
//                .mode(OrbitMode.SPRING)
//        )
//        .add(
//            ParticleRotationForceCommand()
//                .center { this.pos }
//                .strength(0.6)
//        )
//        .add(
//            ParticleAttractionCommand()
//                .range(18.0)
//                .target {
//                    this.pos.add(0.0, 16.0, 0.0)
//                }
//                .strength(0.13)
//                .minDistance(0.2)
//                .falloffPower(2.5)
//        )
//        .add(
//            ParticleFlowFieldCommand()
//                .amplitude(0.1)
//                .frequency(0.3)
//                .timeScale(0.2)
//                .phaseOffset(1.0)
//        )
//        .add(
//            ParticleNoiseCommand()
//                .strength(0.2)
//                .frequency(0.1)
//                .speed(0.2)
//        )

    override fun doTick() {
    }

    override fun genParticles(lerpProgress: Float): List<Pair<ControlableParticleData, RelativeLocation>> {
        return PointsBuilder()
            .addPoint(RelativeLocation())
//            .addBall(ballRadius, ballOption.getRandomCount())
            .addLightningAttenuationPoints(
                (Vec3.ZERO.random() * Random.nextDouble(30.0, 60.0)).asRelative(),
                7,
                15.0,
                0.4,
                10
            )
            .create()
            .map {
                template.clone().apply {
                    maxAge = ballOption.getRandomParticleMaxAge()
                    size = ballOption.getRandomSize()
                    speed = ballOption.getRandomSpeed()
                } to it
            }
    }

    override fun singleParticleAction(
        controler: ParticleControler,
        data: ControlableParticleData,
        spawnPos: RelativeLocation,
        spawnWorld: Level,
        particleLerpProgress: Float,
        posLerpProgress: Float
    ) {
        val spawn = spawnPos.toVector()
        controler.addPreTickAction {
            command
                .updateWithTypes<ParticleRotationForceCommand> {
                    center { spawn }
                    axis(direction)
                }
                .applyVelocity(data, this)
        }
    }
}