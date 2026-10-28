package cn.coostack.cooparticlesapi.items

import cn.coostack.cooparticlesapi.test.APITestGroupBuilder
import cn.coostack.cooparticlesapi.test.TestManager
import net.minecraft.network.chat.Component
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.component.TooltipDisplay
import java.util.function.Consumer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.ChatFormatting

class APIGroupTestingItem(settings: Properties) : Item(settings) {
    override fun getName(stack: ItemStack): Component {
        return Component.literal("API 测试控制器").withStyle(ChatFormatting.GOLD)
    }

    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        display: TooltipDisplay,
        tooltipComponents: Consumer<Component>,
        tooltipFlag: TooltipFlag
    ) {
        tooltipComponents.accept(
            Component.literal("用途: 启动并控制 APITestGroupBuilder 测试序列").withStyle(ChatFormatting.YELLOW)
        )
        tooltipComponents.accept(
            Component.literal("右键: 当前项通过并继续").withStyle(ChatFormatting.GRAY)
        )
        tooltipComponents.accept(
            Component.literal("潜行右键: 快进 +5").withStyle(ChatFormatting.GRAY)
        )
        tooltipComponents.accept(
            Component.literal("冲刺右键: 跳到最后").withStyle(ChatFormatting.GRAY)
        )
        tooltipComponents.accept(
            Component.literal("PageDown/PageUp: 前进或回退当前测试项").withStyle(ChatFormatting.DARK_GRAY)
        )
    }

    override fun use(world: Level, user: Player, hand: InteractionHand): InteractionResult {
        if (world.isClientSide) {
            return InteractionResult.SUCCESS
        }

        val test = TestManager.getGamingTestFromServer(user)
        if (test == null) {
            user.sendSystemMessage(Component.literal("开始测试"))
            TestManager.startTest(APITestGroupBuilder.ID, user)
            user.sendSystemMessage(
                Component.literal(
                    "控制: 右键=通过并继续, 潜行右键=快进+5, 冲刺右键=跳到最后, PageUp/PageDown 可回退/前进"
                )
            )
        } else if (user.isSprinting) {
            TestManager.jumpToLast(user)
        } else if (user.isShiftKeyDown) {
            TestManager.jumpRelative(user, 5)
        } else {
            TestManager.completeCurrent(user)
        }

        return InteractionResult.SUCCESS
    }
}
