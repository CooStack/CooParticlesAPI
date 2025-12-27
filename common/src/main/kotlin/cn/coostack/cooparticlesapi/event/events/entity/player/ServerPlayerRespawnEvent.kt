package cn.coostack.cooparticlesapi.event.events.entity.player

import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level

class ServerPlayerRespawnEvent(player: Player, val world: Level) :
    PlayerEvent(player)