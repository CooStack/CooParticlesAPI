package cn.coostack.cooparticlesapi.animation.timeline

import cn.coostack.cooparticlesapi.utils.RelativeLocation
import kotlin.math.abs

class RelativeLocationConstSpeedAnimator(
    var speed: Double,
    var targetNum: RelativeLocation,
) {
    companion object {
        private const val EPSILON = 1e-6
    }

    var current: RelativeLocation = RelativeLocation.zero()

    init {
        require(speed > 0.0)
    }

    private fun stepSize(): Double = abs(speed)

    private fun moveRelativeLocationToward(
        now: RelativeLocation,
        target: RelativeLocation,
        step: Double,
    ): RelativeLocation {
        val delta = target - now
        val distance = delta.length()
        if (distance <= EPSILON || step <= EPSILON || step >= distance - EPSILON) {
            return target
        }

        val direction = delta * (1.0 / distance)
        return now + (direction * step)
    }

    fun next(): RelativeLocation {
        current = moveRelativeLocationToward(current, targetNum, stepSize())
        return current
    }

    fun prev(): RelativeLocation {
        current = moveRelativeLocationToward(current, RelativeLocation.zero(), stepSize())
        return current
    }

    fun isFinished() = targetNum.distance(current) < EPSILON

    fun reset() {
        current = RelativeLocation.zero()
    }

    fun resetCurrentTo(current: RelativeLocation): RelativeLocationConstSpeedAnimator {
        this.current = current.clone()
        return this
    }
}