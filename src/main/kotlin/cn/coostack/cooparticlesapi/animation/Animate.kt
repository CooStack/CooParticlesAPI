package cn.coostack.cooparticlesapi.animation

/**
 * 控制粒子, 实体动画的基类
 * 在客户端 / 服务器都可用
 * 服务器可用的前提下是 修改服务器参数, 然后导致客户端参数变化
 *
 * 客户端可用只会导致在某个玩家下可见
 * 在replay, flashback 均不可见
 */
interface Animate {
    /**
     * 当前播放时间
     * 单位 tick
     */
    var time: Int

    fun play()

    /**
     * 将当前动画跳转到一个时间节点
     *
     * @param to 跳转的时间点 单位tick
     */
    fun goto(to: Int)

    fun stop()

    fun playing(): Boolean

    fun tick()
}