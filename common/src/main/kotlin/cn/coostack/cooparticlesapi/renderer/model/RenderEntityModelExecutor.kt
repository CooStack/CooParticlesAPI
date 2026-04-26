package cn.coostack.cooparticlesapi.renderer.model

import cn.coostack.cooparticlesapi.renderer.runtime.LocalRenderInput

fun interface RenderEntityModelExecutor {
    fun draw(model: RenderEntityModel, input: LocalRenderInput<*>)
}
