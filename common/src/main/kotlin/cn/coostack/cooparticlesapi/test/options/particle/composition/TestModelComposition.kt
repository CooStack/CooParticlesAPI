package cn.coostack.cooparticlesapi.test.options.particle.composition

import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.extend.asRelative
import cn.coostack.cooparticlesapi.network.particle.composition.AutoParticleComposition
import cn.coostack.cooparticlesapi.network.particle.composition.CompositionData
import cn.coostack.cooparticlesapi.platform.CooClientServices
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

@CooAutoRegister
class TestModelComposition(position: Vec3, world: Level? = null) : AutoParticleComposition(position, world) {
    override fun getParticles(): Map<CompositionData, RelativeLocation> {
        val model = Minecraft.getInstance().entityModels.bakeLayer(ModelLayers.WOLF)
        val points = CooClientServices.MODEL_PART_POINT_COLLECTOR
            .collectSamplePoints(
                model, PoseStack()
                    .apply {
                        scale(3f, 3f, 3f)
                        mulPose(Axis.ZP.rotationDegrees(180f))
                    }, 16
            )
        return points.map {
            it.asRelative()
        }.associateBy {
            CompositionData()
        }
    }

    override fun onDisplay() {
    }
}
