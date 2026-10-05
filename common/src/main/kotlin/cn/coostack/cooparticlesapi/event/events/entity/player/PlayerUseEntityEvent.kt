package cn.coostack.cooparticlesapi.event.events.entity.player

import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player

/**
 * 玩家实体交互前同步发布，两端分别处理，监听器须检查服务端权限。
 * @param player 交互玩家
 * @property hand 交互手
 * @property target 目标实体
 */
class PlayerUseEntityEvent(player: Player, val hand: InteractionHand, val target: Entity) : PlayerEvent(player) {
    /** PASS 继续原版，其他结果代表已经接管交互。 */
    var result: InteractionResult = InteractionResult.PASS
}
