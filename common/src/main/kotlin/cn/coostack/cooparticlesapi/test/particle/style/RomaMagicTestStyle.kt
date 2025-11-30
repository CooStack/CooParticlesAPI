package cn.coostack.cooparticlesapi.test.particle.style

import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import cn.coostack.cooparticlesapi.network.particle.style.ParticleShapeStyle
import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleProvider
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.particles.impl.ControlableEndRodEffect
import cn.coostack.cooparticlesapi.utils.GraphMathHelper
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.PI

class RomaMagicTestStyle(uuid: UUID = UUID.randomUUID()) :
    ParticleGroupStyle(64.0, uuid) {
    class Provider : ParticleStyleProvider {
        override fun createStyle(
            uuid: UUID,
            args: Map<String, ParticleControlerDataBuffer<*>>
        ): ParticleGroupStyle {
            return RomaMagicTestStyle(uuid)
        }


    }

    override fun getCurrentFrames(): Map<StyleData, RelativeLocation> {
        val res = HashMap<StyleData, RelativeLocation>()
        res[StyleData {
            ParticleDisplayer.withStyle(
                ParticleShapeStyle(it)
                    .appendBuilder(
                        PointsBuilder()
                            .addWith {
                                val r = ArrayList<RelativeLocation>()
                                PointsBuilder().addCircle(3.5, 12)
                                    .create().forEachIndexed { index, rel ->
                                        r.addAll(
                                            PointsBuilder().withPreset { withRomaNumber(index + 1, 1.0) }
                                                .rotateAsAxis(PI)
                                                .rotateTo(rel)
                                                .pointsOnEach { p -> p.add(rel) }
                                                .create()
                                        )
                                    }
                                r
                            }
                    ) {
                        StyleData {
                            ParticleDisplayer.withSingle(ControlableEndRodEffect(it))
                        }.withParticleHandler {
                            this.size = 0.1f
                        }
                    }.toggleOnDisplay {
                        this.addPreTickAction {
                            rotateParticlesAsAxis(PI / 256)
                            rotateParticlesToPoint(this@RomaMagicTestStyle.axis)
                        }
                    }
            )
        }] = RelativeLocation(0.0, 0.01, 0.0)
        res[StyleData {
            ParticleDisplayer.withStyle(
                ParticleShapeStyle(it)
                    .appendBuilder(
                        PointsBuilder()
                            .addCircle(3.0, 360)
                            .addCircle(4.0, 360)
                    ) {
                        StyleData {
                            ParticleDisplayer.withSingle(ControlableEndRodEffect(it))
                        }
                    }.toggleOnDisplay {
                        this.addPreTickAction {
                            rotateParticlesAsAxis(-PI / 32)
                            rotateParticlesToPoint(this@RomaMagicTestStyle.axis)
                        }
                    }
            )
        }] = RelativeLocation(0.0, 0.01, 0.0)
        res[StyleData {
            ParticleDisplayer.withStyle(
                ParticleShapeStyle(it)
                    .appendBuilder(
                        PointsBuilder()
                            .addCircle(0.5, 120)
                            .pointsOnEach {
                                it.x += 1.5
                            }
                    ) {
                        StyleData {
                            ParticleDisplayer.withSingle(ControlableEndRodEffect(it))
                        }
                    }.appendBuilder(
                        PointsBuilder()
                            .addCircle(0.5, 120)
                            .pointsOnEach {
                                it.x -= 1.5
                            }
                    ) {
                        StyleData {
                            ParticleDisplayer.withSingle(ControlableEndRodEffect(it))
                        }.withParticleHandler {
                            colorOfRGB(0, 0, 0)
                        }
                    }.appendBuilder(
                        PointsBuilder()
                            .addHalfCircle(1.5, 120)
                            .pointsOnEach { p -> p.x += 3.0 }
                            .addHalfCircle(1.5, 120, PI)
                            .pointsOnEach { p -> p.x -= 1.5 }
                    ) {
                        StyleData {
                            ParticleDisplayer.withSingle(ControlableEndRodEffect(it))
                        }
                    }
                    .toggleOnDisplay {
                        this.addPreTickAction {
                            rotateParticlesAsAxis(PI / 64)
                            rotateParticlesToPoint(this@RomaMagicTestStyle.axis)
                        }
                    }
            )
        }] = RelativeLocation(0.0, 0.01, 0.0)

        res[StyleData {
            ParticleDisplayer.withStyle(
                ParticleShapeStyle(it)
                    .appendBuilder(
                        PointsBuilder()
                            .addPolygonInCircle(4, 120, 6.0)
                            .addPolygonInCircle(4, 120, 5.5)
                            .rotateAsAxis(PI / 4)
                            .addPolygonInCircle(4, 120, 5.5)
                            .addPolygonInCircle(4, 120, 6.0)
                    ) { rel ->
                        StyleData { it ->
                            ParticleDisplayer.withSingle(ControlableEndRodEffect(it))
                        }.withParticleHandler {
                            val pre = rel.length() / 6.0
                            color = with(GraphMathHelper) {
                                lerp(
                                    pre,
                                    Math3DUtil.colorOf(5, 100, 200),
                                    Math3DUtil.colorOf(255, 236, 247)
                                )
                            }
                            size = GraphMathHelper.lerp((rel.length() - 3) / 6.0, 0.1f, 1.5f)
                        }
                    }.toggleOnDisplay {
                        this.addPreTickAction {
                            rotateParticlesAsAxis(-PI / 256)
                            rotateParticlesToPoint(this@RomaMagicTestStyle.axis)
                        }
                    }
            )
        }] = RelativeLocation(0.0, 0.01, 0.0)

        return res
    }

    override fun onDisplay() {
    }

    override fun writePacketArgs(): Map<String, ParticleControlerDataBuffer<*>> {
        return mapOf()
    }

    override fun readPacketArgs(args: Map<String, ParticleControlerDataBuffer<*>>) {
    }
}