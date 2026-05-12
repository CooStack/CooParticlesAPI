package cn.coostack.cooparticlesapi.annotations.events

import cn.coostack.cooparticlesapi.CooParticlesConstants


@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class EventListener(val modId: String = CooParticlesConstants.MOD_ID)
