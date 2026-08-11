package cn.coostack.cooparticlesapi.renderer.shader.glsl

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlFrameBuffer
import com.mojang.blaze3d.pipeline.RenderTarget
import org.lwjgl.opengl.GL33.*
import java.util.function.Supplier

open class MinecraftHookFrameBuffer(
    var mcFrame: RenderTarget,
) : GlFrameBuffer {
    private var warnedZeroRead = false
    override val colorAttachments: IntArray = IntArray(1)
    override var depthSupplier: Supplier<Int> = Supplier {
        mcFrame.depthTextureId
    }

    /**
     * 从 `当前组件` 当前维护的状态中读取 `getOutputChannelCount` 结果，不创建新的渲染资源。
     *
     * 示例：`getOutputChannelCount()`。
     *
     * @return 匹配当前条件的对象或状态；可空返回值表示没有可用结果
     */
    override fun getOutputChannelCount(): Int {
        return 1
    }

    /**
     * 从 `当前组件` 当前维护的状态中读取 `getCurrentDepthAttachment` 结果，不创建新的渲染资源。
     *
     * 示例：`getCurrentDepthAttachment()`。
     *
     * @return 匹配当前条件的对象或状态；可空返回值表示没有可用结果
     */
    override fun getCurrentDepthAttachment(): Int {
        return depthSupplier.get()
    }

    /**
     * 执行 `当前组件` 定义的 `width` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`width()`。
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    override fun width(): Int {
        return mcFrame.width
    }

    /**
     * 执行 `当前组件` 定义的 `useMipmap` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`useMipmap()`。
     */
    override fun useMipmap() {

    }

    /**
     * 执行 `当前组件` 定义的 `height` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`height()`。
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    override fun height(): Int {
        return mcFrame.height
    }

    private var prevFBO = 0
    private var initialized = false
    /**
     * 执行 `当前组件` 定义的 `fbo` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`fbo()`。
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    override fun fbo(): Int {
        return mcFrame.frameBufferId
    }

    /**
     * 初始化或准备 `当前组件` 的 `init` 阶段，使后续渲染调用可以使用相关资源。
     *
     * 示例：`init()`。
     */
    override fun init() {
        if (initialized) {
            return
        }
        initialized = true
        colorAttachments[0] = mcFrame.colorTextureId
    }

    /**
     * 清理 `当前组件` 的 `clear` 状态，使缓存、绑定或 OpenGL 状态可以重新初始化。
     *
     * 示例：`clear()`。
     */
    override fun clear() {
        clear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
    }

    /**
     * 更新 `当前组件` 的 `setTextureFilterMod` 状态；修改会影响后续查询、构建或当前帧绘制。
     *
     * 示例：`setTextureFilterMod(mod = mod)`。
     *
     * @param mod 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    override fun setTextureFilterMod(mod: Int) {
    }

    /**
     * 清理 `当前组件` 的 `clear` 状态，使缓存、绑定或 OpenGL 状态可以重新初始化。
     *
     * 示例：`clear(bit = bit)`。
     *
     * @param bit 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    override fun clear(bit: Int) {
        glClearColor(0f, 0f, 0f, 0f)
        glClear(bit)
    }


    /**
     * 把输入对象加入 `当前组件` 的 `bindFramebuffer` 管理范围，后续查询、构建或绘制会使用该绑定。
     *
     * 示例：`bindFramebuffer()`。
     */
    override fun bindFramebuffer() {
        prevFBO = glGetInteger(GL_FRAMEBUFFER_BINDING)
        glBindFramebuffer(GL_FRAMEBUFFER, fbo())
    }

    /**
     * 按 `当前组件` 约定的字段顺序写入 `writeFrameBufferWith` 数据；读取端必须使用相同协议。
     *
     * 示例：`writeFrameBufferWith(writeScope = writeScope)`。
     *
     * @param writeScope 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    override fun writeFrameBufferWith(writeScope: GlFrameBuffer.() -> Unit) {
//        mcFrame.bindWrite(true)
        bindFramebuffer()
        writeScope()
        reset()
//        mcFrame.unbindWrite()
    }

    /**
     * 从指定来源读取并解析 `readFrameBufferWith` 数据；输入必须符合 `当前组件` 使用的资源或网络格式。
     *
     * 示例：`readFrameBufferWith(readScope = readScope)`。
     *
     * @param readScope 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    override fun readFrameBufferWith(readScope: GlFrameBuffer.() -> Unit) {
        if (fbo() == 0) {
            warnedZeroRead = true
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

    /**
     * 清理 `当前组件` 的 `reset` 状态，使缓存、绑定或 OpenGL 状态可以重新初始化。
     *
     * 示例：`reset()`。
     */
    override fun reset() {
        glBindFramebuffer(GL_FRAMEBUFFER, prevFBO)
    }

    /**
     * 释放 `当前组件` 在 `release` 中管理的资源；再次使用前必须重新初始化。
     *
     * 示例：`release()`。
     */
    override fun release() {
        if (!initialized) {
            return
        }
        warnedZeroRead = false
    }

    /**
     * 更新 `当前组件` 的 `resize` 状态；修改会影响后续查询、构建或当前帧绘制。
     *
     * 示例：`resize(width = width, height = height)`。
     *
     * @param width 目标尺寸的像素数，必须与相关纹理或 framebuffer 尺寸一致
     *
     * @param height 目标尺寸的像素数，必须与相关纹理或 framebuffer 尺寸一致
     */
    override fun resize(width: Int, height: Int) {
    }

    /**
     * 复制或合并 `当前组件` 的 `copyDepthBuffer` 数据，并返回可继续使用的结果。
     *
     * 示例：`copyDepthBuffer(srcFBO = srcFBO)`。
     *
     * @param srcFBO 用于定位目标资源、实体或运行时实例的唯一标识
     */
    override fun copyDepthBuffer(srcFBO: Int) {
    }

}
