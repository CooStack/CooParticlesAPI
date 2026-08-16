package cn.coostack.cooparticlesapi.coofx.playback

/**
 * 在固定 tick 区间内均匀分配固定数量发射的确定性计划。durationTicks 为一时等价于 burst；
 * 其他时长使用整数累计差值分配，不受浮点误差或调用频率影响。
 */
class CooFxEmissionSchedule(
    val delayTicks: Int,
    val count: Long,
    val durationTicks: Int = 1
) {
    init {
        require(delayTicks >= 0) { "发射延迟不能为负数" }
        require(count >= 0L) { "发射数量不能为负数" }
        require(durationTicks > 0) { "发射区间必须至少包含一个 tick" }
    }

    fun emissionsAt(ageTick: Int): LongRange {
        if (ageTick < delayTicks || ageTick >= delayTicks + durationTicks || count == 0L) {
            return LongRange.EMPTY
        }
        val relativeTick = ageTick - delayTicks
        val firstOrdinal = relativeTick.toLong() * count / durationTicks
        val nextOrdinal = (relativeTick + 1L) * count / durationTicks
        return if (firstOrdinal < nextOrdinal) firstOrdinal until nextOrdinal else LongRange.EMPTY
    }

    fun isComplete(ageTick: Int): Boolean = ageTick >= delayTicks + durationTicks
}
