package cn.coostack.cooparticlesapi.entities.structure

import net.minecraft.core.component.DataComponentPatch
import net.minecraft.core.component.DataComponents
import net.minecraft.world.food.FoodProperties
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.TagParser
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.HolderLookup
import net.minecraft.resources.ResourceLocation

/**
 * 模型物品的组件设置；旧绑定保留组件继承，新模拟栈保留真实物品类型及其行为。
 * @property eventId 事件业务标识，留空表示未指定
 * @property boundItem 默认组件来源的物品 ID，留空表示不绑定
 * @property overrideFood 是否用编辑值覆盖继承的食物数值
 * @property nutrition 恢复的饱食度点数
 * @property saturation 实际恢复的饱和度点数，不是倍率
 * @property eatSeconds 进食时长，单位秒
 * @property components 原版 ComponentChanges 格式的 SNBT，允许添加或删除组件
 * @property attachment 事件附加数据的 SNBT，不注册 Fabric Attachment 类型
 * @property simulatedStack 单件目标物品的完整 SNBT，空字符串表示使用原有模型载体
 */
data class ModelItemProperties(
    val eventId: String = "",
    val boundItem: String = "",
    val overrideFood: Boolean = false,
    val nutrition: Int = 0,
    val saturation: Float = 0F,
    val eatSeconds: Float = 1.6F,
    val components: String = "{}",
    val attachment: String = "{}",
    val simulatedStack: String = ""
) {
    /** 轻量校验不访问注册表，供设置和网络边界使用。 */
    fun valid(): Boolean = listOf(eventId, boundItem).all {
        it.length <= 256 && (it.isEmpty() || ResourceLocation.tryParse(it) != null)
    } && nutrition in 0..1000 && saturation.isFinite() && saturation in 0F..1000F &&
        eatSeconds.isFinite() && eatSeconds in 0.05F..120F &&
        components.length <= 8192 && attachment.length <= 8192 && simulatedStack.length <= 65536

    /** 解码复制槽的独立单件物品；禁止将模型再次嵌入模型，避免递归载荷。 */
    fun simulation(registries: HolderLookup.Provider): ItemStack {
        if (simulatedStack.isEmpty()) return ItemStack.EMPTY
        val tag = TagParser.parseTag(simulatedStack)
        require(tag.sizeInBytes() <= 65536L) { "模拟物品数据超过 64 KiB" }
        val stack = ItemStack.parse(registries, tag).orElseThrow { IllegalArgumentException("模拟物品无效") }
        require(!stack.isEmpty && stack.count == 1 && !stack.`is`(StructureModels.HELD_MODEL)) { "请选择一个非模型物品" }
        require(stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.contains("ModelData") != true) { "不能模拟另一个模型" }
        ItemStack.validateComponents(stack.components).getOrThrow { IllegalArgumentException(it) }
        return stack
    }

    /** 附加数据每次返回独立副本，监听器修改不会污染模型设置。 */
    fun attachmentData(): CompoundTag = TagParser.parseTag(attachment)

    /** 构建并校验组件；名称、结构载荷和单件堆叠由模型自身管理，食用能力由开关控制。 */
    fun createStack(registries: HolderLookup.Provider, edible: Boolean): ItemStack {
        require(valid()) { "物品属性格式或数值无效" }
        val simulated = simulation(registries)
        val stack = if (simulated.isEmpty) ItemStack(StructureModels.HELD_MODEL) else simulated
        if (simulated.isEmpty && boundItem.isNotEmpty()) {
            val id = ResourceLocation.parse(boundItem)
            require(BuiltInRegistries.ITEM.containsKey(id)) { "找不到绑定物品：$boundItem" }
            val item = BuiltInRegistries.ITEM.get(id)
            require(item !== Items.AIR && item !== StructureModels.HELD_MODEL) { "不能绑定空气或模型物品本身" }
            stack.applyComponents(item.components())
        }
        val changes = DataComponentPatch.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),
            TagParser.parseTag(components)).getOrThrow { IllegalArgumentException("组件无效：$it") }
        val reserved = setOf(DataComponents.CUSTOM_NAME, DataComponents.ITEM_NAME,
            DataComponents.MAX_STACK_SIZE, DataComponents.CUSTOM_MODEL_DATA)
        require(changes.entrySet().none { it.key in reserved }) { "名称、堆叠数量和外观由模型管理" }
        stack.applyComponents(changes)
        stack.set(DataComponents.MAX_STACK_SIZE, 1)
        stack.remove(DataComponents.CUSTOM_MODEL_DATA)
        stack.remove(DataComponents.ITEM_NAME)
        stack.remove(DataComponents.CUSTOM_NAME)
        val custom = stack.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: CompoundTag()
        require(!custom.contains("ModelData")) { "ModelData 是模型保留字段" }
        custom.put("attachment", attachmentData())
        custom.putString("model_event_id", eventId)
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom))
        if (edible) {
            val inherited = stack.get(DataComponents.FOOD)
                ?: FoodProperties.Builder().alwaysEdible().build()
            val manual = overrideFood || !stack.has(DataComponents.FOOD)
            stack.set(DataComponents.FOOD, FoodProperties(
                if (manual) nutrition else inherited.nutrition(),
                if (manual) saturation else inherited.saturation(),
                true,
                if (manual) eatSeconds else inherited.eatSeconds(),
                inherited.usingConvertsTo(), inherited.effects()
            ))
        } else if (simulated.isEmpty) stack.remove(DataComponents.FOOD)
        ItemStack.validateComponents(stack.components).getOrThrow { IllegalArgumentException(it) }
        require(stack.save(registries).sizeInBytes() <= 65536L) { "物品组件数据超过 64 KiB" }
        return stack
    }

    /** 保存为独立复合标签，字符串 SNBT 避免同步时改变列表类型。 */
    fun toNbt(): CompoundTag = CompoundTag().apply {
        putString("EventId", eventId)
        putString("BoundItem", boundItem)
        putBoolean("OverrideFood", overrideFood)
        putInt("Nutrition", nutrition)
        putFloat("Saturation", saturation)
        putFloat("EatSeconds", eatSeconds)
        putString("Components", components)
        putString("Attachment", attachment)
        putString("SimulatedStack", simulatedStack)
    }

    companion object {
        /** 旧数据缺少物品属性时保持零营养、默认进食时长及无绑定。 */
        fun fromNbt(tag: CompoundTag): ModelItemProperties = ModelItemProperties(
            tag.getString("EventId"), tag.getString("BoundItem"), tag.getBoolean("OverrideFood"),
            tag.getInt("Nutrition"), tag.getFloat("Saturation"),
            if (tag.contains("EatSeconds")) tag.getFloat("EatSeconds") else 1.6F,
            if (tag.contains("Components")) tag.getString("Components") else "{}",
            if (tag.contains("Attachment")) tag.getString("Attachment") else "{}",
            tag.getString("SimulatedStack")
        )
    }
}
