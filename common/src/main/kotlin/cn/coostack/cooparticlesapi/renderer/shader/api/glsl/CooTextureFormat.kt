package cn.coostack.cooparticlesapi.renderer.shader.api.glsl

/**
 * Pipeline 颜色 attachment 的存储格式。
 *
 * 格式会直接决定后处理 FBO 的内部精度。输入端口可以用它声明期望格式，输出端口则用它
 * 声明实际分配格式；编译器会校验能够静态解析的节点连线。场景颜色等外部纹理无法在构建
 * 阶段确定格式时，可以不声明输入期望格式。
 */
enum class CooTextureFormat {
    /** 每通道 8 位无符号归一化颜色，适合最终 SDR 颜色或不需要 HDR 的中间结果。 */
    RGBA8,

    /** 每通道 16 位浮点颜色，适合 Bloom、曝光和其他需要保留超出 0 到 1 范围的 HDR 中间结果。 */
    RGBA16F,

    /** 每通道 32 位浮点颜色，适合确实需要更高精度的计算目标，显存和带宽开销也最高。 */
    RGBA32F
}
