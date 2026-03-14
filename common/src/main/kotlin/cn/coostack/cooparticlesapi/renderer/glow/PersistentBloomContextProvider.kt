package cn.coostack.cooparticlesapi.renderer.glow

/**
 * 提供“持久辉光采样层”的 V2 FrameEffect 扩展接口。
 *
 * 与 `ScreenGlowProvider` 的区别是：
 * - `PersistentBloom` 更偏向帧尾 blur 后再 composite 的稳定外辉光
 * - 适合能量球、法阵核心、远处仍需保持存在感的亮源
 *
 * 调用时机在实体实例的 frame-effect 收集阶段，
 * 最终由 `FrameEffectStack` 调度到 `ClientPersistentBloomManager`。
 */
interface PersistentBloomContextProvider {
    fun collectPersistentBlooms(context: ScreenGlowRenderContext, output: MutableList<PersistentBloom>)
}
