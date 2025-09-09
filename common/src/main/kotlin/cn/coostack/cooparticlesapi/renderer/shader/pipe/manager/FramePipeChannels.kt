package cn.coostack.cooparticlesapi.renderer.shader.pipe.manager

import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.PipeChannels
import cn.coostack.cooparticlesapi.renderer.shader.texture.SimpleTextures
import org.lwjgl.opengl.GL33.*
import java.util.function.Supplier

class FramePipeChannels : PipeChannels {
    private var channels = mutableListOf<Supplier<Int>>()
    private var lastTextureID = 0
    private var lastActiveChannel = 0
    private val prevTextures = Array(32) {
        SimpleTextures.VariablePair(0, false)
    }

    override fun addChannel(id: Supplier<Int>): PipeChannels {
        if (channels.size + 1 > 32) {
            throw IllegalArgumentException("Only 32 channels supported")
        }
        channels.add(id)
        return this
    }

    override fun getChannels(): List<Supplier<Int>> {
        return channels.toList()
    }

    override fun getChannel(index: Int): Supplier<Int> {
        return channels[index]
    }

    override fun currentInputCount(): Int {
        return channels.size
    }

    override fun useOnContext(vertexDraw: Runnable): PipeChannels {
        use()
        vertexDraw.run()
        reset()
        return this
    }


    fun use() {
        lastActiveChannel = glGetInteger(GL_ACTIVE_TEXTURE)
        lastTextureID = glGetInteger(GL_TEXTURE_BINDING_2D)
        channels.forEachIndexed { channelIndex, texture ->
            val zero = GL_TEXTURE0
            val channel = zero + channelIndex
            glActiveTexture(channel)
            val prev = glGetInteger(GL_TEXTURE_BINDING_2D)
            prevTextures[channelIndex].apply {
                first = prev
                second = true
            }
            glBindTexture(GL_TEXTURE_2D, texture.get())
        }
    }

    fun reset() {
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

}