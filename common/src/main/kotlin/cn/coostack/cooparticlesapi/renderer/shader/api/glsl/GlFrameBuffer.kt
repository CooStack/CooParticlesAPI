package cn.coostack.cooparticlesapi.renderer.shader.api.glsl

import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.PipeChannels
import java.util.function.Supplier

interface GlFrameBuffer {

    val colorAttachments: IntArray

    /**
     * 由于 NF会他妈的修改这个傻逼的depthAttachment导致我需要做一个提供器
     * Fuck NeoForge
     * 如果你想让他自己生成 attachment 并且初始化
     * 那么请提供 -1
     */
    var depthSupplier: Supplier<Int>

    fun getCurrentDepthAttachment(): Int

    fun width(): Int

    fun height(): Int

    fun fbo(): Int

    fun init()

    fun bindFramebuffer()

    fun clear(bit: Int)

    fun clear()

    fun setTextureFilterMod(mod: Int)

    /**
     * 将颜色通道转换为 pipe channel
     */
    fun outputChannels(): PipeChannels

    /**
     * 在这个方法内进行渲染操作
     * 此时会使用frameBuffer
     */
    fun writeFrameBufferWith(writeScope: GlFrameBuffer.() -> Unit)

    /**
     * 读取frame中的内容
     * 在该作用域渲染物体
     * (和绑定材质一样)
     * @param readScope 在此方法已经绑定了一些材质, 不要在方法内绑定其他材质
     */
    fun readFrameBufferWith(readScope: GlFrameBuffer.() -> Unit)

    fun reset()

    fun release()

    fun copyDepthBuffer(srcFBO: Int)

    fun resize(width: Int, height: Int)
}