package cn.coostack.cooparticlesapi.utils.helper

import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

abstract class BezierValueScaleHelper(
    scaleTick: Int,
    minScale: Double,
    maxScale: Double,
    var controlPoint1: RelativeLocation,
    var controlPoint2: RelativeLocation
) :
    ScaleHelper(minScale, maxScale, scaleTick) {
    private val deltaScale: Double
        get() = maxScale - minScale

    var bezierPoints = createBezierPoints()

    private fun createBezierPoints(): List<RelativeLocation> {
        val target = RelativeLocation(scaleTick.toDouble(), deltaScale, 0.0)
        val (startHandle, endHandle) = resolveControlPoints()
        return List(scaleTick + 1) { tick ->
            RelativeLocation(
                tick.toDouble(),
                Math3DUtil.evaluateBezierCurveYAtX(
                    target,
                    startHandle,
                    endHandle,
                    tick.toDouble()
                ),
                0.0
            )
        }
    }

    private fun resolveControlPoints(): Pair<RelativeLocation, RelativeLocation> {
        if (controlPoint2.x > 0.0 && controlPoint2.x <= scaleTick.toDouble()) {
            return RelativeLocation(controlPoint1.x, controlPoint1.y - minScale, controlPoint1.z) to
                RelativeLocation(controlPoint2.x - scaleTick, controlPoint2.y - maxScale, controlPoint2.z)
        }
        return controlPoint1.clone() to controlPoint2.clone()
    }

    override fun recalculateStep(): BezierValueScaleHelper {
        val temp = min(minScale, maxScale)
        maxScale = max(minScale, maxScale)
        minScale = temp
        bezierPoints = createBezierPoints()
        return this
    }

    override fun toggleScale(scale: Double) {
        val currentPoint = bezierPoints.withIndex().minBy {
            abs(minScale + it.value.y - scale)
        }
        current = currentPoint.index
        scale(minScale + currentPoint.value.y)
    }

    override fun doScale() {
        if (getLoadedGroup() == null) return
        if (over()) return
        current = (current + 1).coerceAtMost(scaleTick)
        scale(minScale + bezierPoints[current].y)
    }

    override fun doScaleTo(current: Int) {
        val enter = current.coerceIn(0, scaleTick)
        this.current = enter
        when {
            enter <= 0 -> scale(minScale)
            enter >= scaleTick -> scale(maxScale)
            else -> scale(minScale + bezierPoints[enter].y)
        }
    }

    override fun doScaleReversed() {
        if (getLoadedGroup() == null) {
            return
        }
        if (isZero()) {
            return
        }
        current = max(0, current - 1)
        scale(minScale + bezierPoints[current].y)
    }
}