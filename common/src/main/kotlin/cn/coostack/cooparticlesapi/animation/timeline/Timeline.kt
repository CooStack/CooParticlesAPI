package cn.coostack.cooparticlesapi.animation.timeline

/**
 * 简易 Animate
 * 不需要手写Action的实现 tick由自己管理
 *
 * 一般用于简易的动画线中, 不支持多路并行
 */
class Timeline {
    private val steps = ArrayDeque<() -> Boolean>()

    /**
     * 设置一个步骤
     *
     * @param block 如果返回true 则代表时间线已经完成，就会进入下一个时间
     */
    fun step(block: () -> Boolean) = apply {
        steps.add(block)
    }

    fun doTick() {
        if (steps.isEmpty()) return
        val done = steps.first().invoke()
        if (done) steps.removeFirst()
    }
}