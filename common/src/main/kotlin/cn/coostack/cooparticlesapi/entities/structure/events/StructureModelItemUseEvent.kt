package cn.coostack.cooparticlesapi.entities.structure.events

import cn.coostack.cooparticlesapi.event.api.CooEvent
import cn.coostack.cooparticlesapi.entities.structure.ModelItemData
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.InteractionHand
import net.minecraft.nbt.CompoundTag

/**
 * 有效模型物品右键时在逻辑服务端同步发布的通知，可用 EventHandler 监听。
 * 未模拟物品时沿用模型自身入口；模拟物品在原物品空气、方块或实体使用方法成功后通知。
 * 模拟物品可能已经消费或更换手持栈，stack 始终是使用前的独立副本。
 * 潜行右键方块的放置分支不发布本事件；此事件不可取消。
 * 修改副本不会更改背包或默认行为，模型识别可使用 model.structureName 和 model.settings.itemName。
 * @property player 本次使用者，引用仅供当前服务端线程使用
 * @property hand 实际交互的手
 * @property stack 使用前的独立单件物品副本，不是玩家背包中的栈
 * @property model 本次解码的独立模型数据，包含变换、当前生命和掉落库存
 */
class StructureModelItemUseEvent(
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
