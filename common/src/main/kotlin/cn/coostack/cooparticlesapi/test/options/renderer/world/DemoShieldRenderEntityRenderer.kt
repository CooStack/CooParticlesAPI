package cn.coostack.cooparticlesapi.test.options.renderer.world

import cn.coostack.cooparticlesapi.annotations.CooAutoRegisterRenderer
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModel
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.AutoRegisteredRenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.FramePostRenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionCollector
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionInput
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityFeatureSet
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityVisualProfile

/** 护盾演示实体的模型与遮罩泛光 renderer。 */
@CooAutoRegisterRenderer
class DemoShieldRenderEntityRenderer :
    RenderEntityModelRenderer<DemoShieldRenderEntity>,
    FramePostRenderEntityRenderer<DemoShieldRenderEntity>,
    AutoRegisteredRenderEntityRenderer<DemoShieldRenderEntity> {
    override fun initialize(instance: RenderEntityInstance<DemoShieldRenderEntity>) {
    }

    override fun describeFeatures(entity: DemoShieldRenderEntity): RenderEntityFeatureSet {
        return DemoWorldRenderModelSupport.describeFeatures()
    }

    override fun createVisualProfile(entity: DemoShieldRenderEntity): RenderEntityVisualProfile {
        return DemoWorldRenderModelSupport.createVisualProfile()
    }

    override fun buildModel(entity: DemoShieldRenderEntity, tickDelta: Float): RenderEntityModel {
        return DemoWorldRenderModelSupport.buildModel(entity) { model, basePipe ->
            val shellColor = DemoWorldRenderModelSupport.alpha(entity.effectColor, 0.32f)
            val ridgeColor = DemoWorldRenderModelSupport.boosted(entity.effectColor, 1.18f, 0.9f)
            DemoWorldRenderModelSupport.sphereShell(
                model,
                basePipe,
                entity.radius,
                shellColor,
                latSegments = 10,
                lonSegments = 40,
                yScale = 1.0f
            )
            DemoWorldRenderModelSupport.sphereGuideRings(
                model,
                basePipe,
                entity.radius * 1.02f,
                ridgeColor
            )
            DemoWorldRenderModelSupport.circle(
                model,
                basePipe,
                entity.radius * 1.18f,
                DemoWorldRenderModelSupport.alpha(entity.effectColor, 0.26f),
                y = entity.radius * 0.28f
            )
            DemoWorldRenderModelSupport.circle(
                model,
                basePipe,
                entity.radius * 1.18f,
                DemoWorldRenderModelSupport.alpha(entity.effectColor, 0.26f),
                y = -entity.radius * 0.28f
            )
            DemoWorldRenderModelSupport.sphereShell(
                model,
                basePipe,
                entity.radius * 1.13f,
                DemoWorldRenderModelSupport.alpha(entity.effectColor, 0.12f),
                latSegments = 6,
                lonSegments = 24,
                yScale = 1.0f
            )
        }
    }

    override fun collectRenderContributions(
        input: RenderContributionInput<DemoShieldRenderEntity>,
        collector: RenderContributionCollector
    ) {
        DemoWorldRenderModelSupport.collectModelMaskBloom(
            input,
            collector,
            buildModel(input.instance.entity, input.frameContext.tickDelta)
        )
    }
}
