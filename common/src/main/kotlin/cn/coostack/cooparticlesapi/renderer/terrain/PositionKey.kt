package cn.coostack.cooparticlesapi.renderer.terrain

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation

/**
 * 位置到效果组反向索引的键。
 *
 * @property dimension 方块所在维度 ID
 * @property position 方块坐标
 */
internal data class PositionKey(
    val dimension: ResourceLocation,
    val position: BlockPos
)
