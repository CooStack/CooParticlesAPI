package cn.coostack.cooparticlesapi.test.options.renderer.world

import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModel
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.FramePostRenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionCollector
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionInput
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityFeatureSet
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityVisualProfile

class DemoLightOrbRenderEntityRenderer :
    RenderEntityModelRenderer<DemoLightOrbRenderEntity>,
    FramePostRenderEntityRenderer<DemoLightOrbRenderEntity> {
    override fun initialize(instance: RenderEntityInstance<DemoLightOrbRenderEntity>) {
    }

    override fun describeFeatures(entity: DemoLightOrbRenderEntity): RenderEntityFeatureSet {
        return DemoWorldRenderModelSupport.describeFeatures()
    }

    override fun createVisualProfile(entity: DemoLightOrbRenderEntity): RenderEntityVisualProfile {
        return DemoWorldRenderModelSupport.createVisualProfile()
    }

    override fun buildModel(entity: DemoLightOrbRenderEntity, tickDelta: Float): RenderEntityModel {
        return DemoWorldRenderModelSupport.buildModel(entity) { model, basePipe ->
            val shellColor = DemoWorldRenderModelSupport.alpha(entity.effectColor, 0.46f)
            val coreColor = DemoWorldRenderModelSupport.boosted(entity.effectColor, 1.42f, 0.72f)
            DemoWorldRenderModelSupport.sphereShell(
                model,
                basePipe,
                entity.radius,
                shellColor,
                latSegments = 8,
                lonSegments = 36,
                yScale = 1.0f
            )
            DemoWorldRenderModelSupport.disc(
                model,
                basePipe,
                entity.radius * 0.62f,
                coreColor,
                y = 0f
            )
            DemoWorldRenderModelSupport.sphereGuideRings(model, basePipe, entity.radius * 1.02f, coreColor)
            DemoWorldRenderModelSupport.circle(
                model,
                basePipe,
                entity.radius * 0.78f,
                DemoWorldRenderModelSupport.alpha(entity.effectColor, 0.7f),
                y = entity.radius * 0.25f
            )
            DemoWorldRenderModelSupport.sphereShell(
                model,
                basePipe,
                entity.radius * 1.08f,
                DemoWorldRenderModelSupport.alpha(entity.effectColor, 0.18f),
                latSegments = 5,
                lonSegments = 18,
                yScale = 1.0f
            )
            DemoWorldRenderModelSupport.circle(
                model,
                basePipe,
                entity.radius * 1.35f,
                DemoWorldRenderModelSupport.alpha(entity.effectColor, 0.2f),
                y = 0f
            )
        }
    }

    override fun collectRenderContributions(
        input: RenderContributionInput<DemoLightOrbRenderEntity>,
        collector: RenderContributionCollector
    ) {
        DemoWorldRenderModelSupport.collectModelMaskBloom(
            input,
            collector,
            buildModel(input.instance.entity, input.frameContext.tickDelta)
        )
    }
}
