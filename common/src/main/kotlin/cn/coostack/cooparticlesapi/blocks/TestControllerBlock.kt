package cn.coostack.cooparticlesapi.blocks

import cn.coostack.cooparticlesapi.items.TestBlockBinderItem
import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class TestControllerBlock(properties: Properties) : BaseEntityBlock(properties) {
    init {
        registerDefaultState(stateDefinition.any().setValue(HIDDEN, false))
    }

    override fun codec(): MapCodec<out BaseEntityBlock> {
        return CODEC
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return TestControllerBlockEntity(pos, state)
    }

    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        blockEntityType: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        if (blockEntityType != CooBlockEntityTypes.TEST_CONTROLLER.get()) {
            return null
        }
        @Suppress("UNCHECKED_CAST")
        return BlockEntityTicker<TestControllerBlockEntity> { _, _, _, blockEntity ->
            blockEntity.tickServer()
        } as BlockEntityTicker<T>
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult {
        return openIfCreative(level, pos, player)
    }

    override fun useItemOn(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hitResult: BlockHitResult
    ): ItemInteractionResult {
        if (stack.item is TestBlockBinderItem) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
        }
        return when (openIfCreative(level, pos, player)) {
            InteractionResult.SUCCESS, InteractionResult.CONSUME -> ItemInteractionResult.SUCCESS
            InteractionResult.FAIL -> ItemInteractionResult.FAIL
            else -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        }
    }

    override fun getRenderShape(state: BlockState): RenderShape {
        return if (state.getValue(HIDDEN)) RenderShape.INVISIBLE else RenderShape.MODEL
    }

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape {
        return Shapes.block()
    }

    override fun getCollisionShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape {
        return Shapes.block()
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(HIDDEN)
    }

    private fun openIfCreative(level: Level, pos: BlockPos, player: Player): InteractionResult {
        if (!player.isCreative) {
            return InteractionResult.PASS
        }
        if (!level.isClientSide && player is ServerPlayer) {
            val blockEntity = level.getBlockEntity(pos) as? TestControllerBlockEntity ?: return InteractionResult.FAIL
            TestControllerBlockAccess.openControllerScreen(player, blockEntity)
        }
        return InteractionResult.SUCCESS
    }

    companion object {
        val HIDDEN: BooleanProperty = BooleanProperty.create("hidden")
        val CODEC: MapCodec<TestControllerBlock> = simpleCodec(::TestControllerBlock)
    }
}
