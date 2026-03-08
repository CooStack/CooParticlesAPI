package cn.coostack.cooparticlesapi.animation.timeline

import cn.coostack.cooparticlesapi.utils.RelativeLocation

class RelativeLocationConstTimeAnimator(
    var durationTick: Int,
    var targetNum: RelativeLocation,
) {
    companion object {
        private const val EPSILON = 1e-6
    }

    var current: RelativeLocation = RelativeLocation.zero()

    init {
        require(durationTick > 0)
    }

    private fun stepSize(): Double = targetNum.length() / durationTick.toDouble()

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

    fun resetCurrentTo(current: RelativeLocation): RelativeLocationConstTimeAnimator {
        this.current = current.clone()
        return this
    }
}
