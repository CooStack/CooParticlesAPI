package cn.coostack.cooparticlesapi.animation.motion

import cn.coostack.cooparticlesapi.api.controler.server.ServerControler
import net.minecraft.world.phys.Vec3

/**
 * 路线队列运动。
 *
 * 按固定速度依次经过[points]中的路径点， 一tick内如果速度还有剩余会继续走向下一个点。
 */
class RoutePathMotion<T : ServerControler<*>> : AbstractPathMotion<T>() {
    companion object {
        private const val EPSILON = 1e-6
    }

    /**
     * 路径点队列
     */
    val points = ArrayList<Vec3>()

    /**
     * 每tick移动距离
     */
    var speed = 0.1

    /**
     * 完成后是否重新从第一个点开始
     */
    var loop = false

    private var current = Vec3.ZERO
    private var currentIndex = 0

    fun addPoint(point: Vec3): RoutePathMotion<T> = apply {
        points.add(point)
    }

    fun addPoints(points: Collection<Vec3>): RoutePathMotion<T> = apply {
        this.points.addAll(points)
    }

    override fun nextPosition(controler: T): Vec3? {
        if (points.isEmpty() || speed <= EPSILON) {
            return null
        }

        if (tickCount == 0 && currentIndex == 0) {
            current = points.first()
        }

        if (currentIndex >= points.lastIndex) {
            if (!loop) {
                return null
            }
            currentIndex = 0
            current = points.first()
        }

        var remaining = speed
        while (remaining > EPSILON && currentIndex < points.lastIndex) {
            val target = points[currentIndex + 1]
            val delta = target.subtract(current)
            val distance = delta.length()

            if (distance <= EPSILON) {
                current = target
                currentIndex++
                continue
            }

            if (distance <= remaining + EPSILON) {
                current = target
                currentIndex++
                remaining -= distance
            } else {
                current = current.add(delta.normalize().scale(remaining))
                remaining = 0.0
            }
        }

        return current
    }

    override fun onReset() {
        currentIndex = 0
        current = points.firstOrNull() ?: Vec3.ZERO
    }
}
