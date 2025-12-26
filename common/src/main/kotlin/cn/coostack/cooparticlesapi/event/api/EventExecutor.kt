package cn.coostack.cooparticlesapi.event.api

import java.util.function.Consumer
import java.util.function.Function

/**
 * 事件执行器
 *
 * @property priority 该执行器的优先级
 * @param executor 事件执行内容
 */
class EventExecutor(val priority: EventPriority, val executor: Consumer<CooEvent>) : Comparable<EventExecutor> {
    override fun compareTo(other: EventExecutor): Int {
        return priority.compareTo(other.priority)
    }
}