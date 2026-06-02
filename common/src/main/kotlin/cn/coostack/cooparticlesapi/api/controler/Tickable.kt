package cn.coostack.cooparticlesapi.api.controler

/**
 * 可参与逻辑 tick 的基础接口。
 *
 * `RenderEntity`、粒子组等需要被管理器逐帧推进的对象都会复用这套抽象。
 */
interface Tickable<T> {
    /**
     * 添加一个在正式 `tick()` 之前执行的动作。
     *
     * 具体实现是否支持 action 链由各类型自行决定。
     */
    fun addPreTickAction(action: T.() -> Unit): Tickable<T>

    /**
     * 在tick之后， 执行这个tick队列
     * 一般用于部分的位移实现， 因为位置修改不能在prev = pos之前制作的此实现
     */
    fun addPreTickActionPost(action: T.() -> Unit): Tickable<T>

    /**
     * 推进一次逻辑 tick。
     */
    fun tick()
}
