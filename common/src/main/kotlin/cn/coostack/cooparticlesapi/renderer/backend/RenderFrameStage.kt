package cn.coostack.cooparticlesapi.renderer.backend

/**
 * RenderEntity V2 使用的统一帧阶段枚举。
 *
 * backend 可以有不同实现，但所有 renderer/effect 都应该只依赖这套抽象阶段。
 */
enum class RenderFrameStage {
    /**
     * 帧刚开始时的准备阶段。
     *
     * 适合缓存矩阵、目标信息和本帧通用状态。
     */
    FRAME_BEGIN,

    /**
     * 世界几何绘制阶段。
     *
     * 这里对应 `RenderEntityRenderer.render(...)` 的直接世界绘制路径。
     */
    WORLD_PASS,

    /**
     * frame-post 正式执行前的准备阶段。
     *
     * 常用于解析 scene color/depth、准备 post target、做必要的 copy 或预合成。
     */
    POST_PROCESS_PREPARE,

    /** Pipeline 后处理节点的帧尾执行阶段。 */
    FRAME_POST,

    /**
     * 帧收尾阶段。
     *
     * 常用于最终 flush、状态恢复和一帧结束后的清理。
     */
    FRAME_END
}
