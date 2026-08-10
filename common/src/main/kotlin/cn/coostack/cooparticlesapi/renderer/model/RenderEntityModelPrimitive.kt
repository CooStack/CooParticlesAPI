package cn.coostack.cooparticlesapi.renderer.model

data class RenderEntityModelPrimitive(
    val layer: RenderEntityModelLayer,
    val vertices: List<RenderEntityModelVertex>,
    val primitiveMode: RenderEntityModelPrimitiveMode = RenderEntityModelPrimitiveMode.LINES
)
