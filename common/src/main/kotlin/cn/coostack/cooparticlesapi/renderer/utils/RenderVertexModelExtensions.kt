package cn.coostack.cooparticlesapi.renderer.utils

import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelBuilder
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelLayer

fun RenderEntityModelBuilder.vertices(
    layer: RenderEntityModelLayer,
    block: RenderVertexBuilder.() -> Unit
): RenderEntityModelBuilder {
    RenderVertexBuilder().apply(block).addTo(this, layer)
    return this
}

fun RenderEntityModelBuilder.addVertices(
    layer: RenderEntityModelLayer,
    builder: RenderVertexBuilder
): RenderEntityModelBuilder {
    builder.addTo(this, layer)
    return this
}
