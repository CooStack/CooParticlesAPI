package cn.coostack.cooparticlesapi.renderer.effects.builtin

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.resources.ResourceLocation

/**
 * 仓库内置 effect type 常量表。
 */
internal object BuiltinRenderEffectTypes {
    /** 世界光照合成效果类型。 */
    val WORLD_LIGHT: ResourceLocation = id("effect/world_light")
    /** compute dispatch 效果类型。 */
    val COMPUTE_DISPATCH: ResourceLocation = id("effect/compute_dispatch")

    /**
     * 生成当前 mod 命名空间下的内建 effect id。
     */
    private fun id(path: String): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, path)
    }
}
