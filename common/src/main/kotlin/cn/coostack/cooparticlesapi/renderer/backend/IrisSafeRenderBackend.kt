package cn.coostack.cooparticlesapi.renderer.backend

object IrisSafeRenderBackend : RenderBackend {
    override val capabilities: Set<RenderBackendCapability> = setOf(
        RenderBackendCapability.SAFE_WORLD_COMPOSITE,
        RenderBackendCapability.FINAL_FRAME_POST
    )

    override fun runStage(stage: RenderFrameStage, context: RenderFrameContext, hooks: RenderBackendHooks) {
        when (stage) {
            RenderFrameStage.FRAME_BEGIN -> hooks.cacheFrameState(context)
            RenderFrameStage.WORLD_PASS -> hooks.renderWorldPass(context)
            RenderFrameStage.POST_PROCESS_PREPARE -> hooks.preparePostProcess(context)
            RenderFrameStage.FRAME_POST -> hooks.runFramePost(context)
            RenderFrameStage.FRAME_END -> hooks.flushFrameComposites(context)
        }
    }
}
