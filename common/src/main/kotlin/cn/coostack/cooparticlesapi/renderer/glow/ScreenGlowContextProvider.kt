package cn.coostack.cooparticlesapi.renderer.glow

/**
 * V2 built-in frame effect source for context-aware screen glow.
 *
 * 该接口会在实例级 frame-effect 收集阶段提交到 `FrameEffectStack`。
 */
interface ScreenGlowContextProvider {
    fun collectScreenGlows(context: ScreenGlowRenderContext, output: MutableList<ScreenGlow>)
}
