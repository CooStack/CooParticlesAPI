package cn.coostack.cooparticlesapi.renderer.shader.glsl

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlFrameBuffer
import net.minecraft.client.Minecraft
import org.lwjgl.opengl.GL33.*
import java.nio.ByteBuffer
import java.util.function.Supplier

open class SimpleFrameBuffer(
    val colorChannelCount: Int,
    override var depthSupplier: Supplier<Int>
) : GlFrameBuffer {
    override val colorAttachments: IntArray = IntArray(colorChannelCount)
    private var depthAttachment = -1
    private var fbo = 0
    private var prevFBO = 0
    private var initialized = false
    private var newDepth = false
    private var textureFilterMod = GL_LINEAR
    override fun fbo(): Int {
        return fbo
    }

    override fun width(): Int {
        return Minecraft.getInstance().mainRenderTarget.width
    }

    override fun height(): Int {
        return Minecraft.getInstance().mainRenderTarget.height
    }

    override fun getCurrentDepthAttachment(): Int {
        return depthAttachment
    }

    override fun init() {
        if (initialized) {
            return
        }
        initialized = true
        fbo = glGenFramebuffers()
        bindFramebuffer()
        prevFBO = 0
        initColorChannel()
        initDepthChannel()
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            val mc = Minecraft.getInstance()
            val target = mc.mainRenderTarget
            // 逆天NF概率性崩端
            CooParticlesConstants.logger.error(
                """
                    Failed to bind framebuffer $fbo 
                    depth: $depthAttachment 
                    color:${colorAttachments.contentToString()} 
                    status undefined: ${glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_UNDEFINED}
                    status incomplete attachment: ${glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT}
                    status missing attachment: ${glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT}
                    height: ${height()} width: ${width()} 
                    mc_height:${target.height} mc_width: ${target.width}
                    vew mc_height:${target.viewHeight} mc_width: ${target.viewWidth}
                    window h: ${mc.window.screenHeight} w ${mc.window.screenWidth}
                """
            )
            depthAttachment = depthSupplier.get()
        }
        reset()
    }

    override fun setTextureFilterMod(mod: Int) {
        textureFilterMod = mod
    }

    override fun clear() {
        clear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
    }

    override fun clear(bit: Int) {
        glClear(bit)
    }

    override fun bindFramebuffer() {
        prevFBO = glGetInteger(GL_FRAMEBUFFER_BINDING)
        glBindFramebuffer(GL_FRAMEBUFFER, fbo)
    }

    override fun writeFrameBufferWith(writeScope: GlFrameBuffer.() -> Unit) {
        if (fbo == 0) {
            CooParticlesConstants.logger.error("trying to write frame buffer but fbo is zero")
            initialized = false
            init()
            return
        }
        bindFramebuffer()
        if (newDepth) {
            clear()
        } else {
            clear(GL_COLOR_BUFFER_BIT)
        }
        writeScope()
        reset()
    }

    override fun readFrameBufferWith(readScope: GlFrameBuffer.() -> Unit) {
        if (fbo == 0) {
            CooParticlesConstants.logger.error("trying to read frame buffer but fbo is zero")
            initialized = false
            return
        }
        val zero = GL_TEXTURE0
        val activeChannels = IntArray(colorChannelCount)
        val prevActive = glGetInteger(GL_ACTIVE_TEXTURE)
        val prevTexture = glGetInteger(GL_TEXTURE_BINDING_2D)
        repeat(colorChannelCount) {
            val channel = zero + it
            glActiveTexture(channel)
            activeChannels[it] = glGetInteger(GL_TEXTURE_BINDING_2D)
            glBindTexture(GL_TEXTURE_2D, colorAttachments[it])
        }
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
        glDeleteFramebuffers(fbo)
        fbo = 0
        colorAttachments.forEachIndexed { channel, texture ->
            glDeleteTextures(texture)
            colorAttachments[channel] = 0
        }
        if (newDepth) {
            glDeleteTextures(depthAttachment)
            depthAttachment = -1
        }
    }

    private fun initColorChannel() {
        val zero = GL_COLOR_ATTACHMENT0
        val channels = IntArray(colorChannelCount)
        repeat(colorChannelCount) {
            val channel = zero + it
            channels[it] = channel
            val texture = glGenTextures()
            colorAttachments[it] = texture
            bindTextureTo(texture) {
                glTexImage2D(
                    GL_TEXTURE_2D, 0, GL_RGBA,
                    width(), height(), 0, GL_RGBA, GL_UNSIGNED_BYTE, null as ByteBuffer?
                )
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, textureFilterMod)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, textureFilterMod)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
                glGenerateMipmap(GL_TEXTURE_2D)
                glFramebufferTexture2D(
                    GL_FRAMEBUFFER, channel,
                    GL_TEXTURE_2D, texture, 0
                )
            }
        }
        glDrawBuffers(channels)
    }

    private fun initDepthChannel() {
        val get = depthSupplier.get()
        val new = get == -1
        if (new) {
            depthAttachment = glGenTextures()
            newDepth = true
        } else {
            depthAttachment = get
        }
        bindTextureTo(depthAttachment) {
            if (new) {
                glTexImage2D(
                    GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT,
                    width(), height(), 0, GL_DEPTH_COMPONENT, GL_UNSIGNED_BYTE, null as ByteBuffer?
                )
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            }
            glFramebufferTexture2D(
                GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT,
                GL_TEXTURE_2D, depthAttachment, 0
            )
        }
    }

    private fun bindTextureTo(textureID: Int, fc: Runnable) {
        val prev = glGetInteger(GL_TEXTURE_BINDING_2D)
        glBindTexture(GL_TEXTURE_2D, textureID)
        fc.run()
        glBindTexture(GL_TEXTURE_2D, prev)
    }

    override fun resize(width: Int, height: Int) {
//        this.width = width
//        this.height = height
        if (!initialized) {
            return
        }
        readFrameBufferWith {
            clear()
        }
        release()
        initialized = false
        prevFBO = 0
        init()
    }

    override fun copyDepthBuffer(srcFBO: Int) {
        val lastReadReader = glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
        val lastDrawReader = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        glBindFramebuffer(GL_READ_FRAMEBUFFER, srcFBO)
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fbo)
        glBlitFramebuffer(
            0, 0, width(), height(),    // 源区域
            0, 0, width(), height(),    // 目标区域
            GL_DEPTH_BUFFER_BIT,    // 只复制深度缓冲
            GL_NEAREST              // 过滤方式，深度用 NEAREST 就好
        )
        // 解绑，恢复默认
        glBindFramebuffer(GL_READ_FRAMEBUFFER, lastReadReader)
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, lastDrawReader)
    }

}