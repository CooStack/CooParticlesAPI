package cn.coostack.cooparticlesapi.renderer.terrain

/**
 * 顶点效果坐标，范围约束和打包由 [CooEffectUvResolver] 负责。
 *
 * @property u 效果纹理横坐标，写入 UV1 的低 16 位
 * @property v 效果纹理纵坐标，写入 UV1 的高 16 位
 */
internal data class CooEffectUv(
    val u: Float,
    val v: Float
)
