package cn.coostack.cooparticlesapi.renderer.backend

object IrisSafeRenderBackend : RenderBackend {
    // Under Iris we let the shader pack render first and then paint our pass on top. We still
    // advertise SCENE_DEPTH_READ because ClientRenderTargetResolver probes the bound framebuffer
    // for an external depth attachment via glGetFramebufferAttachmentParameteri and exposes it
    // as RenderFrameContext.sceneDepthFramebufferId — that path does not go through our own
    // scene-copy. Effects that cannot recover from a missing depth should still declare optional.
    override val capabilities: Set<RenderBackendCapability> = setOf(
        RenderBackendCapability.SCENE_COLOR_COPY,
        RenderBackendCapability.SCENE_DEPTH_READ,
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
