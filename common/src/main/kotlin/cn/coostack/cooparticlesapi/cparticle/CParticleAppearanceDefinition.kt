package cn.coostack.cooparticlesapi.cparticle

/**
 * 汇总一个共享粒子外观 descriptor 引用的不可变曲线。
 *
 * 示例：一个定义可以同时包含贝塞尔 alpha、轴向缩放和 RGB 曲线。
 * 禁止：descriptor 定义必须保存值副本，不能引用调用方持有的数组。
 *
 * @property alpha 不透明度倍率曲线
 * @property scale X/Y 等比缩放倍率曲线
 * @property scaleX X 轴缩放倍率曲线
 * @property scaleY Y 轴缩放倍率曲线
 * @property color RGB 倍率曲线
 */
internal data class CParticleAppearanceDefinition(
    val alpha: CParticleScalarCurveDefinition?,
    val scale: CParticleScalarCurveDefinition?,
    val scaleX: CParticleScalarCurveDefinition?,
    val scaleY: CParticleScalarCurveDefinition?,
    val color: CParticleColorCurveDefinition?,
)
