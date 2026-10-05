package cn.coostack.cooparticlesapi.entities.structure.events

import cn.coostack.cooparticlesapi.event.api.CooEvent
import cn.coostack.cooparticlesapi.entities.structure.ModelItemData
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.InteractionHand
import net.minecraft.nbt.CompoundTag

/**
 * 完整进食后在逻辑服务端同步发布一次的不可取消通知，营养与原生效果已由物品处理。
 * 用 EventHandler 监听；提前松开或无效数据不发布。模拟物品保留原食用能力及创造模式消费规则。
 * 非模拟载体需要开启食用；不运行实体死亡掉落，监听器可以补充业务效果。
 * @property player 完成进食的玩家，引用仅供当前服务端线程使用
 * @property hand 本次进食使用的手
 * @property stack 消费前的独立单件物品副本，已不属于背包
 * @property model 消费前的完整模型数据，包含原掉落库存；不会自动交付这些物品
 */
class StructureModelItemEatEvent(
    val player: Player,
    val hand: InteractionHand,
    val stack: ItemStack,
    val model: ModelItemData
) : CooEvent() {
    /** 编辑器指定的业务标识，不依赖可变的显示名称。 */
    val eventId: String get() = model.settings.itemProperties.eventId
    /** 本事件可独立修改的附加数据，修改不写回物品。 */
    val attachment: CompoundTag = model.settings.itemProperties.attachmentData()
}
