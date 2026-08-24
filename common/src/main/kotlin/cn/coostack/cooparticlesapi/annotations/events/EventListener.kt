package cn.coostack.cooparticlesapi.annotations.events

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.enums.DistType


@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class EventListener(
    val modId: String = CooParticlesConstants.MOD_ID,
    val dist: DistType = DistType.BOTH,
)
