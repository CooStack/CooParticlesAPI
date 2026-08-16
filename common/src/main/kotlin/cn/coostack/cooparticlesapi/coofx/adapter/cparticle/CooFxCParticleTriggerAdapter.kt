package cn.coostack.cooparticlesapi.coofx.adapter.cparticle

import cn.coostack.cooparticlesapi.coofx.adapter.CooFxEmitterRequest

/**
 * 把现有 CParticle 或 Composition 触发上下文单向转换为 CooFX emitter 请求。
 *
 * 适配器不读取或写入 CParticleStore 的 36-float ABI，不共享实例缓冲，也不推进 CooFX 模拟。
 * 返回 null 表示当前触发不应启动 CooFX emitter。
 */
fun interface CooFxCParticleTriggerAdapter<T : Any> {
    fun createEmitterRequest(trigger: T): CooFxEmitterRequest?
}
