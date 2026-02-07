package cn.coostack.cooparticlesapi.test.options.particle.composition

import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.network.particle.composition.AutoParticleComposition
import cn.coostack.cooparticlesapi.network.particle.composition.CompositionData
import cn.coostack.cooparticlesapi.particles.CooParticleTextureSheet
import cn.coostack.cooparticlesapi.utils.NoiseMode
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.math.PI

@CooAutoRegister
class TestComposition(position: Vec3, world: Level? = null) : AutoParticleComposition(position, world) {
    @CodecField
    var movement = RelativeLocation.yAxis()
    override fun getParticles(): Map<CompositionData, RelativeLocation> {
        return PointsBuilder()
            .addWith {
                val res = arrayListOf<RelativeLocation>()
                getPolygonInCircleVertices(12, 12.0)
                    .forEach { it ->
                        val p = PointsBuilder()
                            .addLine(RelativeLocation(0.25, 0.0, -1.25), RelativeLocation(0.25, 0.0, -2.0), 30)
                            .addLine(RelativeLocation(-0.25, 0.0, -1.25), RelativeLocation(-0.25, 0.0, -2.0), 30)
                            .addLine(RelativeLocation(0.25, 0.0, -2.0), RelativeLocation(-0.25, 0.0, -2.0), 30)
                            .addLine(RelativeLocation(0.25, 0.0, -1.25), RelativeLocation(-0.25, 0.0, -1.25), 30)
                            .addLine(RelativeLocation(0.21, 0.0, -1.25), RelativeLocation(0.21, 0.0, 0.0), 30)
                            .addLine(RelativeLocation(-0.21, 0.0, -1.25), RelativeLocation(-0.21, 0.0, 0.0), 30)
                            .addLine(RelativeLocation(0.21, 0.0, 0.0), RelativeLocation(1.5, 0.0, 0.0), 30)
                            .addLine(RelativeLocation(-0.21, 0.0, 0.0), RelativeLocation(-1.5, 0.0, 0.0), 30)
                            .addLine(RelativeLocation(1.5, 0.0, 0.0), RelativeLocation(1.0, 0.0, 0.5), 30)
                            .addLine(RelativeLocation(-1.5, 0.0, 0.0), RelativeLocation(-1.0, 0.0, 0.5), 30)
                            .addLine(RelativeLocation(1.0, 0.0, 0.5), RelativeLocation(-1.0, 0.0, 0.5), 30)
                            .addLine(RelativeLocation(0.5, 0.0, 0.5), RelativeLocation(0.5, 0.0, 3.25), 30)
                            .addLine(RelativeLocation(-0.5, 0.0, 0.5), RelativeLocation(-0.5, 0.0, 3.25), 30)
                            .addLine(RelativeLocation(0.0, 0.0, 1.0), RelativeLocation(0.0, 0.0, 3.0), 30)
                            .addLine(RelativeLocation(0.5, 0.0, 3.25), RelativeLocation(0.0, 0.0, 4.0), 30)
                            .addLine(RelativeLocation(0.0, 0.0, 4.0), RelativeLocation(-0.5, 0.0, 3.25), 30)
                            .axis(RelativeLocation(0.0, 0.0, 1.0))
                        p.rotateTo(it.clone().add(0.0, 10.0, 0.0))
                        res.addAll(p
                            .pointsOnEach { rel -> rel.add(it) }
                            .createWithoutClone()
                        )
                    }
                res
            }
            .addCircle(12.0, 480)
            .createWithCompositionData {
                CompositionData()
                    .addParticleInstanceInit {
                        textureSheet = CooParticleTextureSheet.ADDITION_BLEND_TRANSLUCENT
                        colorOfRGBA(255, 128, 230, 1f)
                    }
            }
    }

    override fun onDisplay() {
        addPreTickAction {
            rotateToWithAngle(movement, PI / 32)
        }
    }

}