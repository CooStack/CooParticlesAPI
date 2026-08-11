package cn.coostack.cooparticlesapi.test.options.renderer.world

import cn.coostack.cooparticlesapi.annotations.CooAutoRegisterRenderer
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModel
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelines
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.RenderInput

/** 护盾演示实体的模型与遮罩泛光 renderer。 */
@CooAutoRegisterRenderer
class DemoShieldRenderEntityRenderer : RenderEntityRenderer<DemoShieldRenderEntity> {
    override val pipeline = CooPipelines.MASK_BLOOM
        .intensity { entity: DemoShieldRenderEntity -> entity.intensity }

    override fun render(input: RenderInput<DemoShieldRenderEntity>) {
        DemoWorldRenderModelSupport.renderModel(input, buildModel(input.entity, input.tickDelta))
    }

    private fun buildModel(entity: DemoShieldRenderEntity, tickDelta: Float): RenderEntityModel {
        return DemoWorldRenderModelSupport.buildModel { model, baseLayer ->
            val shellColor = DemoWorldRenderModelSupport.alpha(entity.effectColor, 0.32F)
            val ridgeColor = DemoWorldRenderModelSupport.boosted(entity.effectColor, 1.18F, 0.9F)
            DemoWorldRenderModelSupport.sphereShell(
                model,
                baseLayer,
                entity.radius,
                shellColor,
                latSegments = 10,
                lonSegments = 40,
                yScale = 1.0F
            )
            DemoWorldRenderModelSupport.sphereGuideRings(
                model,
                baseLayer,
                entity.radius * 1.02F,
                ridgeColor
            )
            DemoWorldRenderModelSupport.circle(
                model,
                baseLayer,
                entity.radius * 1.18F,
                DemoWorldRenderModelSupport.alpha(entity.effectColor, 0.26F),
                y = entity.radius * 0.28F
            )
            DemoWorldRenderModelSupport.circle(
                model,
                baseLayer,
                entity.radius * 1.18F,
                DemoWorldRenderModelSupport.alpha(entity.effectColor, 0.26F),
                y = -entity.radius * 0.28F
            )
            DemoWorldRenderModelSupport.sphereShell(
                model,
                baseLayer,
                entity.radius * 1.13F,
                DemoWorldRenderModelSupport.alpha(entity.effectColor, 0.12F),
                latSegments = 6,
                lonSegments = 24,
                yScale = 1.0F
            )
        }
    }

}
