package cn.coostack.cooparticlesapi.renderer.runtime

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.effects.FrameEffectCollector
import cn.coostack.cooparticlesapi.renderer.effects.FrameEffectInput

interface RenderEntityRenderer<T : RenderEntity> {
    fun createVisualProfile(entity: T): RenderEntityVisualProfile {
        return RenderEntityVisualProfile()
    }

    fun initialize(instance: RenderEntityInstance<T>) {
    }

    fun update(instance: RenderEntityInstance<T>, entity: T) {
    }

    fun renderLocal(input: LocalRenderInput<T>) {
    }

    fun collectFrameEffects(input: FrameEffectInput<T>, collector: FrameEffectCollector) {
    }

    fun release(instance: RenderEntityInstance<T>) {
    }
}
