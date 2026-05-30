package cn.coostack.cooparticlesapi.renderer.model

import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f

class RenderEntityModelBuilder {
    private val pipes = linkedMapOf<String, RenderEntityModelPipe>()
    private val primitiveVertices =
        linkedMapOf<Pair<RenderEntityModelPipe, RenderEntityModelPrimitiveMode>, MutableList<RenderEntityModelVertex>>()

    fun pipe(id: String, block: RenderEntityModelPipeBuilder.() -> Unit = {}): RenderEntityModelPipe {
        return pipes.getOrPut(id) {
            RenderEntityModelPipeBuilder(id).apply(block).build()
        }
    }

    fun addVertex(
        pipe: RenderEntityModelPipe,
        position: Vector3f,
        color: Vector4f = Vector4f(1f, 1f, 1f, 1f),
        uv: Vector2f = Vector2f(0f, 0f),
        normal: Vector3f = Vector3f(0f, 1f, 0f),
        primitiveMode: RenderEntityModelPrimitiveMode = RenderEntityModelPrimitiveMode.LINES
    ): RenderEntityModelBuilder {
        primitiveVertices.getOrPut(pipe to primitiveMode) { mutableListOf() } += RenderEntityModelVertex(
            position = position,
            color = color,
            uv = uv,
            normal = normal
        )
        return this
    }

    fun addTriangle(
        pipe: RenderEntityModelPipe,
        first: RenderEntityModelVertex,
        second: RenderEntityModelVertex,
        third: RenderEntityModelVertex
    ): RenderEntityModelBuilder {
        val vertices = primitiveVertices.getOrPut(pipe to RenderEntityModelPrimitiveMode.TRIANGLES) { mutableListOf() }
        vertices += first
        vertices += second
        vertices += third
        return this
    }

    fun addQuad(
        pipe: RenderEntityModelPipe,
        first: RenderEntityModelVertex,
        second: RenderEntityModelVertex,
        third: RenderEntityModelVertex,
        fourth: RenderEntityModelVertex
    ): RenderEntityModelBuilder {
        addTriangle(pipe, first, second, third)
        addTriangle(pipe, first, third, fourth)
        return this
    }

    fun addRenderTypeQuad(
        pipe: RenderEntityModelPipe,
        first: RenderEntityModelVertex,
        second: RenderEntityModelVertex,
        third: RenderEntityModelVertex,
        fourth: RenderEntityModelVertex
    ): RenderEntityModelBuilder {
        val vertices = primitiveVertices.getOrPut(pipe to RenderEntityModelPrimitiveMode.QUADS) { mutableListOf() }
        vertices += first
        vertices += second
        vertices += third
        vertices += fourth
        return this
    }

    fun build(): RenderEntityModel {
        return RenderEntityModel(
            pipes = pipes.values.toList(),
            primitives = primitiveVertices.map { (key, vertices) ->
                RenderEntityModelPrimitive(
                    pipe = key.first,
                    vertices = vertices.toList(),
                    primitiveMode = key.second
                )
            }
        )
    }
}
