package cn.coostack.cooparticlesapi.entities.collision

import cn.coostack.cooparticlesapi.extend.asRelative
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.VoxelShape
import kotlin.math.abs
import kotlin.random.Random

/**
 * 有向框并集的不可变几何，缓存空间树和面积分布；可脱离实体独立用于碰撞或粒子采样。
 * 在实体变换变化时重建，不要每个粒子都构建一次。
 * 示例：`CollisionGeometry(boxes).sampleSurfacePoints(200, origin)`。
 * @param boxes 世界空间有向框集合，构造时复制集合，允许为空
 */
class CollisionGeometry(boxes: Collection<OrientedBox>) {
    /** 独占的框列表；各框不可变。 */
    internal val parts: List<OrientedBox> = boxes.toList()
    /** 世界空间总体范围，空几何没有范围。 */
    val bounds: AABB? = parts.map { it.bounds }.reduceOrNull(AABB::minmax)
    /** 包含所有框的空间查询树。 */
    private val tree: CollisionBoxTree? = if (parts.isEmpty()) null else CollisionBoxTree(parts.indices.toList(), parts)
    /** 与有向框一一对应的运动形状，避免逐 tick 重建。 */
    private val shapes: List<VoxelShape> = parts.map { OrientedVoxelShape(it) }
    /** 每个框六个面的累计世界面积，采样包含缩放后的真实面积。 */
    private val faceAreas: DoubleArray = DoubleArray(parts.size * 6)

    init {
        var total = 0.0
        for ((index, box) in parts.withIndex()) for (face in 0..5) {
            val axis = face / 2
            total += 4.0 * box.half[(axis + 1) % 3] * box.half[(axis + 2) % 3]
            faceAreas[index * 6 + face] = total
        }
        require(total.isFinite()) { "碰撞表面积溢出" }
    }

    /**
     * 查询真正穿入范围的运动形状，只供原版逐轴移动使用。
     * 示例：`geometry.collisionShapes(player.boundingBox.expandTowards(movement))`。
     * @param query 世界空间范围
     * @return 不包含外包框空角的形状列表，不得用布尔体素合并代替运动求解
     */
    fun collisionShapes(query: AABB): List<VoxelShape> =
        candidates(query).filter { parts[it].intersects(query) }.map { shapes[it] }

    /**
     * 返回并集的最近射线交点，孔洞不会被外包框遮挡。
     * 示例：`geometry.raycast(start, end)`。
     * @param start 世界起点
     * @param end 世界终点
     * @return 最近世界命中点，未命中或几何为空时为空
     */
    fun raycast(start: Vec3, end: Vec3): Vec3? = candidates(AABB(start, end).inflate(1.0e-7))
        .mapNotNull { parts[it].raycast(start, end) }.minByOrNull { it.distanceToSqr(start) }

    /**
     * 在碰撞并集的暴露表面按世界面积随机采样，直接返回现有粒子 API 的点构建器。
     * 保留孔洞内壁，剔除接触内部面；重合外表面不会重复加权。
     * 这是调用瞬间的静态点集，不会自动跟随实体；空几何或零数量返回空构建器。
     * 示例：`val points = geometry.sampleSurfacePoints(256, emitterPos).create()`。
     * @param count 所需点数，范围 0 到 100000
     * @param relativeTo 从世界点减去的原点；零值表示导出世界坐标
     * @param maxAttempts 最大候选次数，范围 0 到 10000000，防止高度重叠几何阻塞游戏线程
     * @return 恰好包含 count 个相对点的独立构建器，空几何除外
     * @throws IllegalArgumentException 参数无效
     * @throws IllegalStateException 达到候选预算仍未凑足点数；不会静默返回不完整点集
     */
    fun sampleSurfacePoints(count: Int, relativeTo: Vec3 = Vec3.ZERO, maxAttempts: Int = (count * 128).coerceAtMost(10000000)): PointsBuilder {
        require(count in 0..100000 && maxAttempts in 0..10000000)
        require(listOf(relativeTo.x, relativeTo.y, relativeTo.z).all { it.isFinite() })
        val result = PointsBuilder()
        if (count == 0 || parts.isEmpty()) return result
        val totalArea = faceAreas.last()
        var accepted = 0
        repeat(maxAttempts) {
            // 面积加权提议再拒绝内部面，接受分布就是暴露表面的面积分布。
            val target = Random.nextDouble(totalArea)
            var low = 0
            var high = faceAreas.lastIndex
            while (low < high) {
                val middle = (low + high) ushr 1
                if (target < faceAreas[middle]) high = middle else low = middle + 1
            }
            val box = parts[low / 6]
            val face = low % 6
            val axis = face / 2
            val sign = if (face % 2 == 0) -1.0 else 1.0
            val coordinates = box.half.map { Random.nextDouble(-it, it) }.toMutableList()
            coordinates[axis] = sign * box.half[axis]
            val point = box.point(coordinates[0], coordinates[1], coordinates[2])
            val normal = box.axes[axis].scale(sign)
            var coverage = 0
            for (index in candidates(AABB(point, point).inflate(1.0e-7))) {
                val other = parts[index]
                val tolerance = (other.half.min() * 1.0e-6).coerceAtMost(1.0e-7)
                if (!other.contains(point, tolerance)) continue
                // 沿法线的无穷小步仍在另一框内，说明这是接触内部面或被覆盖的面。
                val relative = point.subtract(other.center)
                val exits = other.axes.indices.any { i ->
                    val projection = relative.dot(other.axes[i])
                    val derivative = normal.dot(other.axes[i])
                    abs(abs(projection) - other.half[i]) <= tolerance &&
                        if (projection >= 0.0) derivative > 1.0e-10 else derivative < -1.0e-10
                }
                if (!exits) return@repeat
                coverage++
            }
            // 多个框共享外侧平面时按覆盖次数反向加权，避免复制框改变采样密度。
            if (coverage > 1 && Random.nextInt(coverage) != 0) return@repeat
            result.addPoint(point.subtract(relativeTo).asRelative())
            accepted++
            if (accepted == count) return result
        }
        error("表面采样达到 $maxAttempts 次候选预算，仅取得 $accepted/$count 个点；请降低点数或提高预算")
    }

    /** 空间树只执行宽相位，调用方继续执行精确几何判断。 */
    private fun candidates(query: AABB): List<Int> = buildList { tree?.query(query, this) }
}
