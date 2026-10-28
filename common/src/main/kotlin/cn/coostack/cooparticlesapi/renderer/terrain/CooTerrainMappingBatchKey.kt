package cn.coostack.cooparticlesapi.renderer.terrain

import net.minecraft.resources.Identifier

/** 标识一个 Mapping 实例在指定合成方式下使用的共享 terrain 批次。 */
internal data class CooTerrainMappingBatchKey(
    val dimension: Identifier,
    val instanceId: Identifier,
    val composition: CooTerrainEffectComposition
)
