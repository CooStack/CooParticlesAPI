package cn.coostack.cooparticlesapi.coofx.playback

/**
 * 保存逻辑 tick 的前一帧和当前帧 clip 时间。advance 只接受整数 tick；渲染侧 partial tick
 * 应通过 clock 单独采样，不能改变这里的逻辑状态。
 */
class CooFxPlaybackState(private val clock: CooFxPlaybackClock) {
    var previous: CooFxClipTime = clock.timeAt(clock.startTick)
        private set
    var current: CooFxClipTime = previous
        private set
    var currentTick: Long = clock.startTick
        private set

    fun advance(tick: Long): CooFxClipTime {
        require(tick >= currentTick) { "播放状态不能倒退逻辑 tick" }
        if (tick != currentTick) {
            previous = current
            current = clock.timeAt(tick)
            currentTick = tick
        }
        return current
    }

    fun renderTime(partialTick: Float): CooFxClipTime = clock.timeAt(currentTick, partialTick)
}
