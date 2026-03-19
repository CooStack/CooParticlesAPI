package cn.coostack.cooparticlesapi.renderer.shader.glsl

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderPipelineManager
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlFrameBuffer
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.PipeChannels
import cn.coostack.cooparticlesapi.renderer.shader.pipe.manager.FramePipeChannels
import net.minecraft.client.Minecraft
import org.lwjgl.opengl.GL33.*
import java.nio.ByteBuffer
import java.util.function.Supplier

open class SimpleFrameBuffer(
    val colorChannelCount: Int,
    override var depthSupplier: Supplier<Int>
) : GlFrameBuffer {
    private var warnedZeroRead = false
    override val colorAttachments: IntArray = IntArray(colorChannelCount)

    override fun getOutputChannelCount(): Int {
        return colorChannelCount
    }

    private var useMipmap = false
    private var depthAttachment = -1
    private var fbo = 0
    private var prevFBO = 0
    private var initialized = false
    private var newDepth = false
    private var textureFilterMod = GL_LINEAR
    private lateinit var output: PipeChannels


    override fun fbo(): Int {
        return fbo
    }

    override fun width(): Int {
        return ClientRenderPipelineManager.currentRenderWidth()
    }

    override fun height(): Int {
        return ClientRenderPipelineManager.currentRenderHeight()
    }

    override fun useMipmap() {
        useMipmap = true
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
        if (!::output.isInitialized) {
            output = FramePipeChannels().apply {
                repeat(colorChannelCount) { addChannel { colorAttachments[it] } }
            }
        }
    }

    override fun setTextureFilterMod(mod: Int) {
        textureFilterMod = mod
    }

    override fun outputChannels(): PipeChannels {
        return output
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
        val previousViewport = IntArray(4)
        glGetIntegerv(GL_VIEWPORT, previousViewport)
        bindFramebuffer()
        glViewport(0, 0, width(), height())
        if (newDepth) {
            clear()
        } else {
            clear(GL_COLOR_BUFFER_BIT)
        }
        writeScope()

        // 生成 mipmap
        if (useMipmap) {
            val current = glGetInteger(GL_TEXTURE_BINDING_2D)
            colorAttachments.forEach {
                glBindTexture(GL_TEXTURE_2D, it)
                glGenerateMipmap(GL_TEXTURE_2D)
            }
            glBindTexture(GL_TEXTURE_2D, current)
        }
        glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3])
        reset()
    }

    override fun readFrameBufferWith(readScope: GlFrameBuffer.() -> Unit) {
        if (fbo == 0) {
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
        warnedZeroRead = false
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
                    GL_TEXTURE_2D, 0, GL_RGBA16F,
                    width(), height(), 0, GL_RGBA, GL_FLOAT, null as ByteBuffer?
                )
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, textureFilterMod)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, normalizeMagFilter(textureFilterMod))
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

    private fun normalizeMagFilter(filter: Int): Int {
        return when (filter) {
            GL_NEAREST,
            GL_NEAREST_MIPMAP_NEAREST,
            GL_NEAREST_MIPMAP_LINEAR -> GL_NEAREST
            else -> GL_LINEAR
        }
    }

    override fun resize(width: Int, height: Int) {
        if (!initialized) {
            return
        }
        release()
        initialized = false
        prevFBO = 0
        init()
    }

    override fun copyDepthBuffer(srcFBO: Int) {
        val lastReadReader = glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
        val lastDrawReader = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        val width = Minecraft.getInstance().window.width
        val height = Minecraft.getInstance().window.height

        // 绑定读和写的FBO
        glBindFramebuffer(GL_READ_FRAMEBUFFER, srcFBO)
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fbo)
        glBlitFramebuffer(
            0, 0, width, height,  // 源区域
            0, 0, width, height,  // 目标区域
            GL_DEPTH_BUFFER_BIT,
            GL_NEAREST
        )
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
//        // 解绑，恢复默认
//        glBindFramebuffer(GL_READ_FRAMEBUFFER, lastReadReader)
//        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, lastDrawReader)
    }

}
