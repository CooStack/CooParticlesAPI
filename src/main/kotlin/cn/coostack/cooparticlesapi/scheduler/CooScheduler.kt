package cn.coostack.cooparticlesapi.scheduler

import java.util.concurrent.ConcurrentLinkedQueue
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
    fun runTaskTimer(delay: Int, runnable: Runnable): TickRunnable {
        val tick = TickRunnable(runnable)
        tick.singleDelay = delay
        tick.loop()
        taskQueue.add(tick)
        return tick
    }

    /**
     * delay个tick后运行
     */
    fun runTask(delay: Int, runnable: Runnable): TickRunnable {
        val tick = TickRunnable(runnable)
        tick.singleDelay = delay
        taskQueue.add(tick)
        return tick
    }

    /**
     * 每tick运行一次
     * 一共运行maxLoopTick次
     */
    fun runTaskTimerMaxTick(maxLoopTick: Int, runnable: Runnable): TickRunnable {
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
    fun runTaskTimerMaxTick(preDelay: Int, maxLoopTick: Int, runnable: Runnable): TickRunnable {
        val tick = TickRunnable(runnable)
        tick.maxTick = maxLoopTick
        tick.singleDelay = preDelay
        tick.loopTimer()
        taskQueue.add(tick)
        return tick
    }

    class TickRunnable(val runnable: TickRunnable.() -> Unit) {
        constructor(task: Runnable) : this({ task.run() })

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

        fun setFinishCallback(callable: Runnable): TickRunnable {
            this.finishCallable = callable
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