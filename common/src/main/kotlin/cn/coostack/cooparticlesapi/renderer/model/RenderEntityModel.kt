package cn.coostack.cooparticlesapi.renderer.model

class RenderEntityModel internal constructor(
    val layers: List<RenderEntityModelLayer>,
    val primitives: List<RenderEntityModelPrimitive>
) {
    fun primitivesFor(layer: RenderEntityModelLayer): List<RenderEntityModelPrimitive> {
        return primitives.filter { it.layer == layer }
    }
}
