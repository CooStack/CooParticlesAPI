package cn.coostack.cooparticlesapi.test.options.renderer.world

import cn.coostack.cooparticlesapi.annotations.CooAutoRegisterRenderer
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModel
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelines
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.RenderInput
import org.joml.Vector3f

/** 光束演示实体的模型与遮罩泛光 renderer。 */
@CooAutoRegisterRenderer
class DemoLightBeamRenderEntityRenderer : RenderEntityRenderer<DemoLightBeamRenderEntity> {
    override val pipeline = CooPipelines.MASK_BLOOM
        .blurSigma(5.5F)
        .blurRange(5.5F)
        .intensity { entity: DemoLightBeamRenderEntity -> entity.intensity }

    override fun render(input: RenderInput<DemoLightBeamRenderEntity>) {
        DemoWorldRenderModelSupport.renderModel(input, buildModel(input.entity, input.tickDelta))
    }

    private fun buildModel(entity: DemoLightBeamRenderEntity, tickDelta: Float): RenderEntityModel {
        return DemoWorldRenderModelSupport.buildModel { model, baseLayer ->
            val height = entity.radius * 5F
            val coreColor = DemoWorldRenderModelSupport.boosted(entity.effectColor, 2.2F, 0.95F)
            DemoWorldRenderModelSupport.line(
                model,
                baseLayer,
                Vector3f(0F, -height, 0F),
                Vector3f(0F, height, 0F),
                coreColor
            )
        }
    }

}
