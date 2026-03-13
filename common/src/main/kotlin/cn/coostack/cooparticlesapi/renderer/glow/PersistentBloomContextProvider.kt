package cn.coostack.cooparticlesapi.renderer.glow

/**
 * 提供“持久辉光采样层”的 RenderEntity 扩展接口。
 *
 * 与 `ScreenGlowProvider` 的区别是：
 * - `PersistentBloom` 更偏向帧尾 blur 后再 composite 的稳定外辉光
 * - 适合能量球、法阵核心、远处仍需保持存在感的亮源
 *
 * 调用时机在实体本体 render 之后，由 `ClientPersistentBloomManager` 统一收集。
 */
interface PersistentBloomContextProvider {
    fun collectPersistentBlooms(context: ScreenGlowRenderContext, output: MutableList<PersistentBloom>)
}
