package cn.coostack.cooparticlesapi.animation.timeline

import net.minecraft.world.phys.Vec3

class Vec3ConstTimeAnimator(
    var durationTick: Int,
    var targetNum: Vec3,
) {
    companion object {
        private const val EPSILON = 1e-6
    }

    var current: Vec3 = Vec3(0.0, 0.0, 0.0)

    init {
        require(durationTick > 0)
    }

    private fun stepSize(): Double = targetNum.length() / durationTick.toDouble()

    private fun moveVec3Toward(now: Vec3, target: Vec3, step: Double): Vec3 {
        val delta = target.subtract(now)
        val distance = delta.length()
        if (distance <= EPSILON || step <= EPSILON || step >= distance - EPSILON) {
            return target
        }

        val direction = delta.scale(1.0 / distance)
        return now.add(direction.scale(step))
    }

    fun next(): Vec3 {
        current = moveVec3Toward(current, targetNum, stepSize())
        return current
    }

    fun prev(): Vec3 {
        current = moveVec3Toward(current, Vec3(0.0, 0.0, 0.0), stepSize())
        return current
    }

    fun isFinished() = targetNum.distanceTo(current) < EPSILON

    fun reset() {
        current = Vec3(0.0, 0.0, 0.0)
    }

    fun resetCurrentTo(current: Vec3): Vec3ConstTimeAnimator {
        this.current = current
        return this
    }
}
