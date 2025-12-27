package cn.coostack.cooparticlesapi.event.events.entity.player

import cn.coostack.cooparticlesapi.event.api.EventCancelable
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level

class ServerPlayerDeathEvent(player: Player, val world: Level, val source: DamageSource) :
    PlayerEvent(player), EventCancelable {
    override var isCancelled: Boolean = false

}