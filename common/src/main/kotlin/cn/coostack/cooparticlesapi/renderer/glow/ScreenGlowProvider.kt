package cn.coostack.cooparticlesapi.renderer.glow

/**
 * V2 built-in frame effect source for screen glow.
 *
 * 该接口现在由 `RenderEntityInstance.collectFrameEffects(...)`
 * 在每个实例级别转成 `FrameEffectSubmission`，而不是再依赖全局实体扫描。
 */
interface ScreenGlowProvider {
    fun collectScreenGlows(tickDelta: Float, output: MutableList<ScreenGlow>)
}
