package cn.coostack.cooparticlesapi.cparticle

import org.joml.Vector3f
import org.joml.Vector3fc

internal class CParticleVisualTransition(
    val startTick: Float,
    val durationTicks: Float,
    val alphaCurve: CParticleCurve?,
    val sizeCurve: CParticleCurve?,
    colorFrom: Vector3fc?,
    colorTo: Vector3fc?,
    val mode: CParticleTransitionMode,
) {
    val hasColor = colorFrom != null
    val colorFrom = colorFrom?.let(::Vector3f) ?: Vector3f()
    val colorTo = colorTo?.let(::Vector3f) ?: Vector3f()

    fun progressAt(systemTime: Float): Float? {
        val normalized = (systemTime - startTick) / durationTicks
        return when (mode) {
            CParticleTransitionMode.HOLD_END -> normalized.coerceIn(0f, 1f)
            CParticleTransitionMode.RESET -> normalized.takeIf { it < 1f }?.coerceAtLeast(0f)
            CParticleTransitionMode.LOOP -> normalized.coerceAtLeast(0f) % 1f
        }
    }
}
