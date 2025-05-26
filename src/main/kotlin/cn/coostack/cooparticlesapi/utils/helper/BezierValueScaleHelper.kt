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
    private val deltaScale = maxScale - minScale

    var bezierPoints = Math3DUtil.generateBezierCurve(
        RelativeLocation(scaleTick.toDouble(), deltaScale, 0.0),
        controlPoint1, controlPoint2, scaleTick
    )

    override fun recalculateStep(): BezierValueScaleHelper {
        val temp = min(minScale, maxScale)
        maxScale = max(minScale, maxScale)
        minScale = temp
        bezierPoints = Math3DUtil.generateBezierCurve(
            RelativeLocation(scaleTick.toDouble(), deltaScale, 0.0),
            controlPoint1, controlPoint2, scaleTick
        )
        return this
    }

    override fun toggleScale(scale: Double) {
        // 遍历scale?
        val currentPoint = bezierPoints.withIndex().minBy {
            abs(it.value.y - scale)
        }
        current = currentPoint.index
        scale(minScale + currentPoint.value.y)
    }

    override fun doScale() {
        if (getLoadedGroup() == null) return
        if (over()) return
        val value = bezierPoints[current++].y
        scale(minScale + value)
    }

    override fun doScaleTo(current: Int) {
        val enter = current.coerceAtLeast(0)
        this.current = enter
        if (current >= scaleTick) {
            resetScaleMax()
            return
        }
        if (current <= 0) {
            resetScaleMin()
            return
        }
        val value = bezierPoints[current].y
        scale(minScale + value)
    }

    override fun doScaleReversed() {
        if (getLoadedGroup() == null) {
            return
        }
        if (isZero()) {
            return
        }
        val value = bezierPoints[max(0, --current)].y
        scale(minScale + value)
    }
}