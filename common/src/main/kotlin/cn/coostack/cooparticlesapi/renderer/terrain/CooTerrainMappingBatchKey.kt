package cn.coostack.cooparticlesapi.renderer.terrain

import net.minecraft.resources.ResourceLocation

/** 标识一个 Mapping 实例在指定合成方式下使用的共享 terrain 批次。 */
internal data class CooTerrainMappingBatchKey(
    val dimension: ResourceLocation,
    val instanceId: ResourceLocation,
    val composition: CooTerrainEffectComposition
)
