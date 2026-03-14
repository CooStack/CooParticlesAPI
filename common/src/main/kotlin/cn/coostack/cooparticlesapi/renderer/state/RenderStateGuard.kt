package cn.coostack.cooparticlesapi.renderer.state

class RenderStateGuard(
    private val state: MutableRenderState = MutableRenderState()
) {
    data class MutableRenderState(
        var activeTextureSlot: Int = 0,
        val textureBindings: LinkedHashMap<Int, Int> = linkedMapOf(),
        var blendEnabled: Boolean = false,
        var blendFuncSrc: Int = 1,
        var blendFuncDst: Int = 0,
        var depthTestEnabled: Boolean = true,
        var depthMask: Boolean = true,
        var cullEnabled: Boolean = true
    ) {
        fun copyState(): MutableRenderState {
            return MutableRenderState(
                activeTextureSlot = activeTextureSlot,
                textureBindings = LinkedHashMap(textureBindings),
                blendEnabled = blendEnabled,
                blendFuncSrc = blendFuncSrc,
                blendFuncDst = blendFuncDst,
                depthTestEnabled = depthTestEnabled,
                depthMask = depthMask,
                cullEnabled = cullEnabled
            )
        }

        fun restoreFrom(snapshot: MutableRenderState) {
            activeTextureSlot = snapshot.activeTextureSlot
            textureBindings.clear()
            textureBindings.putAll(snapshot.textureBindings)
            blendEnabled = snapshot.blendEnabled
            blendFuncSrc = snapshot.blendFuncSrc
            blendFuncDst = snapshot.blendFuncDst
            depthTestEnabled = snapshot.depthTestEnabled
            depthMask = snapshot.depthMask
            cullEnabled = snapshot.cullEnabled
        }
    }

    fun <T> use(block: (MutableRenderState) -> T): T {
        val snapshot = state.copyState()
        return try {
            block(state)
        } finally {
            state.restoreFrom(snapshot)
        }
    }
}
