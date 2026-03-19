package cn.coostack.cooparticlesapi.renderer.effects.builtin

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.resources.ResourceLocation

/**
 * 仓库内置 effect type 常量表。
 */
object BuiltinRenderEffectTypes {
    /** 屏幕空间 glow 效果类型。 */
    val SCREEN_GLOW: ResourceLocation = id("effect/screen_glow")
    /** 持久 bloom 效果类型。 */
    val PERSISTENT_BLOOM: ResourceLocation = id("effect/persistent_bloom")
    /** 基于模型/贴图内容 mask 的 bloom 效果类型。 */
    val MASK_BLOOM: ResourceLocation = id("effect/mask_bloom")
    /** 世界光照合成效果类型。 */
    val WORLD_LIGHT: ResourceLocation = id("effect/world_light")
    /** 帧尾 glow 球体效果类型。仅为兼容残留，不再推荐给 RenderEntity glow 使用。 */
    @Deprecated("Legacy compatibility only. RenderEntity glow should use MASK_BLOOM.")
    val POST_GLOW_SPHERE: ResourceLocation = id("effect/post_glow_sphere")
    /** compute dispatch 效果类型。 */
    val COMPUTE_DISPATCH: ResourceLocation = id("effect/compute_dispatch")

    /**
     * 生成当前 mod 命名空间下的内建 effect id。
     */
    private fun id(path: String): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, path)
    }
}
