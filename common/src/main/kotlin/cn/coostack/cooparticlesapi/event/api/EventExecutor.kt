package cn.coostack.cooparticlesapi.event.api

import java.util.function.Consumer
import java.util.function.Function

/**
 * 事件执行器
 *
 * @param modId 该事件监听器的所属mod
 * @param executor 事件执行内容
 */
class EventExecutor(val modId: String, val executor: Consumer<CooEvent>)