package cn.coostack.cooparticlesapi.items

import cn.coostack.cooparticlesapi.blocks.TestControllerBlockAccess
import cn.coostack.cooparticlesapi.blocks.TestControllerBlockEntity
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level

class TestBlockBinderItem(properties: Properties) : Item(properties) {
    override fun getName(stack: ItemStack): Component {
        return Component.literal("测试方块调试器")
    }

    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        tooltipComponents: MutableList<Component>,
        tooltipFlag: TooltipFlag
    ) {
        tooltipComponents.add(Component.literal("右键一个测试方块绑定").withStyle(ChatFormatting.GRAY))
        tooltipComponents.add(Component.literal("Shift + 右键: 开启或者取消测试").withStyle(ChatFormatting.GRAY))
    }

    override fun useOn(context: UseOnContext): InteractionResult {
        val level = context.level
        val player = context.player ?: return InteractionResult.PASS
        val stack = context.itemInHand
        val binding = TestBlockBinding(
            dimension = TestControllerBlockAccess.dimensionId(level),
            pos = context.clickedPos.immutable()
        )
        val blockEntity = level.getBlockEntity(context.clickedPos)
        if (blockEntity !is TestControllerBlockEntity) {
            if (!level.isClientSide && TestBlockBindings.remove(stack, binding)) {
                player.sendSystemMessage(Component.literal("已移除失效测试方块绑定 ${formatPos(binding.pos)}"))
                return InteractionResult.SUCCESS
            }
            return InteractionResult.PASS
        }

        if (!level.isClientSide) {
            if (player.isShiftKeyDown) {
                if (blockEntity.isRunning()) {
                    blockEntity.stopTest()
                    player.sendSystemMessage(Component.literal("已停止测试方块 ${formatPos(binding.pos)}"))
                } else {
                    if (blockEntity.startTest()) {
                        player.sendSystemMessage(Component.literal("已启动测试方块 ${formatPos(binding.pos)}"))
                    } else {
                        player.sendSystemMessage(Component.literal("测试方块启动失败 ${formatPos(binding.pos)}"))
                    }
                }
            } else {
                if (TestBlockBindings.add(stack, binding)) {
                    player.sendSystemMessage(Component.literal("已绑定测试方块 ${formatPos(binding.pos)}"))
                } else {
                    player.sendSystemMessage(Component.literal("该测试方块已经绑定"))
                }
            }
        }

        return InteractionResult.SUCCESS
    }

    override fun use(level: Level, player: net.minecraft.world.entity.player.Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        if (!level.isClientSide && player is ServerPlayer) {
            if (player.isShiftKeyDown) {
                val result = TestControllerBlockAccess.toggleBoundTests(player, stack)
                when {
                    result.stopped > 0 -> {
                        player.sendSystemMessage(Component.literal("已停止 ${result.stopped} 个正在运行的测试方块"))
                    }
                    result.started > 0 -> {
                        player.sendSystemMessage(Component.literal("已启动 ${result.started} 个测试方块"))
                    }
                    else -> {
                        player.sendSystemMessage(Component.literal("没有可启动或可停止的测试方块"))
                    }
                }
                if (result.removed > 0) {
                    player.sendSystemMessage(Component.literal("已移除 ${result.removed} 个失效测试方块绑定"))
                }
            } else {
                TestControllerBlockAccess.openBoundSelection(player, stack)
            }
        }
        return InteractionResultHolder.success(stack)
    }

    private fun formatPos(pos: net.minecraft.core.BlockPos): String {
        return "${pos.x}, ${pos.y}, ${pos.z}"
    }
}
