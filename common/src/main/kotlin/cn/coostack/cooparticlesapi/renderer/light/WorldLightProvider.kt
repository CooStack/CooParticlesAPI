package cn.coostack.cooparticlesapi.renderer.light

/**
 * V2 built-in descriptor source for world light composite.
 *
 * 它不再依赖帧尾全局实体扫描，而是在实例级别提交到
 * `RenderEffectGraph` 的 descriptor 执行链。
 */
interface WorldLightProvider {
    /**
     * 收集这一帧需要提交的世界光源。
     *
     * @param tickDelta 当前帧部分 tick 插值
     * @param output 把本实体贡献的光源追加到输出列表
     */
    fun collectWorldLights(tickDelta: Float, output: MutableList<WorldLight>)
}
