package cn.coostack.cooparticlesapi.items

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World

class TestTickItem : Item(Settings().maxCount(1)) {

    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
        if (world.isClient) return super.use(world, user, hand)
        val server = world.server!!
        val tickManager = server.tickManager
        val frozen = tickManager.isFrozen
        if (frozen) {
            if (tickManager.isSprinting) {
                tickManager.stopSprinting()
            }
            if (tickManager.isStepping) {
                tickManager.stopStepping()
            }
        }

        tickManager.isFrozen = !frozen
        user.sendMessage(
            Text.literal(
                "时停 ${!frozen}"
            )
        )
        return super.use(world, user, hand)
    }

}