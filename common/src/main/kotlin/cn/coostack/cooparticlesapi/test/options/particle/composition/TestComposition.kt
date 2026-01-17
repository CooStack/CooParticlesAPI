package cn.coostack.cooparticlesapi.test.options.particle.composition

import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.network.particle.composition.AutoParticleComposition
import cn.coostack.cooparticlesapi.network.particle.composition.CompositionData
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
            .addSpiral(3.0, 0.0, 5.0, 360, PI / 16, 0.5, 3.0)
            .addSpiral(3.0, 0.0, 5.0, 360, -PI / 16, 0.5, 3.0)
            .createWithCompositionData {
                CompositionData()
                    .addParticleInstanceInit {
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