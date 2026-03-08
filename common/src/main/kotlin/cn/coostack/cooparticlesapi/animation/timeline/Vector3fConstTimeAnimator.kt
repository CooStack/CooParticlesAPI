package cn.coostack.cooparticlesapi.animation.timeline

import org.joml.Vector3f

class Vector3fConstTimeAnimator(
    var durationTick: Int,
    var targetNum: Vector3f,
) {
    companion object {
        private const val EPSILON = 1e-6
    }

    var current: Vector3f = Vector3f()

    init {
        require(durationTick > 0)
    }

    private fun stepSize(): Double = targetNum.length().toDouble() / durationTick.toDouble()

    private fun moveVector3fToward(now: Vector3f, target: Vector3f, step: Double): Vector3f {
        val delta = Vector3f(target).sub(now)
        val distance = delta.length().toDouble()
        if (distance <= EPSILON || step <= EPSILON || step >= distance - EPSILON) {
            return Vector3f(target)
        }

        delta.mul((step / distance).toFloat())
        return Vector3f(now).add(delta)
    }

    fun next(): Vector3f {
        current = moveVector3fToward(current, targetNum, stepSize())
        return current
    }

    fun prev(): Vector3f {
        current = moveVector3fToward(current, Vector3f(), stepSize())
        return current
    }

    fun isFinished() = Vector3f(targetNum).sub(current).length().toDouble() < EPSILON

    fun reset() {
        current = Vector3f()
    }

    fun resetCurrentTo(current: Vector3f): Vector3fConstTimeAnimator {
        this.current = Vector3f(current)
        return this
    }
}