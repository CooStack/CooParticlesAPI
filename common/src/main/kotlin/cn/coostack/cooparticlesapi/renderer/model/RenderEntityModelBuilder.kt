package cn.coostack.cooparticlesapi.renderer.model

import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f

class RenderEntityModelBuilder {
    private val layers = linkedMapOf<String, RenderEntityModelLayer>()
    private val primitiveVertices =
        linkedMapOf<Pair<RenderEntityModelLayer, RenderEntityModelPrimitiveMode>, MutableList<RenderEntityModelVertex>>()

    /** 获取同名几何层；shader 和后处理由 renderer 的 pipeline 声明。 */
    fun layer(id: String): RenderEntityModelLayer {
        return layers.getOrPut(id) { RenderEntityModelLayer(id) }
    }

    fun addVertex(
        layer: RenderEntityModelLayer,
        position: Vector3f,
        color: Vector4f = Vector4f(1F, 1F, 1F, 1F),
        uv: Vector2f = Vector2f(0F, 0F),
        normal: Vector3f = Vector3f(0F, 1F, 0F),
        primitiveMode: RenderEntityModelPrimitiveMode = RenderEntityModelPrimitiveMode.LINES
    ): RenderEntityModelBuilder {
        primitiveVertices.getOrPut(layer to primitiveMode) { mutableListOf() } += RenderEntityModelVertex(
            position = position,
            color = color,
            uv = uv,
            normal = normal
        )
        return this
    }

    fun addTriangle(
        layer: RenderEntityModelLayer,
        first: RenderEntityModelVertex,
        second: RenderEntityModelVertex,
        third: RenderEntityModelVertex
    ): RenderEntityModelBuilder {
        val vertices = primitiveVertices.getOrPut(layer to RenderEntityModelPrimitiveMode.TRIANGLES) { mutableListOf() }
        vertices += first
        vertices += second
        vertices += third
        return this
    }

    fun addQuad(
        layer: RenderEntityModelLayer,
        first: RenderEntityModelVertex,
        second: RenderEntityModelVertex,
        third: RenderEntityModelVertex,
        fourth: RenderEntityModelVertex
    ): RenderEntityModelBuilder {
        addTriangle(layer, first, second, third)
        addTriangle(layer, first, third, fourth)
        return this
    }

    fun addRenderTypeQuad(
        layer: RenderEntityModelLayer,
        first: RenderEntityModelVertex,
        second: RenderEntityModelVertex,
        third: RenderEntityModelVertex,
        fourth: RenderEntityModelVertex
    ): RenderEntityModelBuilder {
        val vertices = primitiveVertices.getOrPut(layer to RenderEntityModelPrimitiveMode.QUADS) { mutableListOf() }
        vertices += first
        vertices += second
        vertices += third
        vertices += fourth
        return this
    }

    fun build(): RenderEntityModel {
        return RenderEntityModel(
            layers = layers.values.toList(),
            primitives = primitiveVertices.map { (key, vertices) ->
                RenderEntityModelPrimitive(
                    layer = key.first,
                    vertices = vertices.toList(),
                    primitiveMode = key.second
                )
            }
        )
    }
}
