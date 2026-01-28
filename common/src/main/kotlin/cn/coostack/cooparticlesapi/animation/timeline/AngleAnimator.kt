package cn.coostack.cooparticlesapi.animation.timeline

/**
 * 角度生长动画器
 * 用来制作角度变换的
 *
 * @property durationTicks 生长总时长
 * @property targetAngle 目标角度 (一般使用 index * PI * 2 / count 计算)
 * @property ease 变换算法
 * @see cn.coostack.cooparticlesapi.test.options.particle.composition.TestGlowingAnimationComposition
 */
class AngleAnimator(
    private val durationTicks: Int,
    private val targetAngle: Double,
    private val ease: Ease = Eases.linear
) {
    private var tick = 0
    private var lastAngle = 0.0
    var finished = false
        private set

    /** 返回本 tick 需要追加旋转的 deltaAngle */
    fun glowDelta(): Double {
        if (finished) return 0.0

        tick++
        val t = (tick.toDouble() / durationTicks.toDouble()).coerceIn(0.0, 1.0)
        val eased = ease.cal(t)
        val angleNow = targetAngle * eased
        val delta = angleNow - lastAngle
        lastAngle = angleNow

        if (tick >= durationTicks) finished = true
        return delta
    }

    /**
     * 反向变化（消失动画）
     */
    fun fadeDelta(): Double {
        // 如果 glow 已经结束了（finished=true 且 tick>=durationTicks），
        // 说明现在是第一次开始 fade：把状态拉回到 fade 的起点。
        if (finished && tick >= durationTicks) {
            finished = false
            tick = 0
            lastAngle = targetAngle
        } else if (finished) {
            // 其他情况下 finished=true 就真的是 fade 也跑完了
            return 0.0
        }

        tick++
        val t = (tick.toDouble() / durationTicks.toDouble()).coerceIn(0.0, 1.0)

        // 时间反向：t=0 -> eased=1 -> angle=targetAngle；t=1 -> eased=0 -> angle=0
        val eased = ease.cal(1.0 - t)
        val angleNow = targetAngle * eased

        val delta = angleNow - lastAngle
        lastAngle = angleNow

        if (tick >= durationTicks) finished = true
        return delta
    }

    fun reset() {
        tick = 0
        lastAngle = 0.0
        finished = false
    }
}