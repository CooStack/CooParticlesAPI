package cn.coostack.cooparticlesapi.test.options.particle.composition

import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.network.particle.composition.*
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.particles.impl.*
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.random.Random
import java.util.SortedMap
import java.util.TreeMap
import org.joml.Vector3f

@CooAutoRegister
class GenNewComposition(position: Vec3, world: Level? = null) : AutoParticleComposition(position, world) {
    @CodecField
    var color: Vector3f = Vector3f(1f, 0.321569f, 0.321569f)

    @CodecField
    var tickCount: Int = 0

    init {
        axis = RelativeLocation.yAxis()
        setDisabledInterval(20)
    }

    override fun getParticles(): Map<CompositionData, RelativeLocation> {
        val result = LinkedHashMap<CompositionData, RelativeLocation>()

        run {
            val rel = RelativeLocation(0.0, 0.0, 0.0)
            result[
                CompositionData()
                    .setDisplayerSupplier {
                        ParticleDisplayer.withComposition(
                            ParticleShapeComposition(it).apply {
                                axis = RelativeLocation.yAxis()
                                loadScaleHelperBezierValue(
                                    0.01,
                                    1.0,
                                    20,
                                    RelativeLocation(6.653289, 1.0, 0.0),
                                    RelativeLocation(11.005575, 1.0, 0.0)
                                )
                                applyBuilder(
                                    PointsBuilder()
                                        .addCircle(10.0, 6)
                                ) { shapeRel0 ->
                                    CompositionData()
                                        .setDisplayerSupplier {
                                            ParticleDisplayer.withComposition(
                                                ParticleShapeComposition(it).apply {
                                                    axis = RelativeLocation.yAxis()
                                                    loadScaleHelperBezierValue(
                                                        0.01,
                                                        1.0,
                                                        18,
                                                        RelativeLocation(14.25552, 0.788725, 0.0),
                                                        RelativeLocation(13.035452, 1.0, 0.0)
                                                    )
                                                    applyBuilder(
                                                        PointsBuilder()
                                                            .addWith {
                                                                val res = arrayListOf<RelativeLocation>()
                                                                getPolygonInCircleVertices(6, 3.0)
                                                                    .forEach { it ->
                                                                        val p = PointsBuilder()
                                                                            .addFillTriangle(
                                                                                RelativeLocation(
                                                                                    0.0,
                                                                                    0.0,
                                                                                    2.0
                                                                                ),
                                                                                RelativeLocation(-1.0, 0.0, 1.0),
                                                                                RelativeLocation(1.0, 0.0, 1.0),
                                                                                5.0
                                                                            )
                                                                            .addFillTriangle(
                                                                                RelativeLocation(
                                                                                    0.0,
                                                                                    0.0,
                                                                                    -2.0
                                                                                ),
                                                                                RelativeLocation(-1.0, 0.0, -1.0),
                                                                                RelativeLocation(1.0, 0.0, -1.0),
                                                                                5.0
                                                                            )
                                                                            .axis(RelativeLocation(0.0, 0.0, 1.0))
                                                                        p.rotateTo(-it)
                                                                        res.addAll(
                                                                            p
                                                                                .pointsOnEach { rel -> rel.add(it) }
                                                                                .createWithoutClone()
                                                                        )
                                                                    }
                                                                res
                                                            }
                                                            .addBuilder(
                                                                RelativeLocation(0.0, 0.0, 0.0),
                                                                PointsBuilder()
                                                                    .addPolygonInCircle(6, 30, 5.0)
                                                                    .rotateAsAxis(
                                                                        0.166667 * PI,
                                                                        RelativeLocation(0.0, 1.0, 0.0)
                                                                    )
                                                            )
                                                            .addPolygonInCircle(6, 30, 5.0)
                                                            .addLine(
                                                                RelativeLocation(0.0, -7.0, 0.0),
                                                                RelativeLocation(0.0, 9.0, 0.0),
                                                                200
                                                            )
                                                    ) { shapeRel1 ->
                                                        CompositionData()
                                                            .setDisplayerSupplier {
                                                                ParticleDisplayer.withSingle(ControlableEndRodEffect(it))
                                                            }
                                                            .addParticleInstanceInit {
                                                                particleAlpha = 0.2F
                                                                size = 0.2F
                                                                color = this@GenNewComposition.color
                                                            }
                                                            .addParticleControlerInstanceInit {
                                                                addPreTickAction {
                                                                    particleAlpha = (tickCount / 20.0F).toFloat()
                                                                    if (this@GenNewComposition.status.displayStatus == 2) {
                                                                        color = this@GenNewComposition.color
                                                                    } else {
                                                                        color = Vector3f(1F, 0F, 1F)
                                                                    }
                                                                }
                                                            }
                                                    }
                                                    applyDisplayAction {
                                                        addPreTickAction {
                                                            rotateToWithAngle(shapeRel0, PI / 32)
                                                        }
                                                        setReversedScaleOnCompositionStatus(this@GenNewComposition)
                                                    }
                                                }
                                            )
                                        }
                                }
                                applyDisplayAction {
                                    addPreTickAction {
                                        rotateAsAxis(PI / 256)
                                    }
                                    setReversedScaleOnCompositionStatus(this@GenNewComposition)
                                }
                            }
                        )
                    }
            ] = rel
        }

        return result
    }

    override fun onDisplay() {
        addPreTickAction {
            if (tickCount++ > 600) {
                status.disable()
            }
        }
    }
}
