package cn.coostack.cooparticlesapi.renderer.glow

/**
 * V2 built-in descriptor source for context-aware screen glow.
 *
 * 该接口会在实例级 frame-post 收集阶段被转换成 `RenderEffectDescriptor`，
 * 再由 `RenderEffectGraph` 批量执行到统一的后处理注册表。
 */
interface ScreenGlowContextProvider {
    /**
     * 收集这一帧需要提交的 screen glow 数据。
     *
     * 与 `ScreenGlowProvider` 的区别在于，这里拿到的是已经预计算好的屏幕空间上下文。
     */
    fun collectScreenGlows(context: ScreenGlowRenderContext, output: MutableList<ScreenGlow>)
}
