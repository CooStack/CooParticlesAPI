package cn.coostack.cooparticlesapi.renderer.glow

/**
 * V2 built-in descriptor source for screen glow.
 *
 * 该接口现在由 `RenderEntityInstance.collectRenderContributions(...)`
 * 在每个实例级别转成 `RenderEffectDescriptor`，而不是再依赖全局实体扫描。
 */
interface ScreenGlowProvider {
    /**
     * 收集这一帧需要提交的 screen glow 数据。
     *
     * @param tickDelta 当前帧的部分 tick 插值
     * @param output 把本实体生成的 glow 追加到这个输出列表
     */
    fun collectScreenGlows(tickDelta: Float, output: MutableList<ScreenGlow>)
}
