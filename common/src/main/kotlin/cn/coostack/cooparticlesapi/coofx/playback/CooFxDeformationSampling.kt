package cn.coostack.cooparticlesapi.coofx.playback

import cn.coostack.cooparticlesapi.coofx.playback.track.CooFxTrack
import cn.coostack.cooparticlesapi.coofx.playback.track.CooFxTrackSampler
import kotlin.math.floor

/** VAT 的相邻帧与帧间插值权重。 */
data class CooFxVatFrameSample(val currentFrame: Int, val nextFrame: Int, val progress: Float)

/** 按秒采样 VAT 帧，不累计帧进度。 */
object CooFxVatFrameSampler {
    fun sample(timeSeconds: Float, frameCount: Int, framesPerSecond: Float, loopMode: CooFxLoopMode): CooFxVatFrameSample {
        require(timeSeconds.isFinite()) { "VAT 采样时间必须为有限数" }
        require(frameCount > 0) { "VAT 至少需要一帧" }
        require(framesPerSecond.isFinite() && framesPerSecond > 0F) { "VAT 帧率必须为有限正数" }
        if (frameCount == 1) return CooFxVatFrameSample(0, 0, 0F)
        val rawFrame = (timeSeconds.coerceAtLeast(0F) * framesPerSecond).toDouble()
        val framePosition = when (loopMode) {
            CooFxLoopMode.ONCE -> rawFrame.coerceAtMost((frameCount - 1).toDouble())
            CooFxLoopMode.LOOP -> rawFrame % frameCount
            CooFxLoopMode.PING_PONG -> {
                val last = (frameCount - 1).toDouble()
                val position = rawFrame % (last * 2.0)
                if (position <= last) position else last * 2.0 - position
            }
        }
        val current = floor(framePosition).toInt()
        val next = when (loopMode) {
            CooFxLoopMode.LOOP -> (current + 1) % frameCount
            else -> (current + 1).coerceAtMost(frameCount - 1)
        }
        return CooFxVatFrameSample(current, next, (framePosition - current).toFloat())
    }
}

/** glTF morph weights 的采样计划，分量数固定等于目标数量。 */
class CooFxMorphWeightTrack(val nodeIndex: Int, val targetCount: Int, val track: CooFxTrack) {
    init {
        require(nodeIndex >= 0) { "morph 节点索引不能为负数" }
        require(targetCount > 0 && track.componentCount == targetCount && !track.quaternion) { "morph 轨道分量数必须等于目标数量" }
    }

    fun sample(timeSeconds: Float): FloatArray = CooFxTrackSampler.sample(track, timeSeconds)
}

/** 把粒子年龄映射到零到一范围后采样的生命周期标量曲线。 */
class CooFxLifecycleCurve(private val track: CooFxTrack) {
    init {
        require(track.componentCount == 1 && !track.quaternion) { "生命周期曲线必须是单分量标量轨道" }
        require(track.startTimeSeconds == 0F && track.endTimeSeconds == 1F) { "生命周期曲线时间域必须是零到一" }
    }

    fun sample(ageTicks: Float, lifetimeTicks: Float): Float {
        require(ageTicks.isFinite() && lifetimeTicks.isFinite() && lifetimeTicks > 0F) { "生命周期参数必须为有限数且总时长为正数" }
        return CooFxTrackSampler.sample(track, (ageTicks / lifetimeTicks).coerceIn(0F, 1F))[0]
    }
}
