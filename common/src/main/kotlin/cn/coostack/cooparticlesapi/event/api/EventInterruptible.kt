package cn.coostack.cooparticlesapi.event.api

/**
 * 硬中断
 * 如果事件实现了这个并且让他为true
 * 那么不会执行在哪之后的事件处理器
 */
interface EventInterruptible {
    var hasInterrupted: Boolean
}