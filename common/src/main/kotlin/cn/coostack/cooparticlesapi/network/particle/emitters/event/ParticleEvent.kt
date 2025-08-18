package cn.coostack.cooparticlesapi.network.particle.emitters.event

import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.particles.ControlableParticle

interface ParticleEvent {
    /**
     * 触发事件的粒子
     */
    var particle: ControlableParticle
    var particleData: ControlableParticleData

    /**
     * 取消接下来的粒子事件队列
     */
    var canceled: Boolean

    /**
     * 用于方便存储
     * 获取事件ID
     */
    fun getEventID(): String
}