package cn.coostack.cooparticlesapi.renderer.utils

import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelBuilder
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelPipe

fun RenderEntityModelBuilder.vertices(
    pipe: RenderEntityModelPipe,
    block: RenderVertexBuilder.() -> Unit
): RenderEntityModelBuilder {
    RenderVertexBuilder().apply(block).addTo(this, pipe)
    return this
}

fun RenderEntityModelBuilder.addVertices(
    pipe: RenderEntityModelPipe,
    builder: RenderVertexBuilder
): RenderEntityModelBuilder {
    builder.addTo(this, pipe)
    return this
}
