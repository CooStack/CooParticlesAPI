package cn.coostack.cooparticlesapi.utils

import kotlin.math.floor
import kotlin.math.sin

internal object ClientCameraShakeMath {
    private const val LEGACY_SHAKE_RETARGET_TICKS = 2.0
    private const val SHAKE_FOLLOW = 0.45
    private const val HIGH_FREQUENCY_FOLLOW_START = 2.0
    private const val HIGH_FREQUENCY_FOLLOW_RANGE = 8.0
    private const val MAX_SHAKE_FOLLOW = 0.92

    fun shakePhaseStep(frequency: Double): Double {
        return frequency / LEGACY_SHAKE_RETARGET_TICKS
    }

    fun shakeFollowFactor(frequency: Double): Double {
        if (frequency <= HIGH_FREQUENCY_FOLLOW_START) {
            return SHAKE_FOLLOW
        }
        val extraFrequency = frequency - HIGH_FREQUENCY_FOLLOW_START
        val normalized = extraFrequency / (extraFrequency + HIGH_FREQUENCY_FOLLOW_RANGE)
        return SHAKE_FOLLOW + (MAX_SHAKE_FOLLOW - SHAKE_FOLLOW) * normalized.coerceIn(0.0, 1.0)
    }

    fun sampleShakeNoise(phase: Double, seed: Double): Double {
        val shiftedPhase = phase + seed
        val index = floor(shiftedPhase)
        val progress = smoothstep(shiftedPhase - index)
        val from = hashNoise(index, seed)
        val to = hashNoise(index + 1.0, seed)
        return lerpDouble(progress, from, to)
    }

    private fun smoothstep(progress: Double): Double {
        val clamped = progress.coerceIn(0.0, 1.0)
        return clamped * clamped * (3.0 - 2.0 * clamped)
    }

    private fun hashNoise(index: Double, seed: Double): Double {
        val raw = sin(index * 12.9898 + seed * 78.233) * 43758.5453123
        val fract = raw - floor(raw)
        return fract * 2.0 - 1.0
    }

    private fun lerpDouble(progress: Double, from: Double, to: Double): Double {
        return from + (to - from) * progress
    }
}
