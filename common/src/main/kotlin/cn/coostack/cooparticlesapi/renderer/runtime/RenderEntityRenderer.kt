package cn.coostack.cooparticlesapi.renderer.runtime

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.pipeline.CooRenderPipeline

/** RenderEntity 的统一渲染入口。 */
interface RenderEntityRenderer<T : RenderEntity> {
    val pipeline: CooRenderPipeline<T>

    fun render(input: RenderInput<T>)
}
