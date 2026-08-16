package cn.coostack.cooparticlesapi.coofx.playback.track

import kotlin.math.acos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 按 glTF 2.0 规则采样已验证轨道。越过轨道两端时保持端点值，单关键帧轨道始终返回该值。
 */
object CooFxTrackSampler {
    fun sample(track: CooFxTrack, timeSeconds: Float): FloatArray {
        require(timeSeconds.isFinite()) { "采样时间必须为有限数" }
        if (track.keyframeCount == 1 || timeSeconds <= track.startTimeSeconds) {
            return keyframeValue(track, 0)
        }
        if (timeSeconds >= track.endTimeSeconds) {
            return keyframeValue(track, track.keyframeCount - 1)
        }

        val lowerIndex = findLowerKeyframe(track, timeSeconds)
        val upperIndex = lowerIndex + 1
        val lowerTime = track.timeAt(lowerIndex)
        val segmentDuration = track.timeAt(upperIndex) - lowerTime
        val progress = (timeSeconds - lowerTime) / segmentDuration
        return when (track.interpolation) {
            CooFxTrackInterpolation.STEP -> keyframeValue(track, lowerIndex)
            CooFxTrackInterpolation.LINEAR -> sampleLinear(track, lowerIndex, upperIndex, progress)
            CooFxTrackInterpolation.CUBICSPLINE -> sampleCubic(
                track,
                lowerIndex,
                upperIndex,
                progress,
                segmentDuration
            )
        }
    }

    private fun findLowerKeyframe(track: CooFxTrack, timeSeconds: Float): Int {
        var low = 0
        var high = track.keyframeCount - 1
        while (low + 1 < high) {
            val middle = (low + high) ushr 1
            if (track.timeAt(middle) <= timeSeconds) {
                low = middle
            } else {
                high = middle
            }
        }
        return low
    }

    private fun keyframeValue(track: CooFxTrack, keyframeIndex: Int): FloatArray {
        val result = FloatArray(track.componentCount) { component ->
            track.valueAt(keyframeIndex, component)
        }
        return normalizeQuaternionIfNeeded(track, result)
    }

    private fun sampleLinear(
        track: CooFxTrack,
        lowerIndex: Int,
        upperIndex: Int,
        progress: Float
    ): FloatArray {
        if (track.quaternion) {
            return slerp(
                keyframeValue(track, lowerIndex),
                keyframeValue(track, upperIndex),
                progress
            )
        }
        return FloatArray(track.componentCount) { component ->
            val start = track.valueAt(lowerIndex, component)
            start + (track.valueAt(upperIndex, component) - start) * progress
        }
    }

    private fun sampleCubic(
        track: CooFxTrack,
        lowerIndex: Int,
        upperIndex: Int,
        progress: Float,
        segmentDuration: Float
    ): FloatArray {
        val progressSquared = progress * progress
        val progressCubed = progressSquared * progress
        val valueStartFactor = 2F * progressCubed - 3F * progressSquared + 1F
        val tangentStartFactor = progressCubed - 2F * progressSquared + progress
        val valueEndFactor = -2F * progressCubed + 3F * progressSquared
        val tangentEndFactor = progressCubed - progressSquared
        val result = FloatArray(track.componentCount) { component ->
            val startValue = track.valueAt(lowerIndex, component, 1)
            val startOutTangent = track.valueAt(lowerIndex, component, 2) * segmentDuration
            val endValue = track.valueAt(upperIndex, component, 1)
            val endInTangent = track.valueAt(upperIndex, component, 0) * segmentDuration
            valueStartFactor * startValue +
                tangentStartFactor * startOutTangent +
                valueEndFactor * endValue +
                tangentEndFactor * endInTangent
        }
        return normalizeQuaternionIfNeeded(track, result)
    }

    private fun slerp(start: FloatArray, end: FloatArray, progress: Float): FloatArray {
        var dot = start.indices.sumOf { index -> (start[index] * end[index]).toDouble() }.toFloat()
        val adjustedEnd = if (dot < 0F) {
            dot = -dot
            FloatArray(end.size) { index -> -end[index] }
        } else {
            end
        }
        if (dot > 0.9995F) {
            return normalize(FloatArray(start.size) { index ->
                start[index] + (adjustedEnd[index] - start[index]) * progress
            })
        }
        val angle = acos(dot.coerceIn(-1F, 1F))
        val angleSine = sin(angle)
        val startFactor = sin((1F - progress) * angle) / angleSine
        val endFactor = sin(progress * angle) / angleSine
        return normalize(FloatArray(start.size) { index ->
            start[index] * startFactor + adjustedEnd[index] * endFactor
        })
    }

    private fun normalizeQuaternionIfNeeded(track: CooFxTrack, value: FloatArray): FloatArray {
        return if (track.quaternion) normalize(value) else value
    }

    private fun normalize(value: FloatArray): FloatArray {
        val length = sqrt(value.sumOf { component -> (component * component).toDouble() }).toFloat()
        require(length > 0F) { "四元数轨道不能产生零长度值" }
        return FloatArray(value.size) { index -> value[index] / length }
    }
}
