package cn.coostack.cooparticlesapi.renderer.model

/**
 * RenderEntity 的不可变几何模型。
 *
 * 该构造函数仅供 builder 使用，调用方应通过 [RenderEntityModelBuilder.build] 创建模型，
 * 以保证 layer 与图元引用已经完成校验。
 *
 * @property layers 模型包含的几何分组
 * @property primitives 按 layer 归属的线、三角形或四边形图元
 */
class RenderEntityModel internal constructor(
    val layers: List<RenderEntityModelLayer>,
    val primitives: List<RenderEntityModelPrimitive>
) {
    /**
     * 返回归属于指定 layer 的图元，保留它们在模型中的原始顺序。
     *
     * 示例：`model.primitivesFor(model.layers.first())`。
     *
     * @param layer 要筛选的模型分组；通常来自当前模型的 [layers]
     * @return 该分组中的图元；没有匹配项时返回空列表
     */
    fun primitivesFor(layer: RenderEntityModelLayer): List<RenderEntityModelPrimitive> {
        return primitives.filter { it.layer == layer }
    }
}
