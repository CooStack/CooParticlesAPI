package cn.coostack.cooparticlesapi.renderer.shader.glsl

import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlFrameBuffer
import com.mojang.blaze3d.pipeline.RenderTarget
import net.minecraft.client.renderer.RenderType
import org.lwjgl.opengl.GL33.*
import java.nio.ByteBuffer
import java.util.function.Supplier

open class MinecraftHookFrameBuffer(
    var mcFrame: RenderTarget,
) : GlFrameBuffer {
    override val colorAttachments: IntArray = IntArray(1)
    override var depthSupplier: Supplier<Int> = Supplier {
        mcFrame.depthTextureId
    }

    override fun getCurrentDepthAttachment(): Int {
        return depthSupplier.get()
    }

    override fun width(): Int {
        return mcFrame.width
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

    override fun clear(bit: Int) {
        glClearColor(0f, 0f, 0f, 0f)
        glClear(bit)
    }

    override fun bindFramebuffer() {
        prevFBO = glGetInteger(GL_FRAMEBUFFER_BINDING)
        glBindFramebuffer(GL_FRAMEBUFFER, fbo())
    }

    override fun writeFrameBufferWith(writeScope: GlFrameBuffer.() -> Unit) {
        mcFrame.bindWrite(true)
        writeScope()
        mcFrame.unbindWrite()
    }

    override fun readFrameBufferWith(readScope: GlFrameBuffer.() -> Unit) {
        mcFrame.bindRead()
        readScope()
        // 恢复
        mcFrame.unbindRead()
    }

    override fun reset() {
        glBindFramebuffer(GL_FRAMEBUFFER, prevFBO)
    }

    override fun release() {
        if (!initialized) {
            return
        }
    }

    private fun bindTextureTo(textureID: Int, fc: Runnable) {
        val prev = glGetInteger(GL_TEXTURE_BINDING_2D)
        glBindTexture(GL_TEXTURE_2D, textureID)
        fc.run()
        glBindTexture(GL_TEXTURE_2D, prev)
    }

    override fun resize(width: Int, height: Int) {
    }

    override fun copyDepthBuffer(srcFBO: Int) {
    }

}