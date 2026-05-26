package cn.coostack.cooparticlesapi.barrages

import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Quaterniond
import org.joml.Vector3d
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 弹幕碰撞盒。
 *
 * - x1/y1/z1, x2/y2/z2 表示**旋转前的局部 AABB** (以原点为中心或任意中心，构造时会保证 1 < 2)
 * - [direction] 表示朝向 (默认 +Z 轴, 无 roll)，可以与 yaw/pitch 互转
 * - [ofBox] 把局部盒子按 [direction] 旋转后取**轴对齐外接 AABB**，再平移到 center
 *
 * 用法:
 * ```
 * val box = HitBox.of(2.0, 2.0, 4.0)            // 局部 2x2x4
 *     .setDirection(barrage.direction)          // 跟随弹幕方向
 * val world = box.ofBox(barrage.loc)            // 用于碰撞扫描的外接 AABB
 * ```
 */
class HitBox(
    var x1: Double, var y1: Double, var z1: Double,
    var x2: Double, var y2: Double, var z2: Double,
) {
    init {
        replacePoint()
    }

    /** 朝向单位向量。默认 +Z，无 roll。 */
    var direction: RelativeLocation = RelativeLocation.zAxis()
        set(value) {
            field = if (value.length() <= 1e-6) RelativeLocation.zAxis() else value.normalize()
        }

    /** 由 [direction] 反算的 yaw (弧度)。 */
    val yaw: Double
        get() = Math3DUtil.getYawFromLocation(direction)

    /** 由 [direction] 反算的 pitch (弧度)。 */
    val pitch: Double
        get() = Math3DUtil.getPitchFromLocation(direction)

    companion object {
        /** 以原点为中心、按尺寸创建一个无旋转的盒子。 */
        @JvmStatic
        fun of(dx: Double, dy: Double, dz: Double): HitBox {
            return HitBox(-dx / 2, -dy / 2, -dz / 2, dx / 2, dy / 2, dz / 2)
        }

        /** 创建带朝向的盒子。 */
        @JvmStatic
        fun of(dx: Double, dy: Double, dz: Double, direction: RelativeLocation): HitBox {
            return of(dx, dy, dz).apply { this.direction = direction }
        }

        /** 由 yaw/pitch 创建带朝向的盒子 (弧度)。 */
        @JvmStatic
        fun fromYawPitch(dx: Double, dy: Double, dz: Double, yaw: Double, pitch: Double): HitBox {
            return of(dx, dy, dz).apply { setYawPitch(yaw, pitch) }
        }

        /** 由 yaw/pitch 反算单位方向向量 (与 [Math3DUtil.getYawFromLocation] 约定一致)。 */
        @JvmStatic
        fun directionOf(yaw: Double, pitch: Double): RelativeLocation {
            val cp = cos(pitch)
            return RelativeLocation(-sin(yaw) * cp, sin(pitch), cos(yaw) * cp)
        }
    }

    /** 设置朝向，链式调用。 */
    fun setDirection(direction: RelativeLocation): HitBox = apply { this.direction = direction }

    /** 设置朝向，链式调用 (Vec3 形式)。 */
    fun setDirection(direction: Vec3): HitBox = apply {
        this.direction = RelativeLocation(direction.x, direction.y, direction.z)
    }

    /** 通过 yaw/pitch 设置朝向 (弧度)。 */
    fun setYawPitch(yaw: Double, pitch: Double): HitBox = apply {
        this.direction = directionOf(yaw, pitch)
    }

    /**
     * 旋转前的局部 AABB (不包含中心平移，不包含朝向旋转)。
     */
    fun localBox(): AABB = AABB(x1, y1, z1, x2, y2, z2)

    /**
     * 旋转后但未平移的外接 AABB。
     * 用于做 Minkowski sum 等需要"以世界轴投影后的盒子尺寸"的地方。
     */
    fun rotatedLocalBox(): AABB {
        if (isAxisAligned()) return AABB(x1, y1, z1, x2, y2, z2)
        val q = Quaterniond().rotateY(-yaw).rotateX(-pitch)
        val v = Vector3d()
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var minZ = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        var maxZ = Double.NEGATIVE_INFINITY
        for (cx in doubleArrayOf(x1, x2)) for (cy in doubleArrayOf(y1, y2)) for (cz in doubleArrayOf(z1, z2)) {
            v.set(cx, cy, cz).rotate(q)
            if (v.x < minX) minX = v.x; if (v.x > maxX) maxX = v.x
            if (v.y < minY) minY = v.y; if (v.y > maxY) maxY = v.y
            if (v.z < minZ) minZ = v.z; if (v.z > maxZ) maxZ = v.z
        }
        return AABB(minX, minY, minZ, maxX, maxY, maxZ)
    }

    /**
     * 把局部盒子按 [direction] 旋转后再平移到 [center]，返回外接 AABB。
     */
    fun ofBox(center: Vec3): AABB = rotatedLocalBox().move(center.x, center.y, center.z)

    /** 判断当前 [direction] 是否就是 +Z (即等价于无旋转)。 */
    fun isAxisAligned(): Boolean {
        val d = direction
        return abs(d.x) < 1e-6 && abs(d.y) < 1e-6 && d.z > 0
    }

    /**
     * @deprecated 旋转请改用 [direction] / [setYawPitch]。本方法保留只为旧调用兼容。
     */
    @Deprecated("使用 direction 属性表达旋转, 不再原地修改 6 个顶点", ReplaceWith("setDirection(to)"))
    fun rotateTo(axis: RelativeLocation, to: RelativeLocation) {
        if (axis.length() <= 1e-6 || to.length() <= 1e-6) return
        val points = listOf(
            RelativeLocation(x1, y1, z1),
            RelativeLocation(x2, y2, z2),
        )
        Math3DUtil.rotatePointsToPoint(points, axis, to)
        x1 = points[0].x; y1 = points[0].y; z1 = points[0].z
        x2 = points[1].x; y2 = points[1].y; z2 = points[1].z
        replacePoint()
    }

    /** 让 1 系列字段恒小于 2 系列字段。 */
    private fun replacePoint() {
        if (x1 > x2) {
            val t = x1; x1 = x2; x2 = t
        }
        if (y1 > y2) {
            val t = y1; y1 = y2; y2 = t
        }
        if (z1 > z2) {
            val t = z1; z1 = z2; z2 = t
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HitBox) return false
        return x1 == other.x1 && y1 == other.y1 && z1 == other.z1 &&
                x2 == other.x2 && y2 == other.y2 && z2 == other.z2 &&
                direction == other.direction
    }

    override fun hashCode(): Int {
        var r = x1.hashCode()
        r = 31 * r + y1.hashCode(); r = 31 * r + z1.hashCode()
        r = 31 * r + x2.hashCode(); r = 31 * r + y2.hashCode(); r = 31 * r + z2.hashCode()
        r = 31 * r + direction.hashCode()
        return r
    }

    override fun toString(): String =
        "HitBox(local=[$x1,$y1,$z1 -> $x2,$y2,$z2], direction=$direction)"
}
