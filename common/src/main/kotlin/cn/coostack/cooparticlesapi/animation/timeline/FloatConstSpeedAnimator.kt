package cn.coostack.cooparticlesapi.animation.timeline

import kotlin.math.abs

class FloatConstSpeedAnimator(
    var speed: Float,
    var targetNum: Float,
) {
    companion object {
        private const val EPSILON = 1e-6
    }

    var current: Float = 0.0f

    init {
        require(speed > 0.0f)
    }

    private fun stepSize(): Double = abs(speed.toDouble())

    private fun moveScalarToward(now: Double, target: Double, step: Double): Double {
        val delta = target - now
        if (abs(delta) <= EPSILON || step <= EPSILON) {
            return target
        }

        val moved = if (delta > 0.0) now + step else now - step
        return if (delta > 0.0) minOf(moved, target) else maxOf(moved, target)
    }

    fun next(): Float {
        current = moveScalarToward(current.toDouble(), targetNum.toDouble(), stepSize()).toFloat()
        return current
    }

    fun prev(): Float {
        current = moveScalarToward(current.toDouble(), 0.0, stepSize()).toFloat()
        return current
    }

    fun isFinished() = abs(targetNum.toDouble() - current.toDouble()) < EPSILON

    fun reset() {
        current = 0.0f
    }

    fun resetCurrentTo(current: Float): FloatConstSpeedAnimator {
        this.current = current
        return this
    }
}
