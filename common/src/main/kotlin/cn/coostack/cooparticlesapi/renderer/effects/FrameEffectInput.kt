package cn.coostack.cooparticlesapi.renderer.effects

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance

data class FrameEffectInput<T : RenderEntity>(
    val instance: RenderEntityInstance<T>,
    val frameContext: RenderFrameContext
)
