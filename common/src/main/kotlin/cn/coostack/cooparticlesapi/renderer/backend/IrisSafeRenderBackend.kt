package cn.coostack.cooparticlesapi.renderer.backend

object IrisSafeRenderBackend : RenderBackend {
    override val capabilities: Set<RenderBackendCapability> = setOf(
        RenderBackendCapability.SAFE_WORLD_COMPOSITE,
        RenderBackendCapability.FINAL_FRAME_POST
    )

    override fun beginFrame(context: RenderFrameContext, hooks: RenderBackendHooks) {
        hooks.cacheFrameState(context)
    }

    override fun finishLevelRender(context: RenderFrameContext, hooks: RenderBackendHooks) {
        hooks.renderWorldPass(context)
        hooks.preparePostProcess(context)
    }
}
