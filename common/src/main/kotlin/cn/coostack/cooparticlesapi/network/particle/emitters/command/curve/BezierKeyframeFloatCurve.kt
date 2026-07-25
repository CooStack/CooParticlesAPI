package cn.coostack.cooparticlesapi.network.particle.emitters.command.curve

import kotlin.math.abs
import kotlin.math.sqrt

class BezierKeyframeFloatCurve(
    frames: List<BezierFloatKeyframe> = listOf(BezierFloatKeyframe(0.0, 0.0))
) : FloatCurve {
    private val keyframes: MutableList<BezierFloatKeyframe> =
        frames.map { it.withClampedTime() }
            .sortedBy { it.time }
            .toMutableList()

    init {
        if (keyframes.isEmpty()) {
            keyframes.add(BezierFloatKeyframe(0.0, 0.0))
        }
    }

    fun setFrames(frames: List<BezierFloatKeyframe>) = apply {
        keyframes.clear()
        keyframes.addAll(
            frames.map { it.withClampedTime() }
                .sortedBy { it.time }
        )
        if (keyframes.isEmpty()) {
            keyframes.add(BezierFloatKeyframe(0.0, 0.0))
        }
    }

    fun addFrame(
        time: Double,
        value: Double,
        outX: Double = 0.0,
        outY: Double = 0.0,
        inX: Double = 0.0,
        inY: Double = 0.0
    ) = apply {
        keyframes.add(
            BezierFloatKeyframe(
                time = time.coerceIn(0.0, 1.0),
                value = value,
                outX = outX,
                outY = outY,
                inX = inX,
                inY = inY
            )
        )
        keyframes.sortBy { it.time }
    }

    override fun sample(t: Double): Double {
        val clamped = t.coerceIn(0.0, 1.0)
        if (keyframes.size == 1) {
            return keyframes[0].value
        }
        if (clamped <= keyframes.first().time) {
            return keyframes.first().value
        }
        if (clamped >= keyframes.last().time) {
            return keyframes.last().value
        }

        for (i in 1 until keyframes.size) {
            val prev = keyframes[i - 1]
            val next = keyframes[i]
            if (clamped <= next.time) {
                return sampleSegment(prev, next, clamped)
            }
        }
        return keyframes.last().value
    }

    private fun sampleSegment(prev: BezierFloatKeyframe, next: BezierFloatKeyframe, time: Double): Double {
        val span = next.time - prev.time
        if (span <= EPSILON) {
            return next.value
        }

        val p0x = prev.time
        val p1x = prev.time + prev.outX / 100.0
        val p2x = next.time + next.inX / 100.0
        val p3x = next.time

        val p0y = prev.value
        val p1y = prev.value + prev.outY
        val p2y = next.value + next.inY
        val p3y = next.value

        val linearGuess = ((time - prev.time) / span).coerceIn(0.0, 1.0)
        val u = solveParameterForX(time, p0x, p1x, p2x, p3x, linearGuess)
        return cubicBezier(u, p0y, p1y, p2y, p3y)
    }

    private fun solveParameterForX(
        targetX: Double,
        p0: Double,
        p1: Double,
        p2: Double,
        p3: Double,
        guess: Double
    ): Double {
        if (p0 <= p1 && p1 <= p2 && p2 <= p3) {
            return solveMonotonicParameter(targetX, p0, p1, p2, p3)
        }

        val roots = mutableListOf<Double>()
        val splitPoints = derivativeRoots(p0, p1, p2, p3)
            .filter { it > EPSILON && it < 1.0 - EPSILON }
            .plus(0.0)
            .plus(1.0)
            .sorted()

        for (i in 1 until splitPoints.size) {
            val lo = splitPoints[i - 1]
            val hi = splitPoints[i]
            val flo = cubicBezier(lo, p0, p1, p2, p3) - targetX
            val fhi = cubicBezier(hi, p0, p1, p2, p3) - targetX
            when {
                abs(flo) <= EPSILON -> roots.add(lo)
                abs(fhi) <= EPSILON -> roots.add(hi)
                flo < 0.0 && fhi > 0.0 -> roots.add(solveBracketedParameter(targetX, p0, p1, p2, p3, lo, hi))
                flo > 0.0 && fhi < 0.0 -> roots.add(solveBracketedParameter(targetX, p0, p1, p2, p3, lo, hi))
            }
        }

        return roots.distinctBy { (it / EPSILON).toLong() }
            .minByOrNull { abs(it - guess) }
            ?: closestSampledParameter(targetX, p0, p1, p2, p3, guess)
    }

    private fun solveMonotonicParameter(
        targetX: Double,
        p0: Double,
        p1: Double,
        p2: Double,
        p3: Double
    ): Double {
        var lo = 0.0
        var hi = 1.0
        var mid = 0.5
        repeat(32) {
            mid = (lo + hi) * 0.5
            if (cubicBezier(mid, p0, p1, p2, p3) < targetX) {
                lo = mid
            } else {
                hi = mid
            }
        }
        return mid
    }

    private fun solveBracketedParameter(
        targetX: Double,
        p0: Double,
        p1: Double,
        p2: Double,
        p3: Double,
        startLo: Double,
        startHi: Double
    ): Double {
        var lo = startLo
        var hi = startHi
        var mid = (lo + hi) * 0.5
        val increasing = cubicBezier(hi, p0, p1, p2, p3) >= cubicBezier(lo, p0, p1, p2, p3)
        repeat(32) {
            mid = (lo + hi) * 0.5
            val x = cubicBezier(mid, p0, p1, p2, p3)
            if ((x < targetX) == increasing) {
                lo = mid
            } else {
                hi = mid
            }
        }
        return mid
    }

    private fun derivativeRoots(p0: Double, p1: Double, p2: Double, p3: Double): List<Double> {
        val a = -p0 + 3.0 * p1 - 3.0 * p2 + p3
        val b = 3.0 * p0 - 6.0 * p1 + 3.0 * p2
        val c = -3.0 * p0 + 3.0 * p1

        val qa = 3.0 * a
        val qb = 2.0 * b
        val qc = c
        if (abs(qa) <= EPSILON) {
            return if (abs(qb) <= EPSILON) emptyList() else listOf(-qc / qb)
        }

        val discriminant = qb * qb - 4.0 * qa * qc
        if (discriminant < 0.0) {
            return emptyList()
        }

        val root = sqrt(discriminant)
        return listOf(
            (-qb - root) / (2.0 * qa),
            (-qb + root) / (2.0 * qa)
        )
    }

    private fun closestSampledParameter(
        targetX: Double,
        p0: Double,
        p1: Double,
        p2: Double,
        p3: Double,
        guess: Double
    ): Double {
        var best = guess
        var bestError = abs(cubicBezier(guess, p0, p1, p2, p3) - targetX)
        for (i in 0..64) {
            val u = i / 64.0
            val error = abs(cubicBezier(u, p0, p1, p2, p3) - targetX)
            if (error < bestError) {
                best = u
                bestError = error
            }
        }
        return best
    }

    private fun cubicBezier(t: Double, p0: Double, p1: Double, p2: Double, p3: Double): Double {
        val u = 1.0 - t
        val u2 = u * u
        val t2 = t * t
        return u2 * u * p0 +
                3.0 * u2 * t * p1 +
                3.0 * u * t2 * p2 +
                t2 * t * p3
    }

    private fun BezierFloatKeyframe.withClampedTime(): BezierFloatKeyframe {
        return copy(time = time.coerceIn(0.0, 1.0))
    }

    private companion object {
        const val EPSILON = 1.0E-9
    }
}
