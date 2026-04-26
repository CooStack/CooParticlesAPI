package cn.coostack.cooparticlesapi.items

import cn.coostack.cooparticlesapi.event.CooEventBus
import cn.coostack.cooparticlesapi.test.options.event.TestChildEvent
import cn.coostack.cooparticlesapi.test.options.event.TestEvent
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level


class TestTickItem : Item(Item.Properties().stacksTo(1)) {

    override fun use(world: Level, user: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        CooEventBus.call(
            TestChildEvent(user, "sb")
        )

        if (world.isClientSide) return super.use(world, user, hand)

        return super.use(world, user, hand)
    }

    fun tickFrozen(world: ServerLevel, user: ServerPlayer) {
        val server = world.server!!
        val tickManager = server.tickRateManager()
        val frozen = tickManager.isFrozen

        if (frozen) {
            if (tickManager.isSprinting) {
                tickManager.stopSprinting()
            }
            if (tickManager.isSteppingForward) {
                tickManager.stopStepping()
            }
        }

        tickManager.isFrozen = !frozen
        user.sendSystemMessage(
            Component.literal(
                "时停 ${!frozen}"
            )
        )
    }

}
