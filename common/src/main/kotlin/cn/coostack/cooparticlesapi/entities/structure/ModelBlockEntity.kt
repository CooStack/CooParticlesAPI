package cn.coostack.cooparticlesapi.entities.structure

import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.ContainerHelper
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.core.HolderLookup
import net.minecraft.resources.ResourceLocation
import net.minecraft.core.NonNullList
import net.minecraft.core.BlockPos

/** 保存模型方块的配置、独立结构快照和转入库存，不在保存时生成模型实体。 */
class ModelBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(StructureModels.BLOCK_ENTITY, pos, state) {
    /** 空名称表示尚未选择结构，只有这种方块可以接收模型物品。 */
    var structureName = ""
        private set
    /** 方块的有效编辑设置，未选择结构也可保存。 */
    var settings = StructureModelSettings()
        private set
    /** 转移物品时保留当前生命；后续保存仅按新的最大生命上限裁剪。 */
    var storedHealth = 20F
        private set
    /** 服务端保存的结构副本，客户端请求不得直接写入。 */
    private var structure: CompoundTag? = null
    /** 原始编辑草稿，与已验证的模型配置和真实库存隔离。 */
    private var editorDraft: CompoundTag? = null
    /** 草稿选择的结构可成功加载时保留服务端快照，名称无效不影响草稿保存。 */
    private var draftStructure: CompoundTag? = null
    /** 仅用于判断是否可以接收物品，未验证的草稿名称不能作为注册标识使用。 */
    val hasSelectedStructure: Boolean
        get() = structureName.isNotEmpty() || editorDraft?.getString("Name")?.isNotBlank() == true
    /** 库存始终由方块独占；转换前转交实体，破坏时散落，均不得复制。 */
    val drops = object : SimpleContainer(27) {
        override fun setChanged() {
            super.setChanged()
            this@ModelBlockEntity.setChanged()
        }

        override fun stillValid(player: Player): Boolean {
            val currentWorld = level ?: return false
            return !isRemoved && currentWorld.getBlockEntity(pos) === this@ModelBlockEntity &&
                player.isAlive && !player.isSpectator && player.abilities.mayBuild &&
                player.canInteractWithBlock(pos, 1.0) && currentWorld.mayInteract(player, pos)
        }
    }

    /** 根据当前世界注册表解码独立快照；空配置没有预览内容。 */
    fun snapshot(registries: HolderLookup.Provider): StructureSnapshot? =
        structure?.let { StructureSnapshot(registries, it.copy()) }

    /** 返回独立的编辑草稿；调用方修改不会改变方块存档。 */
    fun draft(): CompoundTag? = editorDraft?.copy()

    /** 只限制传输大小，不要求草稿能够转换；快照仅接受服务端成功解析的结构。 */
    fun saveDraft(state: CompoundTag, snapshot: StructureSnapshot?) {
        require(state.sizeInBytes() <= 524288L) { "编辑草稿超过 512 KiB" }
        require(snapshot == null || snapshot.tag.sizeInBytes() <= 1048576L)
        editorDraft = state.copy()
        draftStructure = snapshot?.tag?.copy()
        setChanged()
    }

    /** 按请求名称读取服务端保存的快照，不信任草稿提交的任何结构内容。 */
    fun snapshotFor(name: String, registries: HolderLookup.Provider): StructureSnapshot? {
        if (structureName == name) return snapshot(registries)
        val draftName = editorDraft?.getString("Name")?.trim()?.let { ResourceLocation.tryParse(it) }
        return if (draftName?.toString() == name) draftStructure?.let { StructureSnapshot(registries, it.copy()) } else null
    }

    /** 保存已验证的配置，不改变已有库存；空名称只允许空快照。 */
    fun configure(name: String, snapshot: StructureSnapshot?, options: StructureModelSettings) {
        require(options.valid())
        require((name.isEmpty() && snapshot == null) || (ResourceLocation.tryParse(name) != null && snapshot != null))
        require(snapshot == null || snapshot.tag.sizeInBytes() <= 1048576L)
        val health = if (structureName.isEmpty()) options.health else storedHealth.coerceAtMost(options.health)
        structureName = name
        structure = snapshot?.tag?.copy()
        settings = options
        storedHealth = health
        setChanged()
    }

    /** 转入已完整校验的模型；只接收空方块，先准备所有副本再修改字段。 */
    fun receive(data: ModelItemData, registries: HolderLookup.Provider) {
        require(!hasSelectedStructure && drops.isEmpty) { "模型方块已有结构或库存，不能覆盖" }
        data.toNbt(registries)
        val snapshot = data.snapshot.tag.copy()
        val inventory = data.drops.map { it.copy() }
        structureName = data.structureName
        structure = snapshot
        settings = data.settings
        storedHealth = data.health
        editorDraft = null
        draftStructure = null
        inventory.forEachIndexed { index, stack -> drops.setItem(index, stack) }
        setChanged()
    }

    /** 持久化真实库存和结构快照，重进世界后不依赖模板文件仍然存在。 */
    override fun saveAdditional(nbt: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.saveAdditional(nbt, registryLookup)
        nbt.putString("Name", structureName)
        nbt.put("Settings", settings.toNbt())
        nbt.putFloat("Health", storedHealth)
        structure?.let { nbt.put("Structure", it.copy()) }
        editorDraft?.let { nbt.put("EditorDraft", it.copy()) }
        draftStructure?.let { nbt.put("DraftStructure", it.copy()) }
        val stacks = NonNullList.withSize(27, ItemStack.EMPTY)
        repeat(27) { stacks[it] = drops.getItem(it) }
        ContainerHelper.saveAllItems(nbt, stacks, registryLookup)
    }

    /** 空旧方块采用默认设置；有快照的存档必须通过与编辑相同的结构校验。 */
    override fun loadAdditional(nbt: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.loadAdditional(nbt, registryLookup)
        val options = if (nbt.contains("Settings")) StructureModelSettings.fromNbt(nbt.getCompound("Settings")) else StructureModelSettings()
        val name = nbt.getString("Name")
        val snapshot = if (name.isNotEmpty()) StructureSnapshot(registryLookup, nbt.getCompound("Structure").copy()) else null
        configure(name, snapshot, options)
        storedHealth = if (nbt.contains("Health")) nbt.getFloat("Health") else options.health
        require(storedHealth.isFinite() && storedHealth > 0F && storedHealth <= options.health)
        val stacks = NonNullList.withSize(27, ItemStack.EMPTY)
        ContainerHelper.loadAllItems(nbt, stacks, registryLookup)
        repeat(27) { drops.setItem(it, stacks[it]) }
        editorDraft = if (nbt.contains("EditorDraft")) nbt.getCompound("EditorDraft").copy() else null
        draftStructure = if (nbt.contains("DraftStructure")) nbt.getCompound("DraftStructure").copy() else null
    }
}
