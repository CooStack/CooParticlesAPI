package cn.coostack.cooparticlesapi.animation

/**
 * 动画系统的叶子执行单元。
 *
 * 自定义复杂动作可以继续继承本类。框架会在每次播放时依次执行
 * [onStart]、零次或多次 [tick]、[onDone]。[onDone] 在正常完成、取消或跳过时
 * 最多执行一次；尚未执行 [onStart] 的动作被取消时不会执行 [onDone]。
 */
abstract class AnimateAction {
    private enum class RuntimeState {
        PENDING,
        RUNNING,
        FINISHED,
    }

    /** 当前动作是否已经结束。 */
    var done = false

    /**
     * Node 启动后，延迟多少 tick 才推进本动作。
     *
     * 这是兼容旧 Node API 的字段。它不会延迟 [onStart]，也不表示动作持续时间。
     * 链式编排应使用 `then(action, delayTicks)`。
     */
    var timeInterval = 0

    /** 当前播放中已经执行 [tick] 的次数。重播时会重置为 0。 */
    var tickCount = 0

    private var runtimeState = RuntimeState.PENDING
    private var doneCallbackInvoked = false
    private var ticking = false

    /**
     * 判断动作是否已经正常完成。
     *
     * 框架在每个活跃 tick 推进前最多调用一次。用户条件仍应保持无副作用；普通条件等待、
     * 循环和分支选择应使用框架提供的组合 API。
     */
    abstract fun checkDone(): Boolean

    /** 推进动作一个活跃 tick。 */
    abstract fun tick()

    /** 执行一次 [tick]，然后递增 [tickCount]。 */
    fun doTick() {
        tick()
        tickCount++
    }

    /**
     * 动作启动时调用。
     *
     * 自定义实现应在这里重置自身状态，以便同一个 `Animate` 可以再次播放。
     */
    abstract fun onStart()

    /**
     * 已启动的动作结束时调用。
     *
     * 正常完成、取消和跳过共用此回调，框架保证每次播放最多调用一次。
     */
    abstract fun onDone()

    /**
     * 检查并缓存完成状态。
     *
     * @return 动作是否已经完成
     */
    fun check(): Boolean {
        if (!done && checkDone()) {
            if (runtimeState == RuntimeState.RUNNING) {
                finishRuntime()
            } else {
                done = true
            }
        }
        return done
    }

    /**
     * 取消动作。
     *
     * 已启动的动作会结束嵌套执行并调用一次 [onDone]；待启动动作只会标记为结束。
     */
    fun cancel() {
        if (runtimeState == RuntimeState.RUNNING) {
            (this as? NestedActionController)?.cancelNestedActions()
            done = true
            if (!ticking) {
                finishRuntime()
            }
        } else if (runtimeState == RuntimeState.PENDING) {
            done = true
            runtimeState = RuntimeState.FINISHED
        }
    }

    internal fun startRuntime() {
        done = false
        tickCount = 0
        doneCallbackInvoked = false
        ticking = false
        runtimeState = RuntimeState.RUNNING
        onStart()
    }

    internal fun tickRuntime() {
        if (checkRuntime()) {
            return
        }

        ticking = true
        try {
            doTick()
        } finally {
            ticking = false
        }
        if (runtimeState == RuntimeState.RUNNING && done) {
            finishRuntime()
        }
    }

    internal fun checkRuntime(): Boolean {
        if (runtimeState == RuntimeState.RUNNING && (done || checkDone())) {
            finishRuntime()
        }
        return runtimeState != RuntimeState.RUNNING
    }

    internal fun skipRuntime() {
        if (runtimeState == RuntimeState.RUNNING) {
            (this as? NestedActionController)?.skipNestedActions()
            finishRuntime()
        } else if (runtimeState == RuntimeState.PENDING) {
            done = true
            runtimeState = RuntimeState.FINISHED
        }
    }

    private fun finishRuntime() {
        done = true
        runtimeState = RuntimeState.FINISHED
        if (!doneCallbackInvoked) {
            doneCallbackInvoked = true
            onDone()
        }
    }
}

internal interface NestedActionController {
    fun cancelNestedActions()

    fun skipNestedActions()
}
