package cn.coostack.cooparticlesapi.renderer.terrain

import net.minecraft.resources.ResourceLocation

/**
 * 地形 shader 缓存键。
 *
 * @property shaderId shader 资源 ID
 * @property descriptor 根据 Pipeline 输入和 uniform 生成的完整描述文本
 */
internal data class CooTerrainShaderCacheKey(
    val shaderId: ResourceLocation,
    val descriptor: String
)
