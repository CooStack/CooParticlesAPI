package cn.coostack.cooparticlesapi.renderer.glow

import org.joml.Vector3f
import kotlin.math.abs

data class LineGlowSample(
    val position: Vector3f,
    val radius: Float,
    val intensity: Float
)

object LineGlowSampling {
    private const val AXIS_EPSILON = 1.0e-6f
    private const val MIN_SAMPLE_COUNT = 1
    private const val MAX_SAMPLE_COUNT = 7

    @JvmStatic
    fun createSamples(
        center: Vector3f,
        axis: Vector3f,
        halfLength: Float,
        baseRadius: Float,
        totalIntensity: Float,
        sampleCount: Int = 5
    ): List<LineGlowSample> {
        if (baseRadius <= 0.0f || totalIntensity <= 0.0f || sampleCount <= 0) {
            return emptyList()
        }

        val clampedCount = sampleCount.coerceIn(MIN_SAMPLE_COUNT, MAX_SAMPLE_COUNT)
        val normalizedAxis = Vector3f(axis)
        if (normalizedAxis.lengthSquared() <= AXIS_EPSILON) {
            normalizedAxis.set(0.0f, 1.0f, 0.0f)
        } else {
            normalizedAxis.normalize()
        }

        if (clampedCount == 1 || halfLength <= 1.0e-4f) {
            return listOf(
                LineGlowSample(
                    position = Vector3f(center),
                    radius = baseRadius,
                    intensity = totalIntensity
                )
            )
        }

        val intensityWeights = FloatArray(clampedCount)
        val radiusWeights = FloatArray(clampedCount)
        var totalWeight = 0.0f
        for (index in 0 until clampedCount) {
            val sampleT = index.toFloat() / (clampedCount - 1).toFloat()
            val centerBias = 1.0f - abs(sampleT - 0.5f) * 2.0f
            intensityWeights[index] = 0.82f + centerBias * 0.34f
            radiusWeights[index] = 0.88f + centerBias * 0.18f
            totalWeight += intensityWeights[index]
        }

        val samples = ArrayList<LineGlowSample>(clampedCount)
        for (index in 0 until clampedCount) {
            val sampleT = index.toFloat() / (clampedCount - 1).toFloat()
            val offsetFactor = sampleT * 2.0f - 1.0f
            val offset = Vector3f(normalizedAxis).mul(halfLength * offsetFactor)
            samples.add(
                LineGlowSample(
                    position = Vector3f(center).add(offset),
                    radius = baseRadius * radiusWeights[index],
                    intensity = totalIntensity * intensityWeights[index] / totalWeight
                )
            )
        }
        return samples
    }
}
