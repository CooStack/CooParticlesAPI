package cn.coostack.cooparticlesapi.animation.timeline

import kotlin.math.abs
import kotlin.math.sign

/**
 * 值动画器 用来设置 current-> target 的过度
 *
 * @param durationTick 持续时间
 */
class ValueConstTimeAnimator(
    var durationTick: Int,
    var targetNum: Number,
) {
    companion object {
        private const val EPSILON = 1e-6
    }

    var current: Number = 0

    init {
        require(durationTick > 0)
    }

    private fun stepSize(): Double = abs(targetNum.toDouble()) / durationTick.toDouble()

    fun next(): Double {
        val target = targetNum.toDouble()
        val now = current.toDouble()
        val delta = target - now
        if (abs(delta) <= EPSILON) {
            current = target
            return current.toDouble()
        }

        val step = stepSize()
        if (step <= EPSILON) {
            current = target
            return current.toDouble()
        }

        val direction = sign(delta)
        val moved = now + direction * step
        current = if (direction > 0) minOf(moved, target) else maxOf(moved, target)
        return current.toDouble()
    }

    fun prev(): Double {
        val now = current.toDouble()
        if (abs(now) <= EPSILON) {
            current = 0.0
            return current.toDouble()
        }

        val step = stepSize()
        if (step <= EPSILON) {
            current = 0.0
            return current.toDouble()
        }

        val direction = sign(-now)
        val moved = now + direction * step
        current = if (direction > 0) minOf(moved, 0.0) else maxOf(moved, 0.0)
        return current.toDouble()
    }

    fun isFinished() = abs(targetNum.toDouble() - current.toDouble()) < EPSILON

    fun reset() {
        current = 0
    }

    fun resetCurrentTo(current: Number): ValueConstTimeAnimator {
        this.current = current
        return this
    }

}
