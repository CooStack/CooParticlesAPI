package cn.coostack.cooparticlesapi.entities.structure

import com.mojang.serialization.MapCodec
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.entity.player.Player
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.Containers
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level

/** 可持久保存配置的模型方块；仅明确转换时生成实体并移除方块。 */
class ModelBlock(settings: Properties) : Block(settings), EntityBlock {
    override fun codec(): MapCodec<out Block> = simpleCodec(::ModelBlock)

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = ModelBlockEntity(pos, state)

    /** 转换时库存已先转交实体并清空；其他破坏路径只散落一次真实库存。 */
    override fun onRemove(state: BlockState, world: Level, pos: BlockPos, newState: BlockState, moved: Boolean) {
        if (!state.`is`(newState.block) && !world.isClientSide) {
            (world.getBlockEntity(pos) as? ModelBlockEntity)?.let {
                Containers.dropContents(world, pos, it.drops)
                it.drops.clearContent()
            }
        }
        super.onRemove(state, world, pos, newState, moved)
    }

    /** 客户端只预测成功，实际结构目录与转换请求由服务端处理。 */
    override fun useWithoutItem(state: BlockState, world: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        if (player.isSpectator || !player.abilities.mayBuild) return InteractionResult.PASS
        if (player is ServerPlayer) StructureModels.openEditor(player, pos, null)
        return InteractionResult.sidedSuccess(world.isClientSide)
    }
}
