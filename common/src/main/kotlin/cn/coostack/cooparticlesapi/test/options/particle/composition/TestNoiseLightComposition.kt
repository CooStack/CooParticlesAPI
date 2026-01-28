package cn.coostack.cooparticlesapi.test.options.particle.composition

import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.extend.asRelative
import cn.coostack.cooparticlesapi.extend.minus
import cn.coostack.cooparticlesapi.network.particle.composition.AutoParticleComposition
import cn.coostack.cooparticlesapi.network.particle.composition.CompositionData
import cn.coostack.cooparticlesapi.particles.CooParticleTextureSheet
import cn.coostack.cooparticlesapi.utils.NoiseMode
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

@CooAutoRegister
class TestNoiseLightComposition(position: Vec3, world: Level? = null) : AutoParticleComposition(position, world) {
    @CodecField
    var end = Vec3.ZERO

    @CodecField
    var nodeCount = 8
    override fun getParticles(): Map<CompositionData, RelativeLocation> {
        val relEnd = end - position
        return PointsBuilder()
            .addWith {
                connectLineWithNodes(
                    PointsBuilder()
                        .addLine(Vec3.ZERO, relEnd, nodeCount)
                        .applyNoiseOffset(Vec3(8.0, 4.0, 4.0), NoiseMode.SPHERE_UNIFORM, 1145)
                        .addPoint(relEnd.asRelative())
                        .createWithoutClone(), 40
                )
            }
            .createWithCompositionDataWithoutClone {
                CompositionData()
                    .addParticleInstanceInit {
                        textureSheet = CooParticleTextureSheet.ADDITION_BLEND_TRANSLUCENT
                    }
            }
    }

    override fun onDisplay() {
    }
}