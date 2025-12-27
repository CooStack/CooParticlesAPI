package cn.coostack.cooparticlesapi.event.events.entity.player

import cn.coostack.cooparticlesapi.event.api.EventCancelable
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState

class PlayerBlockBreakEvent(player: Player, val world: Level, val pos: BlockPos, val state: BlockState) :
    PlayerEvent(player), EventCancelable {
    override var isCancelled: Boolean = false
}