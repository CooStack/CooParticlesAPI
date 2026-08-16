package cn.coostack.cooparticlesapi.coofx.playback

import kotlin.math.floor

/**
 * 一次绝对时间映射的结果。
 *
 * @param seconds 映射到 clip 内的秒数
 * @param completed ONCE 模式是否已经到达末端
 * @param cycleIndex 已完成的正向 duration 段数量，开始前固定为零
 */
data class CooFxClipTime(
    val seconds: Float,
    val completed: Boolean,
    val cycleIndex: Long
)

/**
 * 把 Minecraft 逻辑 tick 和渲染 partial tick 映射为 clip 秒数。该时钟不累计浮点增量，
 * 因而相同 startTick、当前 tick 和 partial tick 总是产生相同结果。
 */
class CooFxPlaybackClock(
    val startTick: Long,
    val durationSeconds: Float,
    val speed: Float = 1F,
    val loopMode: CooFxLoopMode = CooFxLoopMode.ONCE
) {
    init {
        require(durationSeconds.isFinite() && durationSeconds > 0F) { "播放时长必须为有限正数" }
        require(speed.isFinite() && speed >= 0F) { "播放速度必须为有限非负数" }
    }

    fun timeAt(tick: Long, partialTick: Float = 0F): CooFxClipTime {
        require(partialTick.isFinite() && partialTick in 0F..1F) { "partial tick 必须位于零到一之间" }
        val elapsedTicks = (tick - startTick).toDouble() + partialTick
        if (elapsedTicks <= 0.0 || speed == 0F) {
            return CooFxClipTime(0F, completed = false, cycleIndex = 0L)
        }
        val elapsedSeconds = elapsedTicks / 20.0 * speed
        val duration = durationSeconds.toDouble()
        val cycle = floor(elapsedSeconds / duration).toLong()
        return when (loopMode) {
            CooFxLoopMode.ONCE -> CooFxClipTime(
                seconds = elapsedSeconds.coerceAtMost(duration).toFloat(),
                completed = elapsedSeconds >= duration,
                cycleIndex = cycle
            )

            CooFxLoopMode.LOOP -> CooFxClipTime(
                seconds = (elapsedSeconds % duration).toFloat(),
                completed = false,
                cycleIndex = cycle
            )

            CooFxLoopMode.PING_PONG -> {
                val position = elapsedSeconds % (duration * 2.0)
                CooFxClipTime(
                    seconds = if (position <= duration) position.toFloat() else (duration * 2.0 - position).toFloat(),
                    completed = false,
                    cycleIndex = cycle
                )
            }
        }
    }
}
