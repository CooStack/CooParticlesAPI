package cn.coostack.cooparticlesapi.test.options.renderer.world

import cn.coostack.cooparticlesapi.annotations.CooAutoRegisterRenderer
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModel
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelines
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.RenderInput

/** 光球演示实体的模型与遮罩泛光 renderer。 */
@CooAutoRegisterRenderer
class DemoLightOrbRenderEntityRenderer : RenderEntityRenderer<DemoLightOrbRenderEntity> {
    override val pipeline = CooPipelines.MASK_BLOOM
        .intensity { entity: DemoLightOrbRenderEntity -> entity.intensity }

    override fun render(input: RenderInput<DemoLightOrbRenderEntity>) {
        DemoWorldRenderModelSupport.renderModel(input, buildModel(input.entity, input.tickDelta))
    }

    private fun buildModel(entity: DemoLightOrbRenderEntity, tickDelta: Float): RenderEntityModel {
        return DemoWorldRenderModelSupport.buildModel { model, baseLayer ->
            val shellColor = DemoWorldRenderModelSupport.alpha(entity.effectColor, 0.46F)
            val coreColor = DemoWorldRenderModelSupport.boosted(entity.effectColor, 1.42F, 0.72F)
            DemoWorldRenderModelSupport.sphereShell(
                model,
                baseLayer,
                entity.radius,
                shellColor,
                latSegments = 8,
                lonSegments = 36,
                yScale = 1.0F
            )
            DemoWorldRenderModelSupport.disc(
                model,
                baseLayer,
                entity.radius * 0.62F,
                coreColor,
                y = 0F
            )
            DemoWorldRenderModelSupport.sphereGuideRings(model, baseLayer, entity.radius * 1.02F, coreColor)
            DemoWorldRenderModelSupport.circle(
                model,
                baseLayer,
                entity.radius * 0.78F,
                DemoWorldRenderModelSupport.alpha(entity.effectColor, 0.7F),
                y = entity.radius * 0.25F
            )
            DemoWorldRenderModelSupport.sphereShell(
                model,
                baseLayer,
                entity.radius * 1.08F,
                DemoWorldRenderModelSupport.alpha(entity.effectColor, 0.18F),
                latSegments = 5,
                lonSegments = 18,
                yScale = 1.0F
            )
            DemoWorldRenderModelSupport.circle(
                model,
                baseLayer,
                entity.radius * 1.35F,
                DemoWorldRenderModelSupport.alpha(entity.effectColor, 0.2F),
                y = 0F
            )
        }
    }

}
