package cn.coostack.cooparticlesapi.entities.structure.editor

import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.core.BlockPos
import java.util.UUID

/**
 * 仅由服务器线程持有的玩家选点，不保留玩家或世界对象。
 * @property dimension 选区所属维度，换维度后不可复用
 */
internal class StructureEditorSession(val dimension: ResourceLocation) {
    /** 第一角点，左键覆盖，不受第二点是否存在影响。 */
    var first: BlockPos? = null
    /** 第二角点，右键覆盖。 */
    var second: BlockPos? = null
    /** 每次选点后更新，拒绝旧窗口的保存请求及重复转换。 */
    var revision: UUID = UUID.randomUUID()
    /** 保存界面的草稿，只含服务器验证过的字段。 */
    var draft: CompoundTag = CompoundTag()
    /** 两个点齐全时才提供范围。 */
    val selection: StructureSelection?
        get() = first?.let { a -> second?.let { b -> StructureSelection(a, b) } }

    /** 生成与调用方独立的同步数据，消息不会泄露可变草稿。 */
    fun packet(action: String): CompoundTag = CompoundTag().apply {
        putString("Action", action)
        putString("Dimension", dimension.toString())
        putUUID("Revision", revision)
        first?.let { putLong("First", it.asLong()) }
        second?.let { putLong("Second", it.asLong()) }
        put("Draft", draft.copy())
    }
}
