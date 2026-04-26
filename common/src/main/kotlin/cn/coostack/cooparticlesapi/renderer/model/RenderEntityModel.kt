package cn.coostack.cooparticlesapi.renderer.model

class RenderEntityModel internal constructor(
    val pipes: List<RenderEntityModelPipe>,
    val primitives: List<RenderEntityModelPrimitive>
) {
    fun primitivesFor(pipe: RenderEntityModelPipe): List<RenderEntityModelPrimitive> {
        return primitives.filter { it.pipe == pipe }
    }
}
