package cn.coostack.cooparticlesapi.test.options.renderer.world

import cn.coostack.cooparticlesapi.annotations.CooAutoRegisterRenderer
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModel
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelines
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.RenderInput

/** 黑洞演示实体的模型与遮罩泛光 renderer。 */
@CooAutoRegisterRenderer
class DemoBlackHoleRenderEntityRenderer : RenderEntityRenderer<DemoBlackHoleRenderEntity> {
    override val pipeline = CooPipelines.MASK_BLOOM
        .intensity { entity: DemoBlackHoleRenderEntity -> entity.intensity }

    override fun render(input: RenderInput<DemoBlackHoleRenderEntity>) {
        DemoWorldRenderModelSupport.renderModel(input, buildModel(input.entity, input.tickDelta))
    }

    private fun buildModel(entity: DemoBlackHoleRenderEntity, tickDelta: Float): RenderEntityModel {
        return DemoWorldRenderModelSupport.buildModel { model, baseLayer ->
            val hotRing = DemoWorldRenderModelSupport.boosted(entity.effectColor, 1.7F, 0.72F)
            val innerViolet = DemoWorldRenderModelSupport.alpha(entity.effectColor, 0.42F)
            DemoWorldRenderModelSupport.disc(
                model,
                baseLayer,
                entity.radius * 0.38F,
                innerViolet,
                y = 0F
            )
            DemoWorldRenderModelSupport.annulus(
                model,
                baseLayer,
                entity.radius * 0.52F,
                entity.radius * 1.38F,
                hotRing,
                y = 0F
            )
            DemoWorldRenderModelSupport.circle(model, baseLayer, entity.radius * 1.42F, hotRing, y = 0F)
            DemoWorldRenderModelSupport.spiral(
                model,
                baseLayer,
                entity.radius * 0.5F,
                entity.radius * 1.55F,
                turns = 2.65F,
                color = DemoWorldRenderModelSupport.alpha(entity.effectColor, 0.85F),
                y = 0.04F
            )
            DemoWorldRenderModelSupport.spiral(
                model,
                baseLayer,
                entity.radius * 0.42F,
                entity.radius * 1.48F,
                turns = -2.1F,
                color = DemoWorldRenderModelSupport.alpha(entity.effectColor, 0.58F),
                y = -0.04F
            )
            DemoWorldRenderModelSupport.circle(
                model,
                baseLayer,
                entity.radius * 1.65F,
                DemoWorldRenderModelSupport.alpha(entity.effectColor, 0.28F),
                y = 0F
            )
        }
    }

}
