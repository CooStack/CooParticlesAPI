package cn.coostack.cooparticlesapi.event.api

/**
 * 所有自定义事件都要继承 CooEvent
 *
 * ```kotlin
 * data class CustomPlayerEvent(val player: Player): CooEvent()
 * ```
 */
abstract class CooEvent