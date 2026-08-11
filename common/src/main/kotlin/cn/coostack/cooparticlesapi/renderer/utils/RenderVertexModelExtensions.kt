package cn.coostack.cooparticlesapi.renderer.utils

import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelBuilder
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelLayer

/**
 * 执行 `当前组件` 定义的 `vertices` 操作；输入和返回值用于该组件当前的渲染职责。
 *
 * 示例：`vertices(layer = layer, block = block)`。
 *
 * @param layer 模型分组或地形渲染层，决定图元筛选及 RenderType 行为
 *
 * @param block 在当前生命周期或数据上下文中执行的回调
 *
 * @return 当前操作计算、更新或查询得到的结果
 */
fun RenderEntityModelBuilder.vertices(
    layer: RenderEntityModelLayer,
    block: RenderVertexBuilder.() -> Unit
): RenderEntityModelBuilder {
    RenderVertexBuilder().apply(block).addTo(this, layer)
    return this
}

/**
 * 把输入对象加入 `当前组件` 的 `addVertices` 管理范围，后续查询、构建或绘制会使用该绑定。
 *
 * 示例：`addVertices(layer = layer, builder = builder)`。
 *
 * @param layer 模型分组或地形渲染层，决定图元筛选及 RenderType 行为
 *
 * @param builder 当前操作需要的输入值；其语义由方法名和所属组件共同限定
 *
 * @return 当前操作计算、更新或查询得到的结果
 */
fun RenderEntityModelBuilder.addVertices(
    layer: RenderEntityModelLayer,
    builder: RenderVertexBuilder
): RenderEntityModelBuilder {
    builder.addTo(this, layer)
    return this
}
