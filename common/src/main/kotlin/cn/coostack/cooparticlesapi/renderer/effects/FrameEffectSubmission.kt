package cn.coostack.cooparticlesapi.renderer.effects

import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability

data class FrameEffectSubmission(
    val effectId: String,
    val priority: Int = 0,
    val sourceInstanceId: String = "",
    val requiredCapabilities: Set<RenderBackendCapability> = emptySet(),
    val render: () -> Unit
)
