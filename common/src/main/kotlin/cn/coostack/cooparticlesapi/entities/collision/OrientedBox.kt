package cn.coostack.cooparticlesapi.entities.collision

import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 不可变有向长方体；分离轴运动求解保留旋转后的孔洞，不使用外包框近似。
 * 示例：`OrientedBox(localBox, transform, entity.position())`。
 * @param local 非退化的模型局部框
 * @param transform 模型到放置原点空间的变换
 * @param origin 世界空间放置原点
 */
class OrientedBox(local: AABB, transform: EntityTransform, origin: Vec3) {
    /** 世界空间中心。 */
    val center: Vec3 = origin.add(transform.transformPoint(local.center))
    /** 旋转后的三个单位轴，仅供几何内部使用。 */
    internal val axes: List<Vec3>
    /** 缩放后的半边长，单位为格。 */
    internal val half: List<Double> = listOf(local.xsize * transform.scale.x / 2.0,
        local.ysize * transform.scale.y / 2.0, local.zsize * transform.scale.z / 2.0)
    /** 世界索引及粗筛使用的轴对齐外包框。 */
    val bounds: AABB
    /** AABB 与 OBB 的十五条候选分离轴。 */
    private val separatingAxes: List<Vec3>

    init {
        require(half.all { it.isFinite() && it > 0.0 })
        require(listOf(center.x, center.y, center.z).all { it.isFinite() })
        val rotation = transform.quaternion()
        axes = listOf(Vector3d(1.0, 0.0, 0.0), Vector3d(0.0, 1.0, 0.0), Vector3d(0.0, 0.0, 1.0))
            .map { rotation.transform(it); Vec3(it.x, it.y, it.z) }
        val extent = Vec3(axes.indices.sumOf { abs(axes[it].x) * half[it] },
            axes.indices.sumOf { abs(axes[it].y) * half[it] },
            axes.indices.sumOf { abs(axes[it].z) * half[it] })
        bounds = AABB(center.subtract(extent), center.add(extent))
        require(listOf(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ).all { it.isFinite() })
        val worldAxes = listOf(Vec3(1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0), Vec3(0.0, 0.0, 1.0))
        separatingAxes = worldAxes + axes + worldAxes.flatMap { world -> axes.map { world.cross(it) } }
            .filter { it.lengthSqr() > 1.0e-12 }.map { it.normalize() }
    }

    /**
     * 精确检查世界 AABB 是否穿入有向框，接触不算穿入。
     * 示例：`box.intersects(player.boundingBox)`。
     * @param box 世界空间查询框
     * @return 是否存在正体积交集
     */
    fun intersects(box: AABB): Boolean {
        if (!bounds.intersects(box)) return false
        val relative = box.center.subtract(center)
        return separatingAxes.all { normal ->
            abs(relative.dot(normal)) < projectedRadius(normal, box) - 1.0e-7
        }
    }

    /** 计算两个框在指定单位轴上的投影半径之和。 */
    private fun projectedRadius(normal: Vec3, box: AABB): Double =
        axes.indices.sumOf { abs(axes[it].dot(normal)) * half[it] } +
            (abs(normal.x) * box.xsize + abs(normal.y) * box.ysize + abs(normal.z) * box.zsize) / 2.0

    /**
     * 求原版逐轴移动在首次接触前的可行距离，允许已经穿插的实体脱离。
     * 示例：`shape.clip(Direction.Axis.Y, bounds, -0.1)`。
     * @param axis 世界空间运动轴
     * @param moving 起始世界空间 AABB
     * @param distance 带符号的目标距离，单位为格
     * @return 不超过原距离绝对值的可行距离
     */
    fun clip(axis: Direction.Axis, moving: AABB, distance: Double): Double {
        if (distance == 0.0) return distance
        // 原版踏步高度使用 float；只修复水平顶面的微小舍入下沉，防止共面接缝挡脚。
        // 倾斜面的外包框顶部不是真实支撑面，不能使用此接触修正。
        val penetration = bounds.maxY - moving.minY
        val contact = if (penetration > 0.0 && penetration <= 1.0e-5 &&
            axes.any { abs(it.y) >= 1.0 - 1.0e-12 }) moving.move(0.0, penetration, 0.0) else moving
        val relative = contact.center.subtract(center)
        var enter = Double.NEGATIVE_INFINITY
        var leave = Double.POSITIVE_INFINITY
        for (normal in separatingAxes) {
            // 求所有分离轴上的运动参数区间交集，防止旋转框的空角阻挡玩家。
            val radius = projectedRadius(normal, contact)
            val start = relative.dot(normal)
            val velocity = axis.choose(normal.x, normal.y, normal.z) * distance
            if (abs(velocity) < 1.0e-10) {
                if (abs(start) >= radius - 1.0e-7) return distance
                continue
            }
            val first = (-radius - start) / velocity
            val second = (radius - start) / velocity
            enter = max(enter, min(first, second))
            leave = min(leave, max(first, second))
            if (enter > leave) return distance
        }
        if (leave <= 1.0e-7 || enter < -1.0e-7 || enter > 1.0) return distance
        return distance * enter.coerceAtLeast(0.0)
    }

    /**
     * 将线段转入框的局部正交坐标求首次命中，起点在内部时返回起点。
     * 示例：`shape.raycast(eye, target)`。
     * @param start 世界空间起点
     * @param end 世界空间终点
     * @return 世界空间命中点，未命中时为空
     */
    fun raycast(start: Vec3, end: Vec3): Vec3? {
        val relative = start.subtract(center)
        val delta = end.subtract(start)
        var enter = 0.0
        var leave = 1.0
        for (i in axes.indices) {
            val position = relative.dot(axes[i])
            val velocity = delta.dot(axes[i])
            if (abs(velocity) < 1.0e-10) {
                if (abs(position) > half[i]) return null
            } else {
                val first = (-half[i] - position) / velocity
                val second = (half[i] - position) / velocity
                enter = max(enter, min(first, second))
                leave = min(leave, max(first, second))
                if (enter > leave) return null
            }
        }
        return start.add(delta.scale(enter))
    }

    /** 检查闭框包含，容差只用于采样面的数值误差。 */
    internal fun contains(point: Vec3, tolerance: Double = 0.0): Boolean {
        val relative = point.subtract(center)
        return axes.indices.all { abs(relative.dot(axes[it])) <= half[it] + tolerance }
    }

    /** 将有向框中心坐标中的三个距离转换成世界点。 */
    internal fun point(x: Double, y: Double, z: Double): Vec3 =
        center.add(axes[0].scale(x)).add(axes[1].scale(y)).add(axes[2].scale(z))
}
