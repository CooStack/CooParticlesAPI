package cn.coostack.cooparticlesapi.renderer.backend

object VanillaSafeRenderBackend : RenderBackend {
    override val capabilities: Set<RenderBackendCapability> = setOf(
        RenderBackendCapability.SCENE_COLOR_COPY,
        RenderBackendCapability.SCENE_DEPTH_READ,
        RenderBackendCapability.TERRAIN_DEPTH_READ,
        RenderBackendCapability.SAFE_WORLD_COMPOSITE,
        RenderBackendCapability.FINAL_FRAME_POST
    )

    /**
     * 执行 `VanillaSafeRenderBackend` 定义的 `runStage` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`runStage(stage = stage, context = context, hooks = hooks)`。
     *
     * @param stage 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param context 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param hooks 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    override fun runStage(stage: RenderFrameStage, context: RenderFrameContext, hooks: RenderBackendHooks) {
        when (stage) {
            RenderFrameStage.FRAME_BEGIN -> hooks.cacheFrameState(context)
            RenderFrameStage.WORLD_PASS -> hooks.renderWorldPass(context)
            RenderFrameStage.SCENE_CAPTURE -> Unit
            RenderFrameStage.SCENE_POST -> hooks.runScenePost(context)
            RenderFrameStage.POST_PROCESS_PREPARE -> hooks.preparePostProcess(context)
            RenderFrameStage.FRAME_POST -> hooks.runFramePost(context)
            RenderFrameStage.FRAME_END -> hooks.flushFrameComposites(context)
        }
    }
}
