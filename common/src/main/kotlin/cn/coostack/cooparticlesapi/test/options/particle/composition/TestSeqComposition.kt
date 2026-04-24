package cn.coostack.cooparticlesapi.test.options.particle.composition

import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.extend.asRelative
import cn.coostack.cooparticlesapi.extend.random
import cn.coostack.cooparticlesapi.network.particle.composition.AutoSequencedParticleComposition
import cn.coostack.cooparticlesapi.network.particle.composition.CompositionData
import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import cn.coostack.cooparticlesapi.network.particle.style.ParticleShapeStyle
import cn.coostack.cooparticlesapi.particles.CooParticleTextureSheet
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.FourierSeriesBuilder
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.SortedMap
import kotlin.math.PI

@CooAutoRegister
class TestSeqComposition(position: Vec3, world: Level? = null) : AutoSequencedParticleComposition(position, world) {
    override fun getParticleSequenced(): SortedMap<CompositionData, RelativeLocation> {
        var o = 0
        return PointsBuilder()
            .addFourierSeries(
                FourierSeriesBuilder()
                    .count(2048)
                    .addFourier(3.0, 2.0, 0.0)
                    .addFourier(2.0, -3.0, 0.0)
            )
            .addSpiral(0.0, 10.0, 15.0, 16384, PI / 64, 1.21, 0.2)
            .createWithCompositionDataSorted {
                CompositionData()
                    .apply {
                        order = o++
                        this.addParticleInstanceInit {
                            textureSheet = CooParticleTextureSheet.ADDITION_BLEND_TRANSLUCENT

                        }
                    }
            }
    }

    override fun onDisplay() {
        var flag = false
        addPreTickAction {
            if (displayedParticleCount !in 1..<count) {
                flag = !flag
            }
            if (flag) {
                addMultiple(100)
            } else {
                removeMultiple(100)
            }
        }
    }
}
