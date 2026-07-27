package cn.coostack.cooparticlesapi.cparticle

import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.resources.ResourceLocation

/**
 * 区分图集与独立纹理的绑定方式。
 *
 * Example: 方块模型使用 [ATLAS]，`textures/effect/foo.png` 使用 [TEXTURE]。
 * Forbidden: 不要把尚未 stitch 的独立图片声明为 [ATLAS]。
 */
enum class CParticleTextureBindingKind {
    /**
     * 由 `ModelManager` 管理并已 stitch 的纹理图集。
     *
     * Example: `TextureAtlas.LOCATION_BLOCKS`。
     * Forbidden: 普通 `textures/...png` 资源不能按图集查询 sprite。
     */
    ATLAS,

    /**
     * 由 `TextureManager` 直接管理的独立纹理。
     *
     * Example: `cooparticlesapi:textures/test/magic.png`。
     * Forbidden: 不要用它绑定 atlas sprite 的资源名。
     */
    TEXTURE,
}

/**
 * 一次实例化绘制实际使用的主纹理绑定。
 *
 * 相同 key 的粒子可以共用 draw call；不同 key 必须进入不同系统或批次。
 * Example: `CParticleTextureBindingKey.BLOCK_ATLAS` 可混合多个 BlockState 和物品 sprite。
 * Forbidden: 粒子进入系统后不能只改 [location] 而继续使用原系统槽位。
 *
 * @property kind 纹理是 stitch 图集还是独立资源
 * @property location 实际交给模型图集或纹理管理器的资源 ID
 */
data class CParticleTextureBindingKey(
    val kind: CParticleTextureBindingKind,
    val location: ResourceLocation,
) : Comparable<CParticleTextureBindingKey> {
    /**
     * 为批次排序提供稳定顺序，减少相邻系统之间的重复绑定。
     *
     * Example: renderer 可按 `layer` 后再按 binding key 排序。
     * Forbidden: 不要把此顺序当成渲染层的混合顺序。
     *
     * @param other 另一个纹理绑定
     * @return 先比较绑定类型，再比较资源 ID 字符串的结果
     */
    override fun compareTo(other: CParticleTextureBindingKey): Int {
        val kindOrder = kind.compareTo(other.kind)
        return if (kindOrder != 0) kindOrder else location.toString().compareTo(other.location.toString())
    }

    /**
     * 保存内建图集和 missing texture 的共享 key。
     *
     * Example: legacy sprite/effect 默认使用 [PARTICLE_ATLAS]。
     * Forbidden: 不要在运行时替换这些不可变 key。
     */
    companion object {
        /**
         * 原版粒子图集。
         *
         * Example: `textureOfEffect(effect)` 使用此绑定。
         * Forbidden: 方块模型的 particle icon 不应强制绑定到此图集。
         */
        @JvmField
        val PARTICLE_ATLAS = CParticleTextureBindingKey(
            CParticleTextureBindingKind.ATLAS,
            TextureAtlas.LOCATION_PARTICLES,
        )

        /**
         * 原版方块图集。
         *
         * Example: `textureOfBlock(state)` 使用此绑定。
         * Forbidden: 独立 PNG 不能使用此 key。
         */
        @JvmField
        val BLOCK_ATLAS = CParticleTextureBindingKey(
            CParticleTextureBindingKind.ATLAS,
            TextureAtlas.LOCATION_BLOCKS,
        )

        /**
         * 原版 missing texture 的独立纹理绑定。
         *
         * Example: 空气方块会安全回退到此绑定。
         * Forbidden: 不要把它当作有效模型解析成功的标志。
         */
        @JvmField
        val MISSING = CParticleTextureBindingKey(
            CParticleTextureBindingKind.TEXTURE,
            MissingTextureAtlasSprite.getLocation(),
        )
    }
}
