package cn.coostack.cooparticlesapi.entities.structure

import net.minecraft.world.item.ItemStack
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.component.CustomData
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.IntTag
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtOps
import net.minecraft.core.HolderLookup
import net.minecraft.resources.Identifier
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.Level
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * 可携带模型的独立载荷，不保存 UUID、世界位置、速度或死亡状态。
 * @property structureName 原结构标识，重新放置只使用快照，不读取模板
 * @property snapshot 当前模型的独占结构快照
 * @property settings 已校验的变换和拾取设置
 * @property health 收起时的当前生命值，不得超过设置中的最大生命
 * @property drops 按槽位排列的 27 个物品栈，调用方须传入独立副本
 */
class ModelItemData(
    val structureName: String,
    val snapshot: StructureSnapshot,
    val settings: StructureModelSettings,
    val health: Float,
    val drops: List<ItemStack>
) {
    init {
        require(Identifier.tryParse(structureName) != null) { "模型结构标识无效" }
        require(settings.valid() && settings.pickable) { "模型未允许拾取或物品名称无效" }
        require(health.isFinite() && health > 0F && health <= settings.health) { "模型生命数据无效" }
        require(drops.size == 27) { "模型掉落库存大小无效" }
    }

    /** 编码独立载荷，并限制包含库存的总数据量，超限时拒绝收起而不丢弃内容。 */
    fun toNbt(registries: HolderLookup.Provider): CompoundTag {
        val tag = CompoundTag()
        tag.putInt("Version", 1)
        tag.putString("Name", structureName)
        tag.put("Structure", snapshot.tag.copy())
        tag.put("Settings", settings.toNbt())
        tag.putFloat("Health", health)
        val items = ListTag()
        drops.forEachIndexed { slot, stack ->
            if (!stack.isEmpty) {
                val item = CompoundTag()
                item.putInt("Slot", slot)
                item.put(
                    "Stack",
                    ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack)
                        .getOrThrow { IllegalArgumentException("模型掉落物数据无法编码：$it") },
                )
                items.add(item)
            }
        }
        tag.put("Drops", items)
        require(tag.sizeInBytes() <= 1048576L) { "模型与掉落库存总数据超过 1 MiB，无法收起" }
        return tag
    }

    /** 合成绑定组件、覆盖值与模型载荷，名称和外观仍由模型控制。 */
    fun toStack(registries: HolderLookup.Provider): ItemStack =
        settings.itemProperties.createStack(registries, settings.edible).apply {
        // 原版自定义数据 Codec 会把整数列表转为整数数组；二进制封装保留结构和库存的原始 NBT 类型。
        val encoded = ByteArrayOutputStream().use {
            NbtIo.writeCompressed(toNbt(registries), it)
            it.toByteArray()
        }
        val envelope = get(DataComponents.CUSTOM_DATA)?.copyTag() ?: CompoundTag()
        envelope.putByteArray("ModelData", encoded)
        require(envelope.sizeInBytes() <= 1048576L) { "模型物品压缩数据过大" }
        set(DataComponents.CUSTOM_DATA, CustomData.of(envelope))
        set(DataComponents.CUSTOM_NAME, Component.literal(settings.itemName).withStyle { it.withItalic(false) })
        val encodedStack = ItemStack.CODEC.encodeStart(
            registries.createSerializationContext(NbtOps.INSTANCE),
            this,
        ).getOrThrow { IllegalArgumentException("完整模型物品无法编码：$it") }
        require(encodedStack.sizeInBytes() <= 1048576L) { "完整模型物品超过 1 MiB" }
    }

    /** 创建尚未加入世界的独立实体，先应用最大生命再恢复当前生命；展示与放置共用。 */
    fun createEntity(world: Level, origin: Vec3): StructureModelEntity {
        val entity = StructureModelEntity(StructureModels.ENTITY, world)
        entity.setPos(origin)
        if (world.isClientSide) entity.configurePreview(structureName, snapshot, settings)
        else entity.configure(structureName, snapshot, settings)
        entity.health = health
        drops.forEachIndexed { slot, stack -> entity.drops.setItem(slot, stack.copy()) }
        return entity
    }

    companion object {
        /** 解码不可信物品数据，先校验总量，再解码快照与每个库存槽；失败抛出异常且不消耗物品。 */
        fun fromNbt(tag: CompoundTag, registries: HolderLookup.Provider): ModelItemData {
            require(tag.sizeInBytes() <= 1048576L) { "模型物品数据超过 1 MiB" }
            val payload = if (tag.get("ModelData")?.id == Tag.TAG_BYTE_ARRAY) {
                ByteArrayInputStream(tag.getByteArray("ModelData").orElseThrow()).use {
                    NbtIo.readCompressed(it, NbtAccounter.create(1048576L))
                }
            } else tag
            require(payload.sizeInBytes() <= 1048576L) { "模型物品解压数据超过 1 MiB" }
            require(payload.getIntOr("Version", -1) == 1) { "模型物品数据版本无效" }
            val structure = payload.getCompoundOrEmpty("Structure").copy()
            // 兼容开发期已经生成的裸载荷，仅恢复模板要求的尺寸和方块坐标列表。
            structure.getIntArray("size").ifPresent { size ->
                structure.put("size", ListTag().apply { size.forEach { add(IntTag.valueOf(it)) } })
            }
            val blocks = structure.getList("blocks").orElseGet(::ListTag)
            for (index in 0 until blocks.size) {
                val block = blocks.getCompound(index).orElse(null) ?: continue
                block.getIntArray("pos").ifPresent { pos ->
                    block.put("pos", ListTag().apply { pos.forEach { add(IntTag.valueOf(it)) } })
                }
            }
            val snapshot = StructureSnapshot(registries, structure)
            val settings = StructureModelSettings.fromNbt(
                payload.getCompoundOrEmpty("Settings"),
                snapshot.defaultPivot,
            )
            val saved = payload.getList("Drops").orElseGet(::ListTag)
            require(saved.size <= 27) { "模型掉落库存过大" }
            val slots = mutableSetOf<Int>()
            val drops = MutableList(27) { ItemStack.EMPTY }
            for (index in 0 until saved.size) {
                val entry = saved.getCompound(index).orElseThrow {
                    IllegalArgumentException("模型掉落物数据无效")
                }
                val slot = entry.getIntOr("Slot", -1)
                require(slot in drops.indices && slots.add(slot)) { "模型掉落槽位无效或重复" }
                val stackTag = entry.get("Stack")
                    ?: throw IllegalArgumentException("模型掉落物数据无效")
                val stack = ItemStack.CODEC.parse(
                    registries.createSerializationContext(NbtOps.INSTANCE),
                    stackTag,
                ).getOrThrow { IllegalArgumentException("模型掉落物数据无效：$it") }
                require(!stack.isEmpty && stack.count <= stack.maxStackSize) { "模型掉落物数量无效" }
                drops[slot] = stack
            }
            return ModelItemData(
                payload.getStringOr("Name", ""),
                snapshot,
                settings,
                payload.getFloatOr("Health", 0F),
                drops,
            )
        }
    }
}
