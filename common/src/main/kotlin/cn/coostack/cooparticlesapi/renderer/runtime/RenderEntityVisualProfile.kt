package cn.coostack.cooparticlesapi.renderer.runtime

enum class CompositeMode {
    REPLACE,
    ALPHA,
    ADDITIVE
}

data class RenderEntityVisualProfile(
    val compositeMode: CompositeMode = CompositeMode.REPLACE,
    val needsSceneColorCopy: Boolean = false,
    val needsSceneDepth: Boolean = false,
    val localChainEnabled: Boolean = false,
    val frameEffectsEnabled: Boolean = false,
    val renderPriority: Int = 0
)
