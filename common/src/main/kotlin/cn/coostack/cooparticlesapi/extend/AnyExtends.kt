package cn.coostack.cooparticlesapi.extend

import cn.coostack.cooparticlesapi.CooParticlesAPI
import cn.coostack.cooparticlesapi.CooParticlesAPIClient
import cn.coostack.cooparticlesapi.scheduler.CooScheduler
import kotlin.reflect.KClass


inline fun <reified T, V> Any.runAsIfType(receiver: T.() -> V?): V? {
    if (this !is T) {
        return null
    }
    return receiver()
}

@Suppress("UNCHECKED_CAST")
inline fun <T : Any, V> Any.runAsIfType(type: KClass<T>, receiver: T.() -> V?): V? {
    if (!type.isInstance(this)) {
        return null
    }
    return receiver(this as T)
}

inline fun <reified T> Any.runAsIfType(receiver: T.() -> Unit) {
    if (this !is T) {
        return
    }
    return receiver()
}

fun Any.submitTaskServer(task: CooScheduler.TickRunnable.() -> Unit): CooScheduler.TickRunnable {
    return submitTask(CooParticlesAPI.scheduler, task)
}

fun Any.submitTaskServer(delay: Int, task: CooScheduler.TickRunnable.() -> Unit): CooScheduler.TickRunnable {
    return submitTask(CooParticlesAPI.scheduler, delay, task)
}

fun Any.submitTaskClient(task: CooScheduler.TickRunnable.() -> Unit): CooScheduler.TickRunnable {
    return submitTask(CooParticlesAPIClient.scheduler, task)
}

fun Any.submitTaskClient(delay: Int, task: CooScheduler.TickRunnable.() -> Unit): CooScheduler.TickRunnable {
    return submitTask(CooParticlesAPIClient.scheduler, delay, task)
}

fun Any.submitTaskTimerServer(delay: Int, task: CooScheduler.TickRunnable.() -> Unit): CooScheduler.TickRunnable {
    return submitTaskTimer(CooParticlesAPI.scheduler, delay, task)
}

fun Any.submitTaskTimerClient(delay: Int, task: CooScheduler.TickRunnable.() -> Unit): CooScheduler.TickRunnable {
    return submitTaskTimer(CooParticlesAPIClient.scheduler, delay, task)
}

fun Any.submitTaskTimerMaxTickServer(
    maxLoopTick: Int,
    task: CooScheduler.TickRunnable.() -> Unit,
): CooScheduler.TickRunnable {
    return submitTaskTimerMaxTick(CooParticlesAPI.scheduler, maxLoopTick, task)
}

fun Any.submitTaskTimerMaxTickServer(
    preDelay: Int,
    maxLoopTick: Int,
    task: CooScheduler.TickRunnable.() -> Unit,
): CooScheduler.TickRunnable {
    return submitTaskTimerMaxTick(CooParticlesAPI.scheduler, preDelay, maxLoopTick, task)
}

fun Any.submitTaskTimerMaxTickClient(
    maxLoopTick: Int,
    task: CooScheduler.TickRunnable.() -> Unit,
): CooScheduler.TickRunnable {
    return submitTaskTimerMaxTick(CooParticlesAPIClient.scheduler, maxLoopTick, task)
}

fun Any.submitTaskTimerMaxTickClient(
    preDelay: Int,
    maxLoopTick: Int,
    task: CooScheduler.TickRunnable.() -> Unit,
): CooScheduler.TickRunnable {
    return submitTaskTimerMaxTick(CooParticlesAPIClient.scheduler, preDelay, maxLoopTick, task)
}

fun Any.repeatTasksServer(count: Int, totalTick: Int, task: CooScheduler.TickRunnable.() -> Unit) {
    repeatTasks(CooParticlesAPI.scheduler, count, totalTick, task)
}

fun Any.repeatTasksClient(count: Int, totalTick: Int, task: CooScheduler.TickRunnable.() -> Unit) {
    repeatTasks(CooParticlesAPIClient.scheduler, count, totalTick, task)
}

private fun submitTask(
    scheduler: CooScheduler,
    task: CooScheduler.TickRunnable.() -> Unit,
): CooScheduler.TickRunnable {
    return submitTask(scheduler, 0, task)
}

private fun submitTask(
    scheduler: CooScheduler,
    delay: Int,
    task: CooScheduler.TickRunnable.() -> Unit,
): CooScheduler.TickRunnable {
    return scheduler.runTask(delay, task)
}

private fun submitTaskTimer(
    scheduler: CooScheduler,
    delay: Int,
    task: CooScheduler.TickRunnable.() -> Unit,
): CooScheduler.TickRunnable {
    return scheduler.runTaskTimer(delay, task)
}

private fun submitTaskTimerMaxTick(
    scheduler: CooScheduler,
    maxLoopTick: Int,
    task: CooScheduler.TickRunnable.() -> Unit,
): CooScheduler.TickRunnable {
    return scheduler.runTaskTimerMaxTick(maxLoopTick, task)
}

private fun submitTaskTimerMaxTick(
    scheduler: CooScheduler,
    preDelay: Int,
    maxLoopTick: Int,
    task: CooScheduler.TickRunnable.() -> Unit,
): CooScheduler.TickRunnable {
    return scheduler.runTaskTimerMaxTick(preDelay, maxLoopTick, task)
}

private fun repeatTasks(
    scheduler: CooScheduler,
    count: Int,
    totalTick: Int,
    task: CooScheduler.TickRunnable.() -> Unit,
) {
    scheduler.repeatTasks(count, totalTick, task)
}
