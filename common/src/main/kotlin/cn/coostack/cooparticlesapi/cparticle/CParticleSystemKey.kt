package cn.coostack.cooparticlesapi.cparticle

/**
 * 唯一标识一个 CParticle 系统变体。
 *
 * 同名系统可以按模式、渲染层和纹理绑定拆分，旧的按名称查询仍由 manager 提供。
 * Example: 方块图集和粒子图集可使用相同逻辑名称，但会进入不同系统。
 * Forbidden: 不要忽略 [textureBindingKey] 合并不同主纹理的实例。
 *
 * @property name 调用方使用的逻辑系统名
 * @property mode 系统更新模式
 * @property layer 混合与深度状态
 * @property textureBindingKey 一次 draw 使用的主纹理绑定
 */
data class CParticleSystemKey(
    val name: String,
    val mode: CParticleSystemMode,
    val layer: CParticleRenderLayer,
    val textureBindingKey: CParticleTextureBindingKey,
)
