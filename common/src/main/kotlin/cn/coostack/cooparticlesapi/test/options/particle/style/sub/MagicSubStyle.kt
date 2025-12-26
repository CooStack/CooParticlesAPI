package cn.coostack.cooparticlesapi.test.options.particle.style.sub

import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffers
import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.particles.control.group.ControlableParticleGroup
import cn.coostack.cooparticlesapi.particles.impl.ControlableEndRodEffect
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import java.util.UUID
import kotlin.math.PI

class MagicSubStyle(uuid: UUID, val bindPlayer: UUID, var rotateSpeed: Int) : ParticleGroupStyle(32.0, uuid) {

    override fun onDisplay() {
        addPreTickAction {
            val player = world!!.getPlayerByUUID(bindPlayer) ?: return@addPreTickAction
            val playerPos = player.position()
            val to = playerPos.subtract(pos)
            rotateToWithAngle(RelativeLocation.of(to), PI / 72 * rotateSpeed)
        }
    }

    override fun getCurrentFrames(): Map<StyleData, RelativeLocation> {
        return PointsBuilder()
            .addCircle(4.0, 120)
            .pointsOnEach { it.y += 0.5 }
            .addCircle(3.5, 120)
            .addPolygonInCircle(3, 60, 2.0)
            .rotateAsAxis(PI / 3)
            .addPolygonInCircle(3, 60, 2.0)
            .addCircle(2.0, 120)
            .addWith {
                connectLines(
                    getCircleXZ(4.0, 16).onEach { it.y += 0.5 },
                    getCircleXZ(3.5, 16),
                    10
                ).flatten()
            }
            .addLine(
                RelativeLocation(), RelativeLocation.yAxis().multiplyClone(8.0), 40
            )
            .createWithStyleData {
                StyleData(
                    {
                        ParticleDisplayer.withSingle(
                            ControlableEndRodEffect(it)
                        )
                    }
                ).withParticleHandler {
                    colorOfRGB(255, 190, 212)
                    lifetime = 120
                }
            }
    }

    override fun writePacketArgs(): Map<String, ParticleControlerDataBuffer<*>> {
        return mapOf(
            "rotate_speed" to ParticleControlerDataBuffers.int(rotateSpeed),
        )
    }

    override fun readPacketArgs(args: Map<String, ParticleControlerDataBuffer<*>>) {
        rotateSpeed = args["rotate_speed"] as Int
    }
}