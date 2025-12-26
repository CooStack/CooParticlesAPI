package cn.coostack.cooparticlesapi.event.api

/**
 * 提供给事件的Cancelable
 * 不过也可以自己写状态
 */
interface EventCancelable {
    var isCancelled: Boolean
}