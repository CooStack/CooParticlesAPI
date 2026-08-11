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
        /**
         * 复制或合并 `MutableRenderState` 的 `copyState` 数据，并返回可继续使用的结果。
         *
         * 示例：`copyState()`。
         *
         * @return 根据当前输入生成的新对象或数据结果
         */
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

        /**
         * 执行 `MutableRenderState` 定义的 `restoreFrom` 操作；输入和返回值用于该组件当前的渲染职责。
         *
         * 示例：`restoreFrom(snapshot = snapshot)`。
         *
         * @param snapshot 当前操作需要的输入值；其语义由方法名和所属组件共同限定
         */
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

    /**
     * 执行 `RenderStateGuard` 定义的 `use` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`use(block = block)`。
     *
     * @param block 在当前生命周期或数据上下文中执行的回调
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun <T> use(block: (MutableRenderState) -> T): T {
        val snapshot = state.copyState()
        return try {
            block(state)
        } finally {
            state.restoreFrom(snapshot)
        }
    }
}
