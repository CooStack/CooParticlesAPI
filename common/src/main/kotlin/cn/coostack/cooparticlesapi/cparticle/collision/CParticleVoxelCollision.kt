package cn.coostack.cooparticlesapi.cparticle.collision

import kotlin.math.floor

/**
 * CPU 与 compute shader 共用语义的无分配体素线段穿越算法。
 *
 * Example: 从空单元移动进占用单元时返回命中比例和朝外法线编码。
 * Forbidden: 起点在网格外或已位于占用单元时不会尝试弹出粒子。
 */
internal object CParticleVoxelCollision {
    /** 命中点沿法线退回的距离，与现有 `PhysicsUtil.fixBeforeCollidePosition` 一致。 */
    const val SURFACE_OFFSET = 0.07f

    /** 输出数组中的线段命中比例位置。 */
    const val RESULT_TIME = 0

    /** 输出数组中的法线编码位置：绝对值 1/2/3 表示 X/Y/Z，符号表示方向。 */
    const val RESULT_NORMAL = 1

    /** 调用方可复用的最小输出数组长度。 */
    const val RESULT_SIZE = 2

    /**
     * 沿速度线段查找第一个占用单元。
     *
     * Example: `start=(0.5,0.5,0.5), velocity=(1,0,0)` 命中 X=1 单元时返回 `t=0.5`、normal `-1`。
     * Forbidden: [result] 不能短于 [RESULT_SIZE]。
     *
     * @param grid 只读方块占用网格
     * @param startX 网格局部起点 X
     * @param startY 网格局部起点 Y
     * @param startZ 网格局部起点 Z
     * @param velocityX 本 tick 位移 X
     * @param velocityY 本 tick 位移 Y
     * @param velocityZ 本 tick 位移 Z
     * @param result 复用输出，依次写入命中比例和法线编码
     * @return 线段在网格内命中占用单元时为 `true`
     */
    fun trace(
        grid: CParticleBlockCollisionGrid,
        startX: Float,
        startY: Float,
        startZ: Float,
        velocityX: Float,
        velocityY: Float,
        velocityZ: Float,
        result: FloatArray,
    ): Boolean {
        require(result.size >= RESULT_SIZE)
        if (startX < 0f || startY < 0f || startZ < 0f ||
            startX >= grid.size ||
            startY >= grid.size ||
            startZ >= grid.size
        ) {
            return false
        }
        if (velocityX == 0f && velocityY == 0f && velocityZ == 0f) return false

        var cellX = floor(startX).toInt()
        var cellY = floor(startY).toInt()
        var cellZ = floor(startZ).toInt()
        if (grid.isOccupied(cellX, cellY, cellZ)) return false

        val stepX = velocityX.compareTo(0f)
        val stepY = velocityY.compareTo(0f)
        val stepZ = velocityZ.compareTo(0f)
        var timeX = boundaryTime(startX, cellX, velocityX, stepX)
        var timeY = boundaryTime(startY, cellY, velocityY, stepY)
        var timeZ = boundaryTime(startZ, cellZ, velocityZ, stepZ)
        val deltaX = traversalDelta(velocityX)
        val deltaY = traversalDelta(velocityY)
        val deltaZ = traversalDelta(velocityZ)

        repeat(grid.size * 3) {
            val hitTime: Float
            val normal: Int
            if (timeX <= timeY && timeX <= timeZ) {
                hitTime = timeX
                if (hitTime > 1f) return false
                cellX += stepX
                normal = -stepX
                timeX += deltaX
            } else if (timeY <= timeZ) {
                hitTime = timeY
                if (hitTime > 1f) return false
                cellY += stepY
                normal = -stepY * 2
                timeY += deltaY
            } else {
                hitTime = timeZ
                if (hitTime > 1f) return false
                cellZ += stepZ
                normal = -stepZ * 3
                timeZ += deltaZ
            }
            if (cellX !in 0 until grid.size ||
                cellY !in 0 until grid.size ||
                cellZ !in 0 until grid.size
            ) {
                return false
            }
            if (grid.isOccupied(cellX, cellY, cellZ)) {
                result[RESULT_TIME] = hitTime
                result[RESULT_NORMAL] = normal.toFloat()
                return true
            }
        }
        return false
    }

    /** 返回从起点到当前单元下一边界的参数时间。 */
    private fun boundaryTime(start: Float, cell: Int, velocity: Float, step: Int): Float = when {
        step > 0 -> (cell + 1f - start) / velocity
        step < 0 -> (start - cell) / -velocity
        else -> Float.POSITIVE_INFINITY
    }

    /** 返回每跨过一个单元需要增加的参数时间。 */
    private fun traversalDelta(velocity: Float): Float =
        if (velocity == 0f) Float.POSITIVE_INFINITY else 1f / kotlin.math.abs(velocity)

}
