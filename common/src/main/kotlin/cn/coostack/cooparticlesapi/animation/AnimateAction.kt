package cn.coostack.cooparticlesapi.animation

abstract class AnimateAction {
    var done = false

    /**
     * 在加入一个node时， 在这个node需要多少个tick才会开始播放
     * 相当于时间间隔
     */
    var timeStart = 0

    var tickCount = 0

    /**
     * 检测这个分支是否完成
     *
     * @return
     */
    abstract fun checkDone(): Boolean

    abstract fun tick()


    fun doTick() {
        tick()
        tickCount++
    }

    /**
     * 对动画进行初始化在这里执行
     *
     * 所有动画元素必须在这里可以得到全部重置
     * 否则二次播放就会出问题
     *
     */
    abstract fun onStart()

    /**
     * 执行完毕进行数据释放 (比如删除一些多余的发射器什么的)
     *
     */
    abstract fun onDone()

    /**
     * done 备忘录
     *
     * @return 是否执行完成
     */
    fun check(): Boolean {
        if (!done && checkDone()) {
            done = true
        }
        return done
    }

}