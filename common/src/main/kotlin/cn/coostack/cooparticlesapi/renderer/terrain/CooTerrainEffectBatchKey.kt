package cn.coostack.cooparticlesapi.renderer.terrain

import net.minecraft.resources.Identifier

/**
 * 标识一次地形效果批次的维度和组。
 *
 * @property dimension 批次所属维度 ID
 * @property groupId 批次所属效果组 ID
 */
internal data class CooTerrainEffectBatchKey(
    val dimension: Identifier,
    val groupId: Identifier
)
