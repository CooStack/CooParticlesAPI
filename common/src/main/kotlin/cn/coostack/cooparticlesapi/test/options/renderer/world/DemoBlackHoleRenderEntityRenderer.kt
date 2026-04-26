package cn.coostack.cooparticlesapi.test.options.renderer.world

import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModel
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.FramePostRenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionCollector
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionInput
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityFeatureSet
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityVisualProfile

class DemoBlackHoleRenderEntityRenderer :
    RenderEntityModelRenderer<DemoBlackHoleRenderEntity>,
    FramePostRenderEntityRenderer<DemoBlackHoleRenderEntity> {
    override fun initialize(instance: RenderEntityInstance<DemoBlackHoleRenderEntity>) {
    }

    override fun describeFeatures(entity: DemoBlackHoleRenderEntity): RenderEntityFeatureSet {
        return DemoWorldRenderModelSupport.describeFeatures()
    }

    override fun createVisualProfile(entity: DemoBlackHoleRenderEntity): RenderEntityVisualProfile {
        return DemoWorldRenderModelSupport.createVisualProfile()
    }

    override fun buildModel(entity: DemoBlackHoleRenderEntity, tickDelta: Float): RenderEntityModel {
        return DemoWorldRenderModelSupport.buildModel(entity) { model, basePipe ->
            val hotRing = DemoWorldRenderModelSupport.boosted(entity.effectColor, 1.7f, 0.72f)
            val innerViolet = DemoWorldRenderModelSupport.alpha(entity.effectColor, 0.42f)
            DemoWorldRenderModelSupport.disc(
                model,
                basePipe,
                entity.radius * 0.38f,
                innerViolet,
                y = 0f
            )
            DemoWorldRenderModelSupport.annulus(
                model,
                basePipe,
                entity.radius * 0.52f,
                entity.radius * 1.38f,
                hotRing,
                y = 0f
            )
            DemoWorldRenderModelSupport.circle(model, basePipe, entity.radius * 1.42f, hotRing, y = 0f)
            DemoWorldRenderModelSupport.spiral(
                model,
                basePipe,
                entity.radius * 0.5f,
                entity.radius * 1.55f,
                turns = 2.65f,
                color = DemoWorldRenderModelSupport.alpha(entity.effectColor, 0.85f),
                y = 0.04f
            )
            DemoWorldRenderModelSupport.spiral(
                model,
                basePipe,
                entity.radius * 0.42f,
                entity.radius * 1.48f,
                turns = -2.1f,
                color = DemoWorldRenderModelSupport.alpha(entity.effectColor, 0.58f),
                y = -0.04f
            )
            DemoWorldRenderModelSupport.circle(
                model,
                basePipe,
                entity.radius * 1.65f,
                DemoWorldRenderModelSupport.alpha(entity.effectColor, 0.28f),
                y = 0f
            )
        }
    }

    override fun collectRenderContributions(
        input: RenderContributionInput<DemoBlackHoleRenderEntity>,
        collector: RenderContributionCollector
    ) {
        DemoWorldRenderModelSupport.collectModelMaskBloom(
            input,
            collector,
            buildModel(input.instance.entity, input.frameContext.tickDelta)
        )
    }
}
