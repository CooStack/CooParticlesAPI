package cn.coostack.cooparticlesapi.test.options.particle.composition

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.network.particle.composition.AutoParticleComposition
import cn.coostack.cooparticlesapi.network.particle.composition.CompositionData
import cn.coostack.cooparticlesapi.utils.FourierPhotoUtil
import cn.coostack.cooparticlesapi.utils.ImageUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.FourierSeriesBuilder
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

@CooAutoRegister
class TestFourierPhotoComposition(position: Vec3, world: Level? = null) : AutoParticleComposition(position, world) {
    companion object {
        val image = ImageUtil.loadFromIdentifier(
            ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "test/testing.png")
        )
        val builders = HashMap<RelativeLocation, FourierSeriesBuilder>()
        val points = PointsBuilder()

        init {
            image?.let {
                builders.putAll(
                    FourierPhotoUtil.toFourierBuildersWithOffset(
                        image = it,
                        alphaThreshold = 5,
                        sampleCount = 2048,
                        harmonics = 400,
                        step = 0.05,
                        sortByAmplitude = true,
                        maxComponents = 16,
                        minComponentPixels = 10
                    )
                )
                builders.forEach { it ->
                    points.addBuilder(
                        it.key, PointsBuilder().addFourierSeries(it.value)
                    )
                }
            }
        }

    }

    override fun getParticles(): Map<CompositionData, RelativeLocation> {
        return points
            .createWithCompositionData {
                CompositionData()
            }
    }

    override fun onDisplay() {
    }
}