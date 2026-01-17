package cn.coostack.cooparticlesapi.test.options.particle.composition

import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.extend.asRelative
import cn.coostack.cooparticlesapi.extend.random
import cn.coostack.cooparticlesapi.network.particle.composition.AutoSequencedParticleComposition
import cn.coostack.cooparticlesapi.network.particle.composition.CompositionData
import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import cn.coostack.cooparticlesapi.network.particle.style.ParticleShapeStyle
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.SortedMap

@CooAutoRegister
class TestSeqComposition(position: Vec3, world: Level? = null) : AutoSequencedParticleComposition(position, world) {
    override fun getParticleSequenced(): SortedMap<CompositionData, RelativeLocation> {
        var o = 0
        return PointsBuilder()
            .addLightningAttenuationPoints(
                Vec3.ZERO.random().asRelative() * 100, 8, 18.0, 0.4, 100
            )
            .createWithCompositionDataSorted {
                CompositionData()
                    .apply {
                        order = o++
                    }
            }.apply {
                put(
                    CompositionData()
                        .setDisplayerSupplier {
                            ParticleDisplayer.withStyle(
                                ParticleShapeStyle(it)
                                    .appendBuilder(
                                        PointsBuilder()
                                            .addCircle(3.0, 160)
                                    ) {
                                        ParticleGroupStyle.StyleDataBuilder()
                                            .build()
                                    }
                            )
                        }, RelativeLocation()
                )
            }
    }

    override fun onDisplay() {
        addPreTickAction {
            addMultiple(1000)
        }
    }
}