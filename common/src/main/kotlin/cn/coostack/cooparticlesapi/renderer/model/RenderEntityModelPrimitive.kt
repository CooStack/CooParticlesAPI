package cn.coostack.cooparticlesapi.renderer.model

data class RenderEntityModelPrimitive(
    val pipe: RenderEntityModelPipe,
    val vertices: List<RenderEntityModelVertex>,
    val primitiveMode: RenderEntityModelPrimitiveMode = RenderEntityModelPrimitiveMode.LINES
)
