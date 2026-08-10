package cn.coostack.cooparticlesapi.renderer.backend

/**
 * 渲染 backend 在各阶段需要回调的运行时钩子集合。
 *
 * backend 本身只决定“什么时候跑哪个阶段”，
 * 真正阶段里的业务工作由这些 hook 完成。
 */
interface RenderBackendHooks {
    /**
     * 缓存这一帧的上下文状态，通常用于准备后续阶段需要复用的 frame 数据。
     */
    fun cacheFrameState(context: RenderFrameContext)

    /**
     * 执行 world pass。
     *
     * 这里通常会驱动 `RenderEntityRenderer.render(...)` 的直接世界绘制。
     */
    fun renderWorldPass(context: RenderFrameContext)

    /**
     * 在 frame-post 之前准备屏幕后处理资源。
     *
     * 常见内容包括 scene color/depth 解析、离屏目标准备、copy/composite 前置步骤。
     */
    fun preparePostProcess(context: RenderFrameContext)

    /**
     * 刷新或提交这一帧累积的合成结果。
     */
    fun flushFrameComposites(context: RenderFrameContext)

    /** 运行 Pipeline 的帧尾后处理阶段。 */
    fun runFramePost(context: RenderFrameContext)
}

/**
 * RenderEntity V2 使用的渲染 backend 抽象。
 *
 * 它负责把统一的 `RenderFrameStage` 映射到不同平台或不同渲染环境的真实执行顺序。
 */
interface RenderBackend {
    /**
     * 当前 backend 支持的能力集合。
     *
     * Pipeline compiler 会用它检查 scene copy、depth read 等资源需求。
     */
    val capabilities: Set<RenderBackendCapability>

    /**
     * 判断当前 backend 是否支持某项能力。
     */
    fun supports(capability: RenderBackendCapability): Boolean {
        return capability in capabilities
    }

    /**
     * 执行某一个统一渲染阶段。
     */
    fun runStage(stage: RenderFrameStage, context: RenderFrameContext, hooks: RenderBackendHooks)

    /**
     * 帧开始入口。
     */
    fun beginFrame(context: RenderFrameContext, hooks: RenderBackendHooks) {
        runStage(RenderFrameStage.FRAME_BEGIN, context, hooks)
    }

    /**
     * 关卡世界渲染完成后的阶段桥接入口。
     *
     * 默认顺序是：
     * 1. `WORLD_PASS`
     * 2. `POST_PROCESS_PREPARE`
     */
    fun finishLevelRender(context: RenderFrameContext, hooks: RenderBackendHooks) {
        runStage(RenderFrameStage.WORLD_PASS, context, hooks)
        runStage(RenderFrameStage.POST_PROCESS_PREPARE, context, hooks)
    }

    /**
     * 帧结束入口。
     *
     * 默认顺序是：
     * 1. `FRAME_POST`
     * 2. `FRAME_END`
     */
    fun endFrame(context: RenderFrameContext, hooks: RenderBackendHooks) {
        runStage(RenderFrameStage.FRAME_POST, context, hooks)
        runStage(RenderFrameStage.FRAME_END, context, hooks)
    }
}
