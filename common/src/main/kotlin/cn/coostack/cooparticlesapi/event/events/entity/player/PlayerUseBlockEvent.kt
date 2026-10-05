package cn.coostack.cooparticlesapi.event.events.entity.player

import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.BlockHitResult

/**
 * 玩家方块交互前同步发布，两端独立处理，不自动同步。
 * @param player 当前玩家
 * @property hand 交互手
 * @property hit 本次命中
 */
class PlayerUseBlockEvent(player: Player, val hand: InteractionHand, val hit: BlockHitResult) : PlayerEvent(player) {
    /** PASS 继续原版，其他结果跳过原版交互。 */
    var result: InteractionResult = InteractionResult.PASS
}
