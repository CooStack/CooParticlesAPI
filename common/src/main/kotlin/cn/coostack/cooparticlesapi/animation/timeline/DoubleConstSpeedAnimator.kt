package cn.coostack.cooparticlesapi.animation.timeline

import kotlin.math.abs

class DoubleConstSpeedAnimator(
    var speed: Double,
    var targetNum: Double,
) {
    companion object {
        private const val EPSILON = 1e-6
    }

    var current: Double = 0.0

    init {
        require(speed > 0.0)
    }

    private fun stepSize(): Double = abs(speed)

    private fun moveScalarToward(now: Double, target: Double, step: Double): Double {
        val delta = target - now
        if (abs(delta) <= EPSILON || step <= EPSILON) {
            return target
        }

        val moved = if (delta > 0.0) now + step else now - step
        return if (delta > 0.0) minOf(moved, target) else maxOf(moved, target)
    }

    fun next(): Double {
        current = moveScalarToward(current, targetNum, stepSize())
        return current
    }

    fun prev(): Double {
        current = moveScalarToward(current, 0.0, stepSize())
        return current
    }

    fun isFinished() = abs(targetNum - current) < EPSILON

    fun reset() {
        current = 0.0
    }

    fun resetCurrentTo(current: Double): DoubleConstSpeedAnimator {
        this.current = current
        return this
    }
}
