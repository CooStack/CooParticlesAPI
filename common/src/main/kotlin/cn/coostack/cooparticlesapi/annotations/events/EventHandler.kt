package cn.coostack.cooparticlesapi.annotations.events

import cn.coostack.cooparticlesapi.event.api.EventPriority

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class EventHandler(val priority: EventPriority = EventPriority.NORMAL)
