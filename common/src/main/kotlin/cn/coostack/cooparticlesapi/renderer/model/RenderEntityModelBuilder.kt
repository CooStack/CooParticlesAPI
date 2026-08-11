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

    /**
     * 在 `RenderEntityModelBuilder` 中配置 `addVertex`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`addVertex(layer = layer, position = position, color = color, uv = uv, normal = normal, primitiveMode = primitiveMode)`。
     *
     * @param layer 模型分组或地形渲染层，决定图元筛选及 RenderType 行为
     *
     * @param position 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param color 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param uv 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param normal 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param primitiveMode 顶点的图元组装方式，决定每组顶点形成线、三角形还是四边形
     *
     * @return 当前构建器或由其配置生成的结果
     */
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

    /**
     * 在 `RenderEntityModelBuilder` 中配置 `addTriangle`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`addTriangle(layer = layer, first = first, second = second, third = third)`。
     *
     * @param layer 模型分组或地形渲染层，决定图元筛选及 RenderType 行为
     *
     * @param first 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param second 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param third 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
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

    /**
     * 在 `RenderEntityModelBuilder` 中配置 `addQuad`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`addQuad(layer = layer, first = first, second = second, third = third, fourth = fourth)`。
     *
     * @param layer 模型分组或地形渲染层，决定图元筛选及 RenderType 行为
     *
     * @param first 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param second 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param third 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param fourth 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
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

    /**
     * 在 `RenderEntityModelBuilder` 中配置 `addRenderTypeQuad`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`addRenderTypeQuad(layer = layer, first = first, second = second, third = third, fourth = fourth)`。
     *
     * @param layer 模型分组或地形渲染层，决定图元筛选及 RenderType 行为
     *
     * @param first 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param second 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param third 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param fourth 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
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

    /**
     * 根据输入和 `RenderEntityModelBuilder` 当前配置创建 `build` 结果；返回对象保留本次配置的语义。
     *
     * 示例：`build()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
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
