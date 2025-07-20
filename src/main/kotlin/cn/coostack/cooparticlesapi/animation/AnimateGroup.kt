package cn.coostack.cooparticlesapi.animation

/**
 * 动画组合
 *
 * 通过不同的逻辑, 监听, 随机, 顺序 用于作用动画上
 */
interface AnimateGroup {
    val animates: MutableList<Animate>

    var current: Int

    var time: Int

    fun next()
    fun hasNext(): Boolean

    /**
     * 重置到 0
     */
    fun reset()

    /**
     * 设置当前播放的动画帧
     */
    fun playAt(index: Int)

    fun play()

    fun stop()

    fun playing(): Boolean

    fun tick()
}