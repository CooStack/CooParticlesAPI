package cn.coostack.cooparticlesapi.renderer.backend

/**
 * backend 可声明的渲染能力。
 *
 * Pipeline compiler 和 runtime 会据此判断当前效果是否可以执行。
 */
enum class RenderBackendCapability {
    /**
     * backend 能提供场景颜色缓冲的可读副本。
     *
     * 适合依赖“原场景颜色输入”的 glow、bloom、折射等效果。
     */
    SCENE_COLOR_COPY,

    /**
     * backend 能安全读取场景深度。
     *
     * 适合需要遮挡判断、深度衰减、基于深度的边缘处理的效果。
     */
    SCENE_DEPTH_READ,

    /**
     * backend 能在世界绘制与后处理之间安全完成合成切换。
     *
     * 当一个效果既涉及 world pass 又涉及 frame-post 时，这项能力很关键。
     */
    SAFE_WORLD_COMPOSITE,

    /**
     * backend 允许在真正帧尾执行统一的最终 post 处理阶段。
     *
     * 这是帧尾 Pipeline 和 ShaderEffect 的基础能力。
     */
    FINAL_FRAME_POST
}
