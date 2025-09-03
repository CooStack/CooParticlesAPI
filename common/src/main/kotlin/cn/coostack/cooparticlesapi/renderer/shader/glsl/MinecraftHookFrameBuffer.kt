package cn.coostack.cooparticlesapi.renderer.shader.glsl

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlFrameBuffer
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.PipeChannels
import cn.coostack.cooparticlesapi.renderer.shader.pipe.manager.FramePipeChannels
import com.mojang.blaze3d.pipeline.RenderTarget
import org.lwjgl.opengl.GL33.*
import java.util.function.Supplier

open class MinecraftHookFrameBuffer(
    var mcFrame: RenderTarget,
) : GlFrameBuffer {
    override val colorAttachments: IntArray = IntArray(1)
    override var depthSupplier: Supplier<Int> = Supplier {
        mcFrame.depthTextureId
    }

    override fun getOutputChannelCount(): Int {
        return 1
    }

    override fun getCurrentDepthAttachment(): Int {
        return depthSupplier.get()
    }

    override fun width(): Int {
        return mcFrame.width
    }

    override fun useMipmap() {

    }

    override fun height(): Int {
        return mcFrame.height
    }

    private var prevFBO = 0
    private var initialized = false
    override fun fbo(): Int {
        return mcFrame.frameBufferId
    }

    override fun init() {
        if (initialized) {
            return
        }
        initialized = true
        colorAttachments[0] = mcFrame.colorTextureId
    }

    override fun clear() {
        clear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
    }

    override fun setTextureFilterMod(mod: Int) {
    }

    override fun outputChannels(): PipeChannels {
        return FramePipeChannels()
            .addChannel { mcFrame.colorTextureId }
    }

    override fun clear(bit: Int) {
        glClearColor(0f, 0f, 0f, 0f)
        glClear(bit)
    }


    override fun bindFramebuffer() {
        prevFBO = glGetInteger(GL_FRAMEBUFFER_BINDING)
        glBindFramebuffer(GL_FRAMEBUFFER, fbo())
    }

    override fun writeFrameBufferWith(writeScope: GlFrameBuffer.() -> Unit) {
//        mcFrame.bindWrite(true)
        bindFramebuffer()
        writeScope()
        reset()
//        mcFrame.unbindWrite()
    }

    override fun readFrameBufferWith(readScope: GlFrameBuffer.() -> Unit) {
        if (fbo() == 0) {
            CooParticlesConstants.logger.error("trying to read frame buffer but fbo is zero")
            initialized = false
            return
        }
        val zero = GL_TEXTURE0
        val activeChannels = IntArray(1)
        val prevActive = glGetInteger(GL_ACTIVE_TEXTURE)
        val prevTexture = glGetInteger(GL_TEXTURE_BINDING_2D)
        glActiveTexture(zero)
        activeChannels[0] = glGetInteger(GL_TEXTURE_BINDING_2D)
        glBindTexture(GL_TEXTURE_2D, mcFrame.colorTextureId)

        readScope()
        activeChannels.forEachIndexed { channel, texture ->
            glActiveTexture(zero + channel)
            glBindTexture(GL_TEXTURE_2D, texture)
        }
        glActiveTexture(prevActive)
        glBindTexture(GL_TEXTURE_2D, prevTexture)
    }

    override fun reset() {
        glBindFramebuffer(GL_FRAMEBUFFER, prevFBO)
    }

    override fun release() {
        if (!initialized) {
            return
        }
    }

    override fun resize(width: Int, height: Int) {
    }

    override fun copyDepthBuffer(srcFBO: Int) {
    }

}