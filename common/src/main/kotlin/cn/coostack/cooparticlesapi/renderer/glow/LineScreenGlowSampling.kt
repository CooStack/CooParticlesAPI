package cn.coostack.cooparticlesapi.renderer.glow

import org.joml.Vector3f

object LineScreenGlowSampling {
    @JvmStatic
    fun buildGlows(
        center: Vector3f,
        axis: Vector3f,
        halfLength: Float,
        color: Vector3f,
        radius: Float,
        totalIntensity: Float,
        sampleCount: Int = 5,
        softness: Float = 0.52f
    ): List<ScreenGlow> {
        return LineGlowSampling.createSamples(
            center = center,
            axis = axis,
            halfLength = halfLength,
            baseRadius = radius,
            totalIntensity = totalIntensity,
            sampleCount = sampleCount
        ).map { sample ->
            ScreenGlow(
                position = Vector3f(sample.position),
                color = Vector3f(color),
                radius = sample.radius,
                intensity = sample.intensity,
                softness = softness
            )
        }
    }
}
