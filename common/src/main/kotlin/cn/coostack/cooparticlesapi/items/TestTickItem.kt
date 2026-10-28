package cn.coostack.cooparticlesapi.items

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.event.CooEventBus
import cn.coostack.cooparticlesapi.test.options.event.TestChildEvent
import cn.coostack.cooparticlesapi.test.options.event.TestEvent
import net.minecraft.network.chat.Component
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level


/** 提供 tick 与事件测试入口，保留公开无参构造并补齐注册身份。 */
class TestTickItem : Item(createProperties()) {
    companion object {
        /** 注册声明与构造属性共用的稳定物品路径。 */
        const val ID = "test_tick"

        /** 为无参构造器创建带注册键的独立属性。 */
        private fun createProperties(): Properties = Properties()
            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, ID)))
            .stacksTo(1)
    }

    override fun use(world: Level, user: Player, hand: InteractionHand): InteractionResult {
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
