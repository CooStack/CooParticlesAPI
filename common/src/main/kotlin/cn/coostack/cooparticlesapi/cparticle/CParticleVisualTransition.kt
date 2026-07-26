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

    fun matches(
        durationTicks: Float,
        alphaCurve: CParticleCurve?,
        sizeCurve: CParticleCurve?,
        colorFrom: Vector3fc?,
        colorTo: Vector3fc?,
        mode: CParticleTransitionMode,
    ): Boolean {
        if (this.durationTicks != durationTicks || this.mode != mode) return false
        if (!sameCurve(this.alphaCurve, alphaCurve) || !sameCurve(this.sizeCurve, sizeCurve)) return false
        if (hasColor != (colorFrom != null)) return false
        if (!hasColor) return true
        return sameColor(this.colorFrom, colorFrom!!) && sameColor(this.colorTo, colorTo!!)
    }

    fun progressAt(systemTime: Float): Float? {
        val normalized = (systemTime - startTick) / durationTicks
        return when (mode) {
            CParticleTransitionMode.HOLD_END -> normalized.coerceIn(0f, 1f)
            CParticleTransitionMode.RESET -> normalized.takeIf { it < 1f }?.coerceAtLeast(0f)
            CParticleTransitionMode.LOOP -> normalized.coerceAtLeast(0f) % 1f
        }
    }

    private fun sameCurve(first: CParticleCurve?, second: CParticleCurve?): Boolean {
        if (first === second) return true
        if (first == null || second == null || first.keyCount != second.keyCount) return false
        return first.packed.contentEquals(second.packed)
    }

    private fun sameColor(first: Vector3fc, second: Vector3fc): Boolean {
        return first.x() == second.x() && first.y() == second.y() && first.z() == second.z()
    }
}
