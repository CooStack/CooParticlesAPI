package cn.coostack.cooparticlesapi.event.events.particle.emitter

import cn.coostack.cooparticlesapi.event.api.CooEvent
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters

/**
 * 当粒子从玩家可视中移除时就会执行
 * Client & Server
 *
 * @property emitter
 */
class EmitterRemoveEvent(val emitter: ParticleEmitters, val isClientSide: Boolean) : CooEvent()
