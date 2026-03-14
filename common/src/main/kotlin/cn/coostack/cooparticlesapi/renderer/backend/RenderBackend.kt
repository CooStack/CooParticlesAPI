package cn.coostack.cooparticlesapi.renderer.backend

interface RenderBackendHooks {
    fun cacheFrameState(context: RenderFrameContext)
    fun renderWorldPass(context: RenderFrameContext)
    fun preparePostProcess(context: RenderFrameContext)
    fun flushFrameComposites(context: RenderFrameContext)
    fun runFramePost(context: RenderFrameContext)
}

interface RenderBackend {
    val capabilities: Set<RenderBackendCapability>

    fun supports(capability: RenderBackendCapability): Boolean {
        return capability in capabilities
    }

    fun beginFrame(context: RenderFrameContext, hooks: RenderBackendHooks)

    fun finishLevelRender(context: RenderFrameContext, hooks: RenderBackendHooks)

    fun endFrame(context: RenderFrameContext, hooks: RenderBackendHooks) {
        hooks.flushFrameComposites(context)
        hooks.runFramePost(context)
    }
}
