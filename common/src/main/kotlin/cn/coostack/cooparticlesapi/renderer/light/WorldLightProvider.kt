package cn.coostack.cooparticlesapi.renderer.light

/**
 * V2 built-in FrameEffect source for world light composite.
 *
 * 它不再依赖帧尾全局实体扫描，而是在实例级别提交到 `FrameEffectStack`。
 */
interface WorldLightProvider {
    fun collectWorldLights(tickDelta: Float, output: MutableList<WorldLight>)
}
