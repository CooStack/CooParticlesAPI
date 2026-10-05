package cn.coostack.cooparticlesapi.event.events.client

import cn.coostack.cooparticlesapi.event.api.CooEvent
import cn.coostack.cooparticlesapi.event.api.EventCancelable
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.state.BlockState

/**
 * 原版方块选取轮廓绘制前在客户端渲染线程发布；取消只隐藏轮廓，不改变射线或交互。
 * @property cameraEntity 当前相机实体，不保证是本地玩家
 * @property pos 被选方块的世界坐标
 * @property state 被选方块的状态
 */
class ClientBlockOutlineEvent(val cameraEntity: Entity, val pos: BlockPos, val state: BlockState) :
    CooEvent(), EventCancelable {
    override var isCancelled = false
}
