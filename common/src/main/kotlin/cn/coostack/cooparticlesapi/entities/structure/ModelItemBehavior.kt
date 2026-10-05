package cn.coostack.cooparticlesapi.entities.structure

import cn.coostack.cooparticlesapi.event.CooEventBus
import cn.coostack.cooparticlesapi.entities.structure.events.StructureModelItemEatEvent
import cn.coostack.cooparticlesapi.entities.structure.events.StructureModelItemUseEvent
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.Tag
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.Level

/** 为真实物品附加模型事件与外观载荷，不代替其物品类、耐久或使用行为。 */
object ModelItemBehavior {
    /**
     * 判断真实物品是否携带模型外观；旧模型载体由自己的物品类处理。
     * 示例：`isSkinned(stack)`。
     * @param stack 待检查的物品，不会被修改
     * @return 是否有模型二进制载荷且不是旧模型载体
     */
    @JvmStatic
    fun isSkinned(stack: ItemStack): Boolean = !stack.`is`(StructureModels.HELD_MODEL) &&
        stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.contains("ModelData", Tag.TAG_BYTE_ARRAY.toInt()) == true

    /**
     * 校验潜行右键的模型重新放置开关，未开启时继续执行目标物品行为。
     * 示例：`canPlace(stack, player.level())`。
     * @param stack 当前手持物品
     * @param world 提供注册表的当前世界
     * @return 有效模型是否允许重新放置
     */
    fun canPlace(stack: ItemStack, world: Level): Boolean = isSkinned(stack) && read(stack, world)?.settings?.placeable == true

    /**
     * 将模型外观转移到桶、药水等原版使用后的替换栈，保留结果物品自身组件。
     * 示例：`transferAppearance(before, result)`。
     * @param before 使用前的独立副本，允许其原背包栈已被消耗
     * @param result 原版产生的结果栈；原地添加外观，不改变物品类型和数量
     */
    @JvmStatic
    fun transferAppearance(before: ItemStack, result: ItemStack) {
        if (!isSkinned(before) || result.isEmpty || before === result) return
        val source = before.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: return
        val target = result.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: CompoundTag()
        target.put("ModelData", source.get("ModelData")!!.copy())
        target.putString("model_event_id", source.getString("model_event_id"))
        source.get("attachment")?.let { target.put("attachment", it.copy()) }
        result.set(DataComponents.CUSTOM_DATA, CustomData.of(target))
        before.get(DataComponents.CUSTOM_NAME)?.let { result.set(DataComponents.CUSTOM_NAME, it) }
        result.set(DataComponents.MAX_STACK_SIZE, 1)
    }

    /**
     * 重新放置前保存真实物品的当前状态，避免再次收起时恢复耐久、弹药或已倒出的水。
     * 示例：`currentForPlacement(stack, world)`。
     * @param stack 即将被消费的模型物品；不会修改该栈
     * @param world 提供注册表的当前世界
     * @return 保留当前物品类型及组件的独立模型数据
     * @throws IllegalArgumentException 载荷无效或当前模拟栈超出大小限制
     */
    fun currentForPlacement(stack: ItemStack, world: Level): ModelItemData {
        val data = requireNotNull(read(stack, world)) { "模型物品数据无效" }
        if (!isSkinned(stack)) return data
        val sample = stack.copyWithCount(1)
        val custom = sample.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: CompoundTag()
        listOf("ModelData", "model_event_id", "attachment").forEach { custom.remove(it) }
        if (custom.isEmpty) sample.remove(DataComponents.CUSTOM_DATA)
        else sample.set(DataComponents.CUSTOM_DATA, CustomData.of(custom))
        sample.remove(DataComponents.CUSTOM_NAME)
        sample.set(DataComponents.MAX_STACK_SIZE, sample.item.components().get(DataComponents.MAX_STACK_SIZE) ?: 1)
        val properties = data.settings.itemProperties.copy(
            simulatedStack = sample.save(world.registryAccess()).toString(),
            boundItem = "", components = "{}", overrideFood = false
        )
        require(properties.valid()) { "当前模拟物品数据过大" }
        properties.simulation(world.registryAccess())
        return ModelItemData(data.structureName, data.snapshot, data.settings.copy(itemProperties = properties,
            edible = data.settings.edible && sample.has(DataComponents.FOOD)),
            data.health, data.drops)
    }

    /**
     * 在原物品成功使用后发出模型业务通知，不取消或重复执行原物品行为。
     * 示例：`used(before, world, player, hand)`。
     * @param before 使用前的独立副本
     * @param world 当前世界，仅服务端发出通知
     * @param player 使用玩家
     * @param hand 本次交互手
     */
    @JvmStatic
    fun used(before: ItemStack, world: Level, player: Player, hand: InteractionHand) {
        if (world.isClientSide || !isSkinned(before)) return
        val data = read(before, world) ?: return
        CooEventBus.call(StructureModelItemUseEvent(player, hand, before.copyWithCount(1), data))
    }

    /**
     * 原版完整食用结束后发出通知；不会额外消费、强制开始进食或改变创造模式。
     * 示例：`finished(before, world, user)`。
     * @param before 完成前的独立副本
     * @param world 当前世界，仅服务端发出通知
     * @param user 完成使用的实体，只有活着的非旁观玩家会触发食用事件
     */
    @JvmStatic
    fun finished(before: ItemStack, world: Level, user: LivingEntity) {
        if (world.isClientSide || user !is Player || !user.isAlive || user.isSpectator ||
            !isSkinned(before) || !before.has(DataComponents.FOOD)) return
        val data = read(before, world) ?: return
        CooEventBus.call(StructureModelItemEatEvent(user, user.usedItemHand, before.copyWithCount(1), data))
    }

    /** 不可信载荷失败时不接管交互，原物品仍可按原版处理。 */
    private fun read(stack: ItemStack, world: Level): ModelItemData? = runCatching {
        ModelItemData.fromNbt(requireNotNull(stack.get(DataComponents.CUSTOM_DATA)).copyTag(), world.registryAccess())
    }.getOrNull()
}
