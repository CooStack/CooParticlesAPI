package cn.coostack.cooparticlesapi.entities.structure

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import cn.coostack.cooparticlesapi.entities.collision.OrientedBox

/**
 * 原版结构 NBT 的静态快照，不放置世界方块，不生成保存的实体，也不运行方块实体逻辑。
 * 构造时完成输入复制、空气过滤、大小限制及几何校验，双方使用同一快照。
 * 示例：`StructureSnapshot(level.registryAccess(), template.save(CompoundTag()))`。
 * @param registries 方块状态解码环境
 * @param source 原版结构数据，不会被修改
 * @throws IllegalArgumentException 尺寸、坐标、调色板或同步预算无效
 */
class StructureSnapshot(registries: HolderLookup.Provider, source: CompoundTag) : BlockGetter {
    companion object {
        /** 通过原版模板建立独立快照，例如 `fromTemplate(level.registryAccess(), template)`。 */
        fun fromTemplate(registries: HolderLookup.Provider, template: StructureTemplate): StructureSnapshot =
            StructureSnapshot(registries, template.save(CompoundTag()))
    }
    /** 导出独立标签，编辑器和物品不能改写已发布快照。 */
    val tag: CompoundTag get() = toNbt()
    /** 独占且经过过滤的持久化数据。 */
    private val storedTag = source.copy()
    /** 只读局部方块索引，快照发布后不再修改。 */
    internal val states: Map<BlockPos, BlockState>
    /** 展示用方块实体标签；仅渲染器可读取，不能在世界中运行。 */
    internal val blockData: Map<BlockPos, CompoundTag>
    /** 原模板尺寸，包含模板中的空气边界。 */
    val size: BlockPos
    /** 默认支点为模板底部水平中心，而不是已占用方块的质心。 */
    val defaultPivot: Vec3 get() = Vec3(size.x / 2.0, 0.0, size.z / 2.0)
    /** 未变换的物理碰撞形状。 */
    internal val collision: List<AABB>
    /** 未变换的选取形状，包含可选取装饰和流体。 */
    internal val outline: List<AABB>

    init {
        val dimensions = storedTag.getList("size", 3)
        require(dimensions.size == 3) { "结构尺寸无效" }
        size = BlockPos(dimensions.getInt(0), dimensions.getInt(1), dimensions.getInt(2))
        require(listOf(size.x, size.y, size.z).all { it in 1..48 }) { "结构每轴尺寸须为 1 到 48 格" }
        val palettes = storedTag.getList("palettes", 9)
        val palette = if (palettes.isEmpty()) storedTag.getList("palette", 10) else palettes.getList(0)
        val lookup = registries.lookupOrThrow(Registries.BLOCK)
        val decoded = (0 until palette.size).map { NbtUtils.readBlockState(lookup, palette.getCompound(it)) }
        val blocks = storedTag.getList("blocks", 10)
        require(blocks.size <= 110592) { "结构数据过大" }
        val stateMap = linkedMapOf<BlockPos, BlockState>()
        val dataMap = linkedMapOf<BlockPos, CompoundTag>()
        val filtered = ListTag()
        val seen = hashSetOf<BlockPos>()
        for (index in 0 until blocks.size) {
            val block = blocks.getCompound(index)
            require(block.contains("state", 3)) { "结构缺少调色板下标" }
            val state = decoded.getOrNull(block.getInt("state"))
            require(state != null) { "结构调色板下标无效" }
            val coordinates = block.getList("pos", 3)
            require(coordinates.size == 3) { "结构坐标无效" }
            val pos = BlockPos(coordinates.getInt(0), coordinates.getInt(1), coordinates.getInt(2))
            require(pos.x in 0 until size.x && pos.y in 0 until size.y && pos.z in 0 until size.z) { "结构坐标越界" }
            require(seen.add(pos)) { "结构坐标重复" }
            if (state.isAir || state.`is`(Blocks.STRUCTURE_VOID)) continue
            stateMap[pos] = state
            if (block.contains("nbt", 10)) dataMap[pos] = block.getCompound("nbt").copy()
            filtered.add(block.copy())
            require(stateMap.size <= 8192) { "结构最多包含 8192 个非空气方块" }
        }
        require(stateMap.isNotEmpty()) { "结构中没有可显示方块" }
        states = stateMap
        blockData = dataMap
        storedTag.remove("entities")
        storedTag.put("blocks", filtered)
        require(storedTag.sizeInBytes() <= 1048576L) { "结构 NBT 估算内存超过 1 MiB" }
        collision = states.flatMap { (pos, state) -> state.getCollisionShape(this, pos).toAabbs().map { it.move(pos) } }
        outline = states.flatMap { (pos, state) ->
            val boxes = state.getShape(this, pos).toAabbs()
            if (boxes.isEmpty() && !state.fluidState.isEmpty) {
                listOf(AABB(0.0, 0.0, 0.0, 1.0, state.fluidState.getHeight(this, pos).toDouble(), 1.0).move(pos))
            } else boxes.map { it.move(pos) }
        }
        require(collision.size <= 16384 && outline.size <= 16384) { "结构形状过于复杂" }
    }

    /**
     * 导出快照的独立副本，不暴露内部可变 NBT。
     * 示例：`entityTag.put("Structure", snapshot.toNbt())`。
     * @return 过滤实体与空气后的原版结构标签
     */
    fun toNbt(): CompoundTag = storedTag.copy()

    /**
     * 计算首次放置的结构原点；无旋转原尺寸时按实际方块格对齐。
     * 示例：`snapshot.placementOrigin(settings, Vec3.atBottomCenterOf(pos))`。
     * @param settings 已校验的模型设置
     * @param target 目标格底面中心
     * @return 世界空间放置原点，不含用户手动偏移
     */
    fun placementOrigin(settings: StructureModelSettings, target: Vec3): Vec3 {
        if (settings.rotation.all { it == 0.0 } && settings.scale.all { it == 1.0 }) {
            val minX = states.keys.minOf { it.x }
            val minY = states.keys.minOf { it.y }
            val minZ = states.keys.minOf { it.z }
            val width = states.keys.maxOf { it.x } - minX + 1
            val depth = states.keys.maxOf { it.z } - minZ + 1
            return target.subtract(0.5 + minX + width / 2 + settings.pivotCompensation[0],
                minY + settings.pivotCompensation[1], 0.5 + minZ + depth / 2 + settings.pivotCompensation[2])
        }
        val unshifted = settings.transform.copy(offset = Vec3.ZERO)
        val boxes = (collision + outline).ifEmpty {
            listOf(AABB(0.0, 0.0, 0.0, size.x.toDouble(), size.y.toDouble(), size.z.toDouble()))
        }
        val bounds = boxes.map { OrientedBox(it, unshifted, Vec3.ZERO).bounds }.reduce(AABB::minmax)
        return target.subtract(bounds.center.x, bounds.minY, bounds.center.z)
    }

    override fun getBlockEntity(pos: BlockPos): BlockEntity? = null
    override fun getBlockState(pos: BlockPos): BlockState = states[pos] ?: Blocks.AIR.defaultBlockState()
    override fun getFluidState(pos: BlockPos): FluidState = getBlockState(pos).fluidState
    override fun getHeight(): Int = size.y
    override fun getMinBuildHeight(): Int = 0
}
