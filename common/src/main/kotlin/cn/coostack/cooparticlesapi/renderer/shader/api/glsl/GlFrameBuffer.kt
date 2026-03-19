package cn.coostack.cooparticlesapi.renderer.shader.api.glsl

import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.PipeChannels
import java.util.function.Supplier

/**
 * OpenGL framebuffer 抽象。
 *
 * 用于统一封装颜色附件、深度附件、绑定、读写与尺寸调整。
 */
interface GlFrameBuffer {

    /**
     * 当前 framebuffer 持有的颜色附件纹理 id 列表。
     */
    val colorAttachments: IntArray

    /**
     * 深度附件提供器。
     *
     * 某些平台会在运行时替换或重建 depth attachment，
     * 因此这里不直接持有固定 id，而是通过提供器按需读取。
     *
     * 如果希望由实现自行创建 depth attachment，通常约定提供 `-1`。
     */
    var depthSupplier: Supplier<Int>

    /**
     * 启用 mipmap 纹理使用模式。
     */
    fun useMipmap()

    /**
     * 返回当前输出通道数量。
     */
    fun getOutputChannelCount(): Int

    /**
     * 返回当前深度附件 id。
     */
    fun getCurrentDepthAttachment(): Int

    /**
     * 返回当前 framebuffer 宽度。
     */
    fun width(): Int

    /**
     * 返回当前 framebuffer 高度。
     */
    fun height(): Int

    /**
     * 返回当前 framebuffer 对应的 FBO id。
     */
    fun fbo(): Int

    /**
     * 初始化 framebuffer 与附件资源。
     */
    fun init()

    /**
     * 绑定当前 framebuffer 作为写入目标。
     */
    fun bindFramebuffer()

    /**
     * 使用指定 clear bit 清理当前 framebuffer。
     */
    fun clear(bit: Int)

    /**
     * 使用默认清理策略清理当前 framebuffer。
     */
    fun clear()

    /**
     * 设置附件纹理过滤模式。
     */
    fun setTextureFilterMod(mod: Int)

    /**
     * 把颜色附件转换为 `PipeChannels`，供 shader pipe 继续消费。
     */
    fun outputChannels(): PipeChannels

    /**
     * 在绑定当前 framebuffer 的作用域内执行写入逻辑。
     */
    fun writeFrameBufferWith(writeScope: GlFrameBuffer.() -> Unit)

    /**
     * 在读取当前 framebuffer 内容的作用域内执行逻辑。
     *
     * 调用方通常不应在 `readScope` 中再切换无关纹理绑定。
     */
    fun readFrameBufferWith(readScope: GlFrameBuffer.() -> Unit)

    /**
     * 恢复 framebuffer 使用前的绑定状态。
     */
    fun reset()

    /**
     * 释放 framebuffer 与附件资源。
     */
    fun release()

    /**
     * 从另一个 FBO 复制深度缓冲。
     */
    fun copyDepthBuffer(srcFBO: Int)

    /**
     * 调整 framebuffer 尺寸。
     */
    fun resize(width: Int, height: Int)
}
