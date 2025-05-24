package cn.coostack.cooparticlesapi.test.particle.style

import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffers
import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleProvider
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.particles.impl.TestEndRodEffect
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class RotateTestStyle(val player: UUID, uuid: UUID = UUID.randomUUID()) :
    ParticleGroupStyle(128.0, uuid) {
    class Provider : ParticleStyleProvider {
        override fun createStyle(
            uuid: UUID,
            args: Map<String, ParticleControlerDataBuffer<*>>
        ): ParticleGroupStyle {
            return RotateTestStyle(args["player"]!!.loadedValue as UUID, uuid)
        }

    }

    override fun getCurrentFrames(): Map<StyleData, RelativeLocation> {
        return PointsBuilder()
            .addLine(
                RelativeLocation(0.0, -2.0, 0.0),
                RelativeLocation(0.0, 4.0, 0.0),
                20
            )
            .addLine(
                RelativeLocation(2.0, 3.0, 0.0),
                RelativeLocation(0.0, 4.0, 0.0),
                20
            )
            .addLine(
                RelativeLocation(-2.0, 3.0, 0.0),
                RelativeLocation(0.0, 4.0, 0.0),
                20
            )
            .addLine(
                RelativeLocation(1.0, 0.0, 0.0),
                RelativeLocation(-1.0, 0.0, 0.0),
                5
            ).addLine(
                RelativeLocation(0.0, 0.0, 2.0),
                RelativeLocation(0.0, 0.0, -2.0),
                5
            )
            .createWithStyleData {
                StyleData {
                    ParticleDisplayer.withSingle(
                        TestEndRodEffect(it)
                    )
                }
            }
    }

    override fun onDisplay() {
//        var x = -PI
//        var y = -PI
        addPreTickAction {
//            if (y >= PI) {
//                y = -PI
//                x += PI / 180
//            }
//            if (x >= PI) {
//                x = -PI
//            }
////            rotateParticlesToPoint(RelativeLocation(cos(x), sin(x), sin(x)))
//            y += PI / 180
//            Math3DUtil.rotatePointsToWithAngle(
//                particleLocations.values.toList(), PI/180, PI/180, axis
//            )
//            toggleRelative()
            val player = world!!.getPlayerByUuid(player)!!
            val loc = player.pos
            val relativize = pos.relativize(loc)
            rotateParticlesToPoint(
                RelativeLocation.of(player.rotationVector)
            )
        }
    }

    override fun rotateParticlesToPoint(to: RelativeLocation) {
        Math3DUtil.rotatePointsToWithAngle(
            particleLocations.values.toList(), to, axis
        )
        axis = to
        toggleRelative()
    }

    fun rotateParticlesToPoint(yaw: Double, pitch: Double) {
        Math3DUtil.rotatePointsToWithAngle(
            particleLocations.values.toList(), yaw, pitch, axis
        )
        Math3DUtil.rotatePointsToWithAngle(listOf(axis), yaw, pitch, axis)
        toggleRelative()
    }

    override fun writePacketArgs(): Map<String, ParticleControlerDataBuffer<*>> {
        return mapOf("player" to ParticleControlerDataBuffers.uuid(player))
    }

    override fun readPacketArgs(args: Map<String, ParticleControlerDataBuffer<*>>) {
    }
}