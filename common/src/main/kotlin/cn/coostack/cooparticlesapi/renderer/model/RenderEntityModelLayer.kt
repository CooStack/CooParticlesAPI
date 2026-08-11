package cn.coostack.cooparticlesapi.renderer.model

/**
 * 模型内部的纯几何分组，不声明 shader 或后处理行为。
 *
 * 构造函数由 [RenderEntityModelBuilder.layer] 统一调用，避免同一名称在一个模型中产生多个
 * 不相等的 layer 实例。
 *
 * @property id layer 的业务名称，用于把图元分组并在绘制时筛选
 */
data class RenderEntityModelLayer internal constructor(
    val id: String
)
