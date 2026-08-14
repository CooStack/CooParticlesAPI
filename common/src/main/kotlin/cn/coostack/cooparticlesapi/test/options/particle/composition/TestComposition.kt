package cn.coostack.cooparticlesapi.test.options.particle.composition

import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
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
    var movement by dirty(RelativeLocation.yAxis())
    override fun getParticles(): Map<CompositionData, RelativeLocation> {
        return PointsBuilder()
            .addCircle(1.0, 100)
            .clearAsRoundXZMask(RelativeLocation(), 1.8)
            .addCircle(2.0, 120)
            .addPolygonInCircle(5, 10, 1.8)
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
