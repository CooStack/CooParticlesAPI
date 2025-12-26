package cn.coostack.cooparticlesapi.annotations.events


@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class EventListener(val modId: String)
