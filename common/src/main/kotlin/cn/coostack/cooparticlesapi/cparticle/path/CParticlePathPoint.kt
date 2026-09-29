package cn.coostack.cooparticlesapi.cparticle.path

import net.minecraft.world.phys.Vec3

/**
 * 判定一个世界坐标是否三个分量都为有限数。
 *
 * `Vec3` 本身没有 `isFinite` 入口，只能逐分量判断；这里集中一处，避免各处重复写出同样的三元条件。
 */
private fun Vec3.isFiniteVector(): Boolean =
    x.isFinite() && y.isFinite() && z.isFinite()

/**
 * # 路径控制点
 *
 * 控制点由位置与两侧控制柄组成。[inHandle] / [outHandle] 是**相对位置的偏移向量**，
 * 不是绝对坐标，这样整体平移控制点时不需要同时改写控制柄。
 *
 * 控制柄的使用规则：
 * - 第 `i` 段的起点控制柄取 `points[i].outHandle`，终点控制柄取 `points[i + 1].inHandle`。
 * - 两侧控制柄共线且等长时，该点是平滑连接点；不共线时形成尖角。
 * - [CParticlePathSegmentType.LINEAR] 完全忽略控制柄。
 *
 * @property position 控制点的本地空间位置
 * @property inHandle 从前一段进入本点时的控制柄偏移
 * @property outHandle 从本点离开进入下一段时的控制柄偏移
 */
data class CParticlePathPoint(
    val position: Vec3,
    val inHandle: Vec3 = Vec3.ZERO,
    val outHandle: Vec3 = Vec3.ZERO,
) {
    init {
        require(position.isFiniteVector()) { "Path control point position must be finite" }
        require(inHandle.isFiniteVector()) { "Path control point in-handle must be finite" }
        require(outHandle.isFiniteVector()) { "Path control point out-handle must be finite" }
    }

    /**
     * 产生一个只改变位置、控制柄整体跟随平移的新控制点。
     *
     * @param nextPosition 新位置
     * @return 平移后的控制点
     */
    fun withPosition(nextPosition: Vec3): CParticlePathPoint =
        copy(position = nextPosition)

    companion object {
        /**
         * 用一条折线的顶点构造控制点序列，全部控制柄为零。
         *
         * @param positions 折线顶点，至少需要两个点
         * @return 可直接用于 [CParticlePathDefinition] 的控制点序列
         */
        @JvmStatic
        fun polyline(positions: List<Vec3>): List<CParticlePathPoint> =
            positions.map { CParticlePathPoint(it) }

        /**
         * 构造一个中心对称的平滑控制柄。
         *
         * 常用于让相邻两段的连接处保持切线连续：入控制柄指向前一点方向，出控制柄指向后一点方向。
         *
         * @param previous 前一个控制点位置
         * @param current 当前控制点位置
         * @param next 后一个控制点位置
         * @param scale 控制柄长度相对相邻两点平均距离的比例
         * @return 同时带有入、出控制柄的控制点
         */
        @JvmStatic
        fun smooth(
            previous: Vec3,
            current: Vec3,
            next: Vec3,
            scale: Double = 1.0 / 3.0,
        ): CParticlePathPoint {
            require(scale.isFinite() && scale >= 0.0) { "Smooth handle scale must be finite and non-negative" }
            val direction = next.subtract(previous)
            val averageDistance = (current.distanceTo(previous) + next.distanceTo(current)) * 0.5
            val length = averageDistance * scale
            val unit = if (direction.lengthSqr() > 1.0E-12) direction.normalize() else Vec3.ZERO
            return CParticlePathPoint(
                position = current,
                inHandle = unit.scale(-length),
                outHandle = unit.scale(length),
            )
        }
    }
}
