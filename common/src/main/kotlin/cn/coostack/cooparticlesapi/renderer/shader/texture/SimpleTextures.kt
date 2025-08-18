package cn.coostack.cooparticlesapi.renderer.shader.texture

import cn.coostack.cooparticlesapi.renderer.shader.api.texture.GlTexture
import cn.coostack.cooparticlesapi.renderer.shader.api.texture.GlTextures
import org.lwjgl.opengl.GL33.*

class SimpleTextures : GlTextures {
    data class VariablePair<K, V>(var first: K, var second: V)

    private val textureWithChannel = mutableListOf<GlTexture>()

    private var lastTextureID = 0
    private var lastActiveChannel = 0
    private val prevTextures = Array<VariablePair<Int, Boolean>>(32) {
        VariablePair(0, false)
    }

    override fun addTexture(texture: GlTexture) {
        require(textureWithChannel.size < 32) { "没有那么多材质通道!" }
        textureWithChannel.add(texture)
    }

    override fun init() {
        textureWithChannel.forEach {
            it.init()
        }
    }

    override fun use() {
        lastActiveChannel = glGetInteger(GL_ACTIVE_TEXTURE)
        lastTextureID = glGetInteger(GL_TEXTURE_BINDING_2D)
        textureWithChannel.forEachIndexed { channelIndex, texture ->
            val zero = GL_TEXTURE0
            val channel = zero + channelIndex
            glActiveTexture(channel)
            val prev = glGetInteger(GL_TEXTURE_BINDING_2D)
            prevTextures[channelIndex].apply {
                first = prev
                second = true
            }
            glBindTexture(GL_TEXTURE_2D, texture.textureID())
        }
    }

    override fun reset() {
        prevTextures.forEachIndexed { channelIndex, prevTexture ->
            if (!prevTexture.second) {
                return@forEachIndexed
            }
            val zero = GL_TEXTURE0
            glActiveTexture(zero + channelIndex)
            glBindTexture(GL_TEXTURE_2D, prevTexture.first)
            prevTexture.second = false
        }
        glActiveTexture(lastActiveChannel)
        glBindTexture(GL_TEXTURE_2D, lastTextureID)
    }

    override fun drawWith(renderContext: Runnable) {
        use()
        renderContext.run()
        reset()
    }
}