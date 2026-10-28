package cn.coostack.cooparticlesapi.renderer.terrain

import net.minecraft.resources.Identifier

/**
 * 客户端效果组索引键。
 *
 * @property dimension 组所属维度 ID
 * @property id 组 ID
 */
internal data class GroupKey(
    val dimension: Identifier,
    val id: Identifier
)
