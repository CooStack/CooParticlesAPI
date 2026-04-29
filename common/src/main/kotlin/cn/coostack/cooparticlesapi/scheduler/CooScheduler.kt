package cn.coostack.cooparticlesapi.scheduler

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Predicate
import kotlin.math.sin

class CooScheduler {
    internal val ticks = ConcurrentLinkedQueue<TickRunnable>()
    internal val taskQueue = ConcurrentLinkedQueue<TickRunnable>()
    internal fun doTick() {
        val iterator = ticks.iterator()
        while (iterator.hasNext()) {
            val tick = iterator.next()
            tick.doTick()
            if (tick.canceled) {
                iterator.remove()
            }
        }
        ticks.addAll(taskQueue)
        taskQueue.clear()
    }

    /**
     * 每 delay 个tick运行一次
     */
    fun runTaskTimer(delay: Int, runnable: TickRunnable.() -> Unit): TickRunnable {
        val tick = TickRunnable(runnable)
        tick.singleDelay = delay
        tick.loop()
        taskQueue.add(tick)
        return tick
    }


    /**
     * 循环执行， 假设一共需要执行15次 （count = 15） 然后在totalTick=5 内执行完， 那么每tick就会执行3次
     *
     * @param count 一共需要执行的次数
     * @param totalTick 总tick数
     * @param runnable
     * @return
     */
    fun repeatTasks(count: Int, totalTick: Int, runnable: TickRunnable.() -> Unit) {
        val fixedCount = count.coerceAtLeast(1)
        val fixedTotalTick = totalTick.coerceAtLeast(1)


        if (fixedTotalTick > fixedCount) {
            // Bresenham: 将 fixedCount 次执行均匀分布到 fixedTotalTick 个tick中
            var error = 0
            runTaskTimerMaxTick(1, fixedTotalTick) {
                error += fixedCount
                if (error >= fixedTotalTick) {
                    runnable()
                    error -= fixedTotalTick
                }
            }
            return
        }

        val preTickCount = fixedCount / fixedTotalTick
        val remainder = fixedCount % fixedTotalTick

        if (remainder > 0) {
            lateinit var tick: TickRunnable
            tick = runTaskTimerMaxTick(totalTick - 1) {
                repeat(preTickCount) {
                    runnable()
                }
            }.setFinishCallback {
                repeat(preTickCount + remainder) {
                    runnable(tick)
                }
            }
        } else {
            runTaskTimerMaxTick(totalTick) {
                repeat(preTickCount) {
                    runnable()
                }
            }
        }
    }

    /**
     * delay个tick后运行
     */
    fun runTask(delay: Int, runnable: TickRunnable.() -> Unit): TickRunnable {
        val tick = TickRunnable(runnable)
        tick.singleDelay = delay
        taskQueue.add(tick)
        return tick
    }

    /**
     * 每tick运行一次
     * 一共运行maxLoopTick次
     */
    fun runTaskTimerMaxTick(maxLoopTick: Int, runnable: TickRunnable.() -> Unit): TickRunnable {
        val tick = TickRunnable(runnable)
        tick.maxTick = maxLoopTick
        tick.loopTimer()
        taskQueue.add(tick)
        return tick
    }

    /**
     * @param preDelay 每次执行的延时
     * @param maxLoopTick 最大执行到
     * 每preDelay运行一次
     * 一共运行maxLoopTick次
     */
    fun runTaskTimerMaxTick(preDelay: Int, maxLoopTick: Int, runnable: TickRunnable.() -> Unit): TickRunnable {
        val tick = TickRunnable(runnable)
        tick.maxTick = maxLoopTick
        tick.singleDelay = preDelay
        tick.loopTimer()
        taskQueue.add(tick)
        return tick
    }

    class TickRunnable(val runnable: TickRunnable.() -> Unit) {
        /**
         * loopTimer为true时 启用
         * 代表执行的最大Tick (singleDelay + currentTick > maxTick && currentTick < maxTick 时也会执行)
         */
        internal var maxTick = 0

        /**
         * 单吃执行的时间间隔
         * looped loopTimer 都为false时代表一次task的延时执行的时间
         */
        internal var singleDelay = 1
        private var currentTick = 0
        var canceled = false
            private set

        private var looped = false
        private var finishCallable: Runnable = Runnable {}
        private var cancelPredicate: Predicate<TickRunnable> = Predicate { false }
        fun setFinishCallback(callable: Runnable): TickRunnable {
            this.finishCallable = callable
            return this
        }


        fun setCancelPredicate(predicate: Predicate<TickRunnable>): TickRunnable {
            this.cancelPredicate = predicate
            return this
        }

        /**
         * 每singleDelay tick执行一次
         * 执行到maxTick结束
         */
        private var loopTimer = false
        fun loop(): TickRunnable {
            looped = true
            currentTick = maxTick
            return this
        }

        /**
         * 每singleDelay tick执行一次
         * 执行到maxTick结束
         */
        fun loopTimer(): TickRunnable {
            loopTimer = true
            return this
        }

        fun cancel() {
            canceled = true
        }

        fun doTick() {
            if (canceled) {
                return
            }

            if (loopTimer) {
                val canInvoke = currentTick++ % singleDelay == 0
                if (canInvoke) {
                    runnable(this)
                }
                if (cancelPredicate.test(this)) {
                    canceled = true
                    finishCallable.run()
                    return
                }
                if (currentTick >= maxTick) {
                    canceled = true
                    finishCallable.run()
                    return
                }
                return
            }

            if (looped) {
                if (currentTick++ >= singleDelay) {
                    runnable(this)
                    if (cancelPredicate.test(this)) {
                        canceled = true
                        finishCallable.run()
                    }
                    currentTick = 0
                }
                return
            }
            if (currentTick++ >= singleDelay) {
                runnable(this)
                finishCallable.run()
                canceled = true
            }
            return
        }
    }
}
