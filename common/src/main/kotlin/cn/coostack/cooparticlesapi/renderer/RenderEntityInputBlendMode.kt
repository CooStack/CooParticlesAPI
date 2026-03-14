package cn.coostack.cooparticlesapi.renderer

/**
 * 控制 RenderEntity 写入共享 pipe 输入目标时的混合方式。
 *
 * 这个阶段发生在 pipe 的最终 composite 之前。
 * 当多个 RenderEntity 共用同一个 pipe 时，如果它们都直接往同一个输入 FBO 写内容，
 * 就需要明确它们之间是“覆盖”还是“累加”。
 */
enum class RenderEntityInputBlendMode {
    /**
     * 关闭混合，当前实体直接覆盖它写到的像素。
     * 适合本身就会手动控制 blend，或者必须精确覆盖输入通道的效果。
     */
    REPLACE,

    /**
     * 使用标准 alpha 混合把当前实体写入共享输入 FBO。
     * 适合 glow / distortion mask 这类需要多实例共存，但又不能让同一实体内部片元彼此纯加法放大的效果。
     */
    ALPHA,

    /**
     * 使用加法混合把当前实体累加到共享输入 FBO。
     * 适合 glow / bloom / emissive mask 这类允许多个实体共同叠加的效果。
     */
    ADDITIVE
}
