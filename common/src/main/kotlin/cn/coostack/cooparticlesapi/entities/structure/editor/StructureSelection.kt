package cn.coostack.cooparticlesapi.entities.structure.editor

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB

/**
 * 两个包含端点的方块坐标构成的选区，不包含客户端平滑动画状态。
 * @property first 左键确定的第一点
 * @property second 右键确定的第二点
 */
data class StructureSelection(val first: BlockPos, val second: BlockPos) {
    /** 结构模板使用的最小方块坐标。 */
    val origin: BlockPos = BlockPos(
        minOf(first.x, second.x), minOf(first.y, second.y), minOf(first.z, second.z)
    )
    /** 包含两个端点的整格尺寸；同一点构成一格，而不是零体积。 */
    val size: BlockPos = BlockPos(
        maxOf(first.x, second.x) - origin.x + 1,
        maxOf(first.y, second.y) - origin.y + 1,
        maxOf(first.z, second.z) - origin.z + 1
    )
    /** 最大边界位于最后一格的外侧，供世界线框使用。 */
    val box: AABB
        get() = AABB(origin.x.toDouble(), origin.y.toDouble(), origin.z.toDouble(),
            (origin.x + size.x).toDouble(), (origin.y + size.y).toDouble(), (origin.z + size.z).toDouble())

    /** 返回是否符合原版结构方块每轴最多 48 格的保存范围。 */
    fun valid(): Boolean = size.x in 1..48 && size.y in 1..48 && size.z in 1..48

    /**
     * 使用保存界面的相对位置和尺寸调整范围，不改变原始选点。
     * @param offset 相对于选区最小角的偏移，每轴 -48 到 48 格
     * @param dimensions 保存尺寸，每轴 1 到 48 格
     * @return 调整后的包含端点选区
     * @throws IllegalArgumentException 偏移或尺寸超出原版范围
     */
    fun adjusted(offset: BlockPos, dimensions: BlockPos): StructureSelection {
        require(listOf(offset.x, offset.y, offset.z).all { it in -48..48 }) { "相对位置须为 -48 到 48" }
        require(listOf(dimensions.x, dimensions.y, dimensions.z).all { it in 1..48 }) { "结构每轴尺寸须为 1–48 格" }
        val start = origin.offset(offset)
        return StructureSelection(start, start.offset(dimensions).offset(-1, -1, -1))
    }
}
