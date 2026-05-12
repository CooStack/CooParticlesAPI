package cn.coostack.cooparticlesapi.event.events.entity.player

import cn.coostack.cooparticlesapi.event.events.entity.EntityUnloadType
import net.minecraft.world.entity.player.Player

class PlayerDisconnectEvent @JvmOverloads constructor(
    player: Player,
    val unloadType: EntityUnloadType = EntityUnloadType.QUIT
) : PlayerEvent(player)
