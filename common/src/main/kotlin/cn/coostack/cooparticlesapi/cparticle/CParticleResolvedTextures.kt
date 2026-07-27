package cn.coostack.cooparticlesapi.cparticle

import net.minecraft.world.phys.Vec3

/**
 * 一粒 CParticle 的基础纹理和可选蒙版解析结果。
 *
 * Example: end rod 基础纹理可以配一个来自方块图集的石头蒙版。
 * Forbidden: 基础纹理或蒙版无效时不能继续把该粒子写入 GPU system。
 *
 * @property base 决定粒子原始轮廓的纹理
 * @property mask 叠在基础纹理上的可选蒙版
 * @property randomBaseQuarterUv 基础纹理是否使用稳定随机四分之一裁剪
 * @property randomMaskQuarterUv 蒙版纹理是否使用稳定随机四分之一裁剪
 */
internal data class CParticleResolvedTextures(
    val base: CParticleResolvedTexture,
    val mask: CParticleResolvedTexture?,
    val randomBaseQuarterUv: Boolean,
    val randomMaskQuarterUv: Boolean,
) {
    /**
     * 两个来源是否都能参与当前 draw。
     *
     * Example: 有效的基础纹理配 `null` 蒙版时返回 `true`。
     * Forbidden: missing 方块或物品来源不能被当成透明蒙版继续绘制。
     */
    val isValid: Boolean
        get() = base.isValid && (mask?.isValid != false)

}

/**
 * 在粒子生成位置解析基础纹理和额外蒙版。
 *
 * Example: [CParticle.effect] 解析为基础 SpriteSet，[CParticle.textureSource] 解析为蒙版。
 * Forbidden: 不要在每个普通渲染帧调用；STATIC 粒子只需在生成时解析一次。
 *
 * @receiver 待解析的粒子描述
 * @param position 用于方块 biome tint 和模型选择的生成位置
 * @return 可用于选择双纹理 system 的解析结果
 */
internal fun CParticle.resolveTextures(position: Vec3): CParticleResolvedTextures {
    val baseSource = effectiveTextureSource()
    val maskSource = textureSource
    return CParticleResolvedTextures(
        base = CParticleTextureResolver.resolve(baseSource, position),
        mask = maskSource?.let { CParticleTextureResolver.resolve(it, position) },
        randomBaseQuarterUv = (baseSource as? CParticleTextureSource.Block)?.randomCrop == true,
        randomMaskQuarterUv = (maskSource as? CParticleTextureSource.Block)?.randomCrop == true,
    )
}
