package cn.coostack.cooparticlesapi.renderer.backend

object VanillaSafeRenderBackend : RenderBackend {
    override val capabilities: Set<RenderBackendCapability> = setOf(
        RenderBackendCapability.SCENE_COLOR_COPY,
        RenderBackendCapability.SCENE_DEPTH_READ,
        RenderBackendCapability.SAFE_WORLD_COMPOSITE,
        RenderBackendCapability.FINAL_FRAME_POST,
        RenderBackendCapability.EARLY_WORLD_HOOK
    )

    override fun beginFrame(context: RenderFrameContext, hooks: RenderBackendHooks) {
        hooks.cacheFrameState(context)
        hooks.renderWorldPass(context)
    }

    override fun finishLevelRender(context: RenderFrameContext, hooks: RenderBackendHooks) {
        hooks.preparePostProcess(context)
    }
}
