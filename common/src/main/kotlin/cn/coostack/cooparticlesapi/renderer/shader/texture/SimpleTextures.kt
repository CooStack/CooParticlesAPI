package cn.coostack.cooparticlesapi.renderer.shader.texture

import cn.coostack.cooparticlesapi.renderer.shader.api.texture.GlTexture
import cn.coostack.cooparticlesapi.renderer.shader.api.texture.GlTextures
import org.lwjgl.opengl.GL33.*

class SimpleTextures : GlTextures {
    private data class TextureState(
        val activeChannel: Int,
        val activeTexture: Int,
        val channelTextures: IntArray
    )

    private val textureWithChannel = mutableListOf<GlTexture>()
    private val states = ArrayDeque<TextureState>()

    /**
     * 把输入对象加入 `SimpleTextures` 的 `addTexture` 管理范围，后续查询、构建或绘制会使用该绑定。
     *
     * 示例：`addTexture(texture = texture)`。
     *
     * @param texture 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    override fun addTexture(texture: GlTexture) {
        require(textureWithChannel.size < 32) { "没有那么多材质通道!" }
        textureWithChannel.add(texture)
    }

    /**
     * 从 `SimpleTextures` 当前维护的状态中读取 `getTextureCounts` 结果，不创建新的渲染资源。
     *
     * 示例：`getTextureCounts()`。
     *
     * @return 匹配当前条件的对象或状态；可空返回值表示没有可用结果
     */
    override fun getTextureCounts(): Int {
        return textureWithChannel.size
    }

    /**
     * 初始化或准备 `SimpleTextures` 的 `init` 阶段，使后续渲染调用可以使用相关资源。
     *
     * 示例：`init()`。
     */
    override fun init() {
        textureWithChannel.forEach {
            it.init()
        }
    }

    /**
     * 释放 `SimpleTextures` 在 `release` 中管理的资源；再次使用前必须重新初始化。
     *
     * 示例：`release()`。
     */
    override fun release() {
        textureWithChannel.forEach {
            it.release()
        }
    }

    /**
     * 执行 `SimpleTextures` 定义的 `use` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`use()`。
     */
    override fun use() {
        val activeChannel = glGetInteger(GL_ACTIVE_TEXTURE)
        val activeTexture = glGetInteger(GL_TEXTURE_BINDING_2D)
        val channelTextures = IntArray(textureWithChannel.size)
        try {
            channelTextures.indices.forEach { channelIndex ->
                glActiveTexture(GL_TEXTURE0 + channelIndex)
                channelTextures[channelIndex] = glGetInteger(GL_TEXTURE_BINDING_2D)
            }
        } finally {
            glActiveTexture(activeChannel)
            glBindTexture(GL_TEXTURE_2D, activeTexture)
        }

        val state = TextureState(activeChannel, activeTexture, channelTextures)
        states.addLast(state)
        try {
            textureWithChannel.forEachIndexed { channelIndex, texture ->
                glActiveTexture(GL_TEXTURE0 + channelIndex)
                glBindTexture(GL_TEXTURE_2D, texture.textureID())
            }
        } catch (error: Throwable) {
            states.removeLast()
            try {
                restore(state)
            } catch (restoreError: Throwable) {
                error.addSuppressed(restoreError)
            }
            throw error
        }
    }

    /**
     * 清理 `SimpleTextures` 的 `reset` 状态，使缓存、绑定或 OpenGL 状态可以重新初始化。
     *
     * 示例：`reset()`。
     */
    override fun reset() {
        if (states.isEmpty()) return
        restore(states.removeLast())
    }

    /**
     * 执行 `SimpleTextures` 的 `drawWith` 渲染操作，处理传入数据并更新当前帧或 GPU 状态。
     *
     * 示例：`drawWith(renderContext = renderContext)`。
     *
     * @param renderContext 在当前生命周期或数据上下文中执行的回调
     */
    override fun drawWith(renderContext: Runnable) {
        var stateCreated = false
        try {
            use()
            stateCreated = true
            renderContext.run()
        } finally {
            if (stateCreated) reset()
        }
    }

    private fun restore(state: TextureState) {
        state.channelTextures.forEachIndexed { channelIndex, texture ->
            glActiveTexture(GL_TEXTURE0 + channelIndex)
            glBindTexture(GL_TEXTURE_2D, texture)
        }
        glActiveTexture(state.activeChannel)
        glBindTexture(GL_TEXTURE_2D, state.activeTexture)
    }
}
