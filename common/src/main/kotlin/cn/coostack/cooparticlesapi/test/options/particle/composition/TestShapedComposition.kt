package cn.coostack.cooparticlesapi.test.options.particle.composition

import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.network.particle.composition.AutoParticleComposition
import cn.coostack.cooparticlesapi.network.particle.composition.CompositionData
import cn.coostack.cooparticlesapi.network.particle.composition.ParticleShapeComposition
import cn.coostack.cooparticlesapi.network.particle.composition.SequencedParticleShapeComposition
import cn.coostack.cooparticlesapi.particles.CooParticleTextureSheet
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.math.PI

@CooAutoRegister
class TestShapedComposition(position: Vec3, world: Level? = null) : AutoParticleComposition(position, world) {

    @CodecField
    var tick = 0

    override fun getParticles(): Map<CompositionData, RelativeLocation> {
        val res = mutableMapOf<CompositionData, RelativeLocation>()

        res[
            CompositionData()
                .setDisplayerSupplier {
                    ParticleDisplayer.withComposition(
                        ParticleShapeComposition()
                            .applyBuilder(
                                PointsBuilder()
                                    .addSpiral(0.0, 10.0, 10.0, 1800, PI / 32)
                            ) {
                                CompositionData()
                                    .addParticleInstanceInit {
                                        textureSheet = CooParticleTextureSheet.ADDITION_BLEND_TRANSLUCENT
                                    }
                            }.applyDisplayAction {
                                addPreTickAction {
                                    rotateAsAxis(-PI / 64)
                                }
                                setReversedScaleOnCompositionStatus(this@TestShapedComposition)
                            }.loadScaleHelper(0.01, 1.0, 10)
                    )
                }
        ] = RelativeLocation()
        res[
            CompositionData()
                .setDisplayerSupplier {
                    ParticleDisplayer.withComposition(
                        ParticleShapeComposition()
                            .applyBuilder(
                                PointsBuilder()
                                    .addSpiral(0.0, 10.0, 10.0, 1800, PI / 32)
                                    .rotateTo(-RelativeLocation.yAxis())
                            ) {
                                CompositionData().addParticleInstanceInit {
                                    textureSheet = CooParticleTextureSheet.ADDITION_BLEND_TRANSLUCENT
                                }
                            }
                            .applyDisplayAction {
                                addPreTickAction {
                                    rotateAsAxis(PI / 64)
                                }
                                setReversedScaleOnCompositionStatus(this@TestShapedComposition)
                            }.loadScaleHelper(0.01, 1.0, 10)


                    )
                }
        ] = RelativeLocation()

        res[
            CompositionData()
                .setDisplayerSupplier {
                    ParticleDisplayer.withComposition(
                        SequencedParticleShapeComposition()
                            .applyBuilderI(
                                PointsBuilder()
                                    .addCircle(10.0, 1080)
                            ) {
                                CompositionData().apply {
                                    this.order = it
                                }.addParticleInstanceInit {
                                    textureSheet = CooParticleTextureSheet.ADDITION_BLEND_TRANSLUCENT
                                }
                            }
                            .applyDisplayAction {
                                animate.addAnimate(30) {
                                    spawnAge > 10
                                }
                                addPreTickAction {
                                    if (spawnAge > 30) {
                                        addMultiple(10)
                                    }
                                    rotateAsAxis(PI / 16)
                                }
                            }
                    )
                }
        ] = RelativeLocation()

        return res
    }

    override fun onDisplay() {
        setDisabledInterval(20)
        addPreTickAction {
            if (tick++ > 200) {
                status.setStatus(2)
            }
        }
    }
}