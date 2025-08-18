package cn.coostack.cooparticlesapi.network.particle.emitters.event

/**
 * 自动注册
 * 事件处理器是单例模式
 */
interface ParticleEventHandler : Comparable<ParticleEventHandler> {

    fun handle(event: ParticleEvent)

    /**
     * 详细见
     * 目标事件的EventID
     * @see ParticleEvent.getEventID
     */
    fun getTargetEventID(): String

    fun getHandlerID(): String

    fun getPriority(): Int

    override fun compareTo(other: ParticleEventHandler): Int {
        return getPriority() - other.getPriority()
    }

}