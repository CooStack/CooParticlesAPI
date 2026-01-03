package cn.coostack.cooparticlesapi.event.events.particle.emitter

import cn.coostack.cooparticlesapi.event.api.CooEvent
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters

/**
 * 当粒子生成在可视范围时执行
 * Client && Server
 *
 * @property emitter
 * @property isClientSide
 */
class EmitterSpawnEvent(val emitter: ParticleEmitters, val isClientSide: Boolean) : CooEvent()
