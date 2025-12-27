package cn.coostack.cooparticlesapi.event.events.entity.player

import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

/**
 *
 * @property original 破坏之前的物品
 * @constructor
 *
 * @param player
 */
class PlayerItemDestroyEvent(player: Player, val original: ItemStack) : PlayerEvent(player)