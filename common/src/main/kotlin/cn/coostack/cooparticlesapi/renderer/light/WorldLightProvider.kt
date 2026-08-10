package cn.coostack.cooparticlesapi.renderer.light

/** 为当前渲染实例提供世界光源，由 Pipeline runtime 在对应帧阶段收集。 */
interface WorldLightProvider {
    /**
     * 收集这一帧需要提交的世界光源。
     *
     * @param tickDelta 当前帧部分 tick 插值
     * @param output 把本实体贡献的光源追加到输出列表
     */
    fun collectWorldLights(tickDelta: Float, output: MutableList<WorldLight>)
}
