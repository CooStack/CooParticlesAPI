package cn.coostack.cooparticlesapi.entities.structure.editor

import net.minecraft.world.level.block.state.BlockState
import net.minecraft.nbt.CompoundTag
import net.minecraft.core.BlockPos

/**
 * 替换前的整格状态，包含被模板忽略的结构空位，供失败恢复使用。
 * @property pos 世界方块坐标
 * @property state 原始状态，不随世界替换改变
 * @property data 方块实体的完整持久化标签，没有方块实体时为空
 */
internal data class SelectionBlockBackup(val pos: BlockPos, val state: BlockState, val data: CompoundTag?)
