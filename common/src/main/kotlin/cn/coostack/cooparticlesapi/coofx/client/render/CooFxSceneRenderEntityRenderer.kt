package cn.coostack.cooparticlesapi.coofx.client.render

import cn.coostack.cooparticlesapi.annotations.CooAutoRegisterRenderer
import cn.coostack.cooparticlesapi.coofx.server.CooFxSceneRenderEntity
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelines
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.RenderInput

/** CooFX scene RenderEntity 的空 renderer；真实 mesh 绘制由 CooFX WORLD_PASS 统一批处理。 */
@CooAutoRegisterRenderer
class CooFxSceneRenderEntityRenderer : RenderEntityRenderer<CooFxSceneRenderEntity> {
    override val pipeline = CooPipelines.entity<CooFxSceneRenderEntity>(CooFxSceneRenderEntity.ID) {}

    override fun render(input: RenderInput<CooFxSceneRenderEntity>) = Unit
}
