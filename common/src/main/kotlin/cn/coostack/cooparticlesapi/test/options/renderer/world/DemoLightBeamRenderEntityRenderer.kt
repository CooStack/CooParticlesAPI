package cn.coostack.cooparticlesapi.test.options.renderer.world

import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModel
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.FramePostRenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionCollector
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionInput
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityFeatureSet
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityVisualProfile
import org.joml.Vector3f

class DemoLightBeamRenderEntityRenderer :
    RenderEntityModelRenderer<DemoLightBeamRenderEntity>,
    FramePostRenderEntityRenderer<DemoLightBeamRenderEntity> {
    override fun initialize(instance: RenderEntityInstance<DemoLightBeamRenderEntity>) {
    }

    override fun describeFeatures(entity: DemoLightBeamRenderEntity): RenderEntityFeatureSet {
        return DemoWorldRenderModelSupport.describeFeatures()
    }

    override fun createVisualProfile(entity: DemoLightBeamRenderEntity): RenderEntityVisualProfile {
        return DemoWorldRenderModelSupport.createVisualProfile()
    }

    override fun buildModel(entity: DemoLightBeamRenderEntity, tickDelta: Float): RenderEntityModel {
        return DemoWorldRenderModelSupport.buildModel(entity) { model, basePipe ->
            val height = entity.radius * 5f
            val coreColor = DemoWorldRenderModelSupport.boosted(entity.effectColor, 2.2f, 0.95f)
            DemoWorldRenderModelSupport.line(
                model,
                basePipe,
                Vector3f(0f, -height, 0f),
                Vector3f(0f, height, 0f),
                coreColor
            )
        }
    }

    override fun collectRenderContributions(
        input: RenderContributionInput<DemoLightBeamRenderEntity>,
        collector: RenderContributionCollector
    ) {
        DemoWorldRenderModelSupport.collectModelMaskBloom(
            input,
            collector,
            buildModel(input.instance.entity, input.frameContext.tickDelta)
        )
    }
}
