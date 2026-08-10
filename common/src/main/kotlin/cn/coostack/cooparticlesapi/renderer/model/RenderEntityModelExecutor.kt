package cn.coostack.cooparticlesapi.renderer.model

import cn.coostack.cooparticlesapi.renderer.runtime.RenderInput

fun interface RenderEntityModelExecutor {
    fun draw(model: RenderEntityModel, input: RenderInput<*>)
}
