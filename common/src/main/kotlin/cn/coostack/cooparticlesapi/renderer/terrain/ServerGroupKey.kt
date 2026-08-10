package cn.coostack.cooparticlesapi.renderer.terrain

import net.minecraft.resources.ResourceLocation

/**
 * 服务端效果组存储键。
 *
 * @property dimension 组所属维度 ID
 * @property id 组 ID
 */
internal data class ServerGroupKey(
    val dimension: ResourceLocation,
    val id: ResourceLocation
)
