package cn.coostack.cooparticlesapi.entities.structure

import cn.coostack.cooparticlesapi.event.CooEventBus
import cn.coostack.cooparticlesapi.entities.structure.events.StructureModelItemEatEvent
import cn.coostack.cooparticlesapi.entities.structure.events.StructureModelItemUseEvent
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.server.level.ServerLevel
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.Level

/** 携带结构快照的单件物品，收起不触发死亡，成功放置后包括创造模式在内均消耗一件。 */
class HeldStructureModelItem(settings: Properties) : Item(settings) {
    /** 普通右键只触发业务事件；食用开关开启时按原版进食时长开始使用，满饥饿值也可用。 */
    override fun use(world: Level, user: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = user.getItemInHand(hand)
        if (!user.isAlive || user.isSpectator || user.cooldowns.isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack)
        }
        val component = stack.get(DataComponents.CUSTOM_DATA) ?: return InteractionResultHolder.fail(stack)
        val data = runCatching { ModelItemData.fromNbt(component.copyTag(), world.registryAccess()) }.getOrElse {
            user.displayClientMessage(Component.literal(it.message ?: "模型物品数据无效"), true)
            return InteractionResultHolder.fail(stack)
        }
        if (data.settings.edible) {
            if (!stack.has(DataComponents.FOOD)) return InteractionResultHolder.fail(stack)
            user.startUsingItem(hand)
        }
        if (!world.isClientSide) {
            CooEventBus.call(StructureModelItemUseEvent(user, hand, stack.copyWithCount(1), data))
        }
        return InteractionResultHolder.sidedSuccess(stack, world.isClientSide)
    }

    /** 完整进食后才消费并发布完成通知，提前停止使用不会进入此方法。 */
    override fun finishUsingItem(stack: ItemStack, world: Level, user: LivingEntity): ItemStack {
        if (world.isClientSide || user !is Player || !user.isAlive || user.isSpectator || stack.isEmpty) return stack
        val component = stack.get(DataComponents.CUSTOM_DATA) ?: return stack
        val data = runCatching { ModelItemData.fromNbt(component.copyTag(), world.registryAccess()) }.getOrNull() ?: return stack
        if (!data.settings.edible || !stack.has(DataComponents.FOOD)) return stack
        val hand = user.usedItemHand
        val consumed = stack.copyWithCount(1)
        val result = super.finishUsingItem(stack, world, user)
        // 与重新放置一样，创造模式也转移掉这份载荷，不能保留一个已消费的容器。
        if (user.isCreative) result.shrink(1)
        CooEventBus.call(StructureModelItemEatEvent(user, hand, consumed, data))
        return result
    }

    /** 仅在目标位置权限合法且实体生成成功后消耗物品；失败保留原物品。 */
    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player ?: return InteractionResult.FAIL
        if (!player.isShiftKeyDown) return use(context.level, player, context.hand).result
        val component = context.itemInHand.get(DataComponents.CUSTOM_DATA) ?: return InteractionResult.FAIL
        val world = context.level
        val pos = context.clickedPos.relative(context.clickedFace)
        if (!player.isAlive || player.isSpectator || player.cooldowns.isOnCooldown(this) ||
            !player.abilities.mayBuild) return InteractionResult.FAIL
        val result = runCatching {
            val data = ModelItemBehavior.currentForPlacement(context.itemInHand, world)
            require(data.settings.placeable) { "此模型未开启重新放置能力" }
            require(world.worldBorder.isWithinBounds(pos) && world.isInWorldBounds(pos) &&
                world.hasChunkAt(pos) && world.mayInteract(player, pos) &&
                player.mayUseItemAt(pos, context.clickedFace, context.itemInHand)) { "此处不允许放置模型" }
            if (world is ServerLevel) {
                val target = Vec3.atBottomCenterOf(pos)
                val entity = data.createEntity(world, data.snapshot.placementOrigin(data.settings, target))
                check(world.addFreshEntity(entity)) { "无法放置模型" }
            }
        }
        if (result.isFailure) {
            player.displayClientMessage(Component.literal(result.exceptionOrNull()?.message ?: "模型物品数据无效"), true)
            return InteractionResult.FAIL
        }
        if (world.isClientSide) return InteractionResult.SUCCESS
        context.itemInHand.shrink(1)
        // 原版创造模式会恢复旧栈计数；脱离已消费的手持引用，避免生成实体后物品复活。
        if (context.itemInHand.isEmpty) player.setItemInHand(context.hand, ItemStack.EMPTY)
        return InteractionResult.CONSUME
    }

    /** 空手时直接拿到交互手上，否则进入背包；交付成功后才清空旧库存并移除实体，不触发死亡。 */
    fun capture(model: StructureModelEntity, player: Player, hand: InteractionHand): InteractionResult {
        if (!model.canPickUp(player) || !player.isShiftKeyDown ||
            model.isPassenger() || model.isVehicle() || model.isLeashed) return InteractionResult.FAIL
        val emptyHand = player.getItemInHand(hand).isEmpty
        if (!emptyHand && player.inventory.freeSlot < 0) {
            player.displayClientMessage(Component.literal("背包已满，无法收起模型"), true)
            return InteractionResult.FAIL
        }
        if (model.level().isClientSide) return InteractionResult.SUCCESS
        val snapshot = model.snapshot ?: return InteractionResult.FAIL
        val result = runCatching {
            val data = ModelItemData(model.structureName, snapshot, model.settings, model.health,
                (0 until model.drops.containerSize).map { model.drops.getItem(it).copy() })
            data.toStack(model.registryAccess())
        }
        val stack = result.getOrElse {
            player.displayClientMessage(Component.literal(it.message ?: "无法收起模型"), true)
            return InteractionResult.FAIL
        }
        if (emptyHand) player.setItemInHand(hand, stack)
        else if (!player.inventory.add(stack)) return InteractionResult.FAIL
        model.drops.clearContent()
        model.discard()
        return InteractionResult.CONSUME
    }
}
