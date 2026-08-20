package cn.coostack.cooparticlesapi.renderer.shader.glsl

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderPipelineManager
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlFrameBuffer
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.CooTextureFormat
import net.minecraft.client.Minecraft
import org.lwjgl.opengl.GL33.*
import java.nio.ByteBuffer
import java.util.function.Supplier

/** 描述 framebuffer 深度纹理的 OpenGL allocation 与 attachment 形式。 */
internal data class GlDepthTextureFormat(
    val internalFormat: Int,
    val pixelFormat: Int,
    val dataType: Int,
    val attachment: Int,
)

open class SimpleFrameBuffer(
    val colorChannelCount: Int,
    override var depthSupplier: Supplier<Int>,
    private val colorFormat: CooTextureFormat = CooTextureFormat.RGBA8,
    mipLevels: Int = 1
) : GlFrameBuffer {
    constructor(
        colorChannelCount: Int,
        depthSupplier: Supplier<Int>,
        fixedWidth: Int,
        fixedHeight: Int
    ) : this(colorChannelCount, depthSupplier) {
        this.fixedWidth = fixedWidth.coerceAtLeast(1)
        this.fixedHeight = fixedHeight.coerceAtLeast(1)
    }

    constructor(
        colorChannelCount: Int,
        depthSupplier: Supplier<Int>,
        colorFormat: CooTextureFormat,
        mipLevels: Int,
        fixedWidth: Int,
        fixedHeight: Int
    ) : this(colorChannelCount, depthSupplier, colorFormat, mipLevels) {
        this.fixedWidth = fixedWidth.coerceAtLeast(1)
        this.fixedHeight = fixedHeight.coerceAtLeast(1)
    }

    private var warnedZeroRead = false
    override val colorAttachments: IntArray = IntArray(colorChannelCount)

    /**
     * 从 `当前组件` 当前维护的状态中读取 `getOutputChannelCount` 结果，不创建新的渲染资源。
     *
     * 示例：`getOutputChannelCount()`。
     *
     * @return 匹配当前条件的对象或状态；可空返回值表示没有可用结果
     */
    override fun getOutputChannelCount(): Int {
        return colorChannelCount
    }

    private var useMipmap = mipLevels > 1
    private var requestedMipLevels = mipLevels.coerceAtLeast(1)
    private var depthAttachment = -1
    private var depthTextureFormat: GlDepthTextureFormat? = null
    private var fbo = 0
    private var previousReadFramebuffer = 0
    private var previousDrawFramebuffer = 0
    private var initialized = false
    private var newDepth = false
    private var textureFilterMod = GL_LINEAR
    private var fixedWidth: Int? = null
    private var fixedHeight: Int? = null


    /**
     * 执行 `当前组件` 定义的 `fbo` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`fbo()`。
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    override fun fbo(): Int {
        return fbo
    }

    /**
     * 执行 `当前组件` 定义的 `width` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`width()`。
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    override fun width(): Int {
        return fixedWidth ?: ClientRenderPipelineManager.currentRenderWidth()
    }

    /**
     * 执行 `当前组件` 定义的 `height` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`height()`。
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    override fun height(): Int {
        return fixedHeight ?: ClientRenderPipelineManager.currentRenderHeight()
    }

    /**
     * 执行 `当前组件` 定义的 `useMipmap` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`useMipmap()`。
     */
    override fun useMipmap() {
        useMipmap = true
        requestedMipLevels = Int.MAX_VALUE
    }

    /**
     * 从 `当前组件` 当前维护的状态中读取 `getCurrentDepthAttachment` 结果，不创建新的渲染资源。
     *
     * 示例：`getCurrentDepthAttachment()`。
     *
     * @return 匹配当前条件的对象或状态；可空返回值表示没有可用结果
     */
    override fun getCurrentDepthAttachment(): Int {
        return depthAttachment
    }

    /** 在初始化前指定自有深度纹理的精确 OpenGL 格式。 */
    internal fun setDepthTextureFormat(format: GlDepthTextureFormat?) {
        check(!initialized) { "Depth texture format must be configured before framebuffer init" }
        depthTextureFormat = format
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
        fbo = glGenFramebuffers()
        val previousRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
        val previousDraw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        try {
            glBindFramebuffer(GL_FRAMEBUFFER, fbo)
            initColorChannel()
            initDepthChannel()
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
                val mc = Minecraft.getInstance()
                val target = mc.mainRenderTarget
                // NeoForge 偶发创建出不完整 FBO；保留完整状态，供日志定位实际 attachment。
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
        } catch (error: Throwable) {
            release()
            throw error
        } finally {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, previousRead)
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previousDraw)
        }
    }

    /**
     * 更新 `当前组件` 的 `setTextureFilterMod` 状态；修改会影响后续查询、构建或当前帧绘制。
     *
     * 示例：`setTextureFilterMod(mod = mod)`。
     *
     * @param mod 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    override fun setTextureFilterMod(mod: Int) {
        textureFilterMod = mod
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
     * 清理 `当前组件` 的 `clear` 状态，使缓存、绑定或 OpenGL 状态可以重新初始化。
     *
     * 示例：`clear(bit = bit)`。
     *
     * @param bit 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    override fun clear(bit: Int) {
        glClear(bit)
    }

    /**
     * 把输入对象加入 `当前组件` 的 `bindFramebuffer` 管理范围，后续查询、构建或绘制会使用该绑定。
     *
     * 示例：`bindFramebuffer()`。
     */
    override fun bindFramebuffer() {
        previousReadFramebuffer = glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
        previousDrawFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        glBindFramebuffer(GL_FRAMEBUFFER, fbo)
    }

    /**
     * 按 `当前组件` 约定的字段顺序写入 `writeFrameBufferWith` 数据；读取端必须使用相同协议。
     *
     * 示例：`writeFrameBufferWith(writeScope = writeScope)`。
     *
     * @param writeScope 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    override fun writeFrameBufferWith(writeScope: GlFrameBuffer.() -> Unit) {
        writeFrameBufferWith(useMipmap, writeScope)
    }

    /** 在本次写入完成后按需刷新 mip 链。 */
    fun writeFrameBufferWith(
        generateMipmaps: Boolean,
        writeScope: GlFrameBuffer.() -> Unit
    ) {
        if (fbo == 0) {
            CooParticlesConstants.logger.error("trying to write frame buffer but fbo is zero")
            initialized = false
            init()
            return
        }
        val previousViewport = IntArray(4)
        val previousRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
        val previousDraw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        val previousClearColor = FloatArray(4)
        val previousColorMask = IntArray(4)
        glGetFloatv(GL_COLOR_CLEAR_VALUE, previousClearColor)
        glGetIntegerv(GL_COLOR_WRITEMASK, previousColorMask)
        val previousDepthMask = glGetBoolean(GL_DEPTH_WRITEMASK)
        val previousScissor = glIsEnabled(GL_SCISSOR_TEST)
        glGetIntegerv(GL_VIEWPORT, previousViewport)
        try {
            glBindFramebuffer(GL_FRAMEBUFFER, fbo)
            glViewport(0, 0, width(), height())
            glDisable(GL_SCISSOR_TEST)
            glColorMask(true, true, true, true)
            glDepthMask(true)
            glClearColor(0F, 0F, 0F, 0F)
            if (newDepth) {
                clear()
            } else {
                clear(GL_COLOR_BUFFER_BIT)
            }
            glDepthMask(previousDepthMask)
            writeScope()

            if (generateMipmaps && resolvedMipLevels() > 1) {
                generateMipmaps()
            }
        } finally {
            glClearColor(
                previousClearColor[0],
                previousClearColor[1],
                previousClearColor[2],
                previousClearColor[3]
            )
            glColorMask(
                previousColorMask[0] != 0,
                previousColorMask[1] != 0,
                previousColorMask[2] != 0,
                previousColorMask[3] != 0
            )
            glDepthMask(previousDepthMask)
            if (previousScissor) glEnable(GL_SCISSOR_TEST) else glDisable(GL_SCISSOR_TEST)
            glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3])
            glBindFramebuffer(GL_READ_FRAMEBUFFER, previousRead)
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previousDraw)
        }
    }

    /** 根据当前声明刷新全部颜色 attachment 的 mip 链。 */
    fun generateMipmaps() {
        if (resolvedMipLevels() <= 1) return
        val current = glGetInteger(GL_TEXTURE_BINDING_2D)
        try {
            colorAttachments.forEach {
                glBindTexture(GL_TEXTURE_2D, it)
                glGenerateMipmap(GL_TEXTURE_2D)
            }
        } finally {
            glBindTexture(GL_TEXTURE_2D, current)
        }
    }

    /**
     * 从指定来源读取并解析 `readFrameBufferWith` 数据；输入必须符合 `当前组件` 使用的资源或网络格式。
     *
     * 示例：`readFrameBufferWith(readScope = readScope)`。
     *
     * @param readScope 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    override fun readFrameBufferWith(readScope: GlFrameBuffer.() -> Unit) {
        if (fbo == 0) {
            return
        }
        val zero = GL_TEXTURE0
        val activeChannels = IntArray(colorChannelCount)
        val prevActive = glGetInteger(GL_ACTIVE_TEXTURE)
        val prevTexture = glGetInteger(GL_TEXTURE_BINDING_2D)
        val previousRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
        val previousDraw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        var capturedChannels = 0
        try {
            repeat(colorChannelCount) {
                val channel = zero + it
                glActiveTexture(channel)
                activeChannels[it] = glGetInteger(GL_TEXTURE_BINDING_2D)
                capturedChannels++
                glBindTexture(GL_TEXTURE_2D, colorAttachments[it])
            }
            readScope()
        } finally {
            repeat(capturedChannels) { channel ->
                glActiveTexture(zero + channel)
                glBindTexture(GL_TEXTURE_2D, activeChannels[channel])
            }
            glActiveTexture(prevActive)
            glBindTexture(GL_TEXTURE_2D, prevTexture)
            glBindFramebuffer(GL_READ_FRAMEBUFFER, previousRead)
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previousDraw)
        }
    }

    /**
     * 清理 `当前组件` 的 `reset` 状态，使缓存、绑定或 OpenGL 状态可以重新初始化。
     *
     * 示例：`reset()`。
     */
    override fun reset() {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, previousReadFramebuffer)
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer)
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
        initialized = false
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
                val internalFormat = when (colorFormat) {
                    CooTextureFormat.RGBA8 -> GL_RGBA8
                    CooTextureFormat.RGBA16F -> GL_RGBA16F
                    CooTextureFormat.RGBA32F -> GL_RGBA32F
                }
                val dataType = when (colorFormat) {
                    CooTextureFormat.RGBA8 -> GL_UNSIGNED_BYTE
                    CooTextureFormat.RGBA16F,
                    CooTextureFormat.RGBA32F -> GL_FLOAT
                }
                repeat(resolvedMipLevels()) { level ->
                    glTexImage2D(
                        GL_TEXTURE_2D,
                        level,
                        internalFormat,
                        maxOf(1, width() shr level),
                        maxOf(1, height() shr level),
                        0,
                        GL_RGBA,
                        dataType,
                        null as ByteBuffer?
                    )
                }
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, textureFilterMod)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, normalizeMagFilter(textureFilterMod))
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, resolvedMipLevels() - 1)
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
            val format = depthTextureFormat ?: GlDepthTextureFormat(
                GL_DEPTH_COMPONENT,
                GL_DEPTH_COMPONENT,
                GL_FLOAT,
                GL_DEPTH_ATTACHMENT
            )
            if (new) {
                glTexImage2D(
                    GL_TEXTURE_2D,
                    0,
                    format.internalFormat,
                    width(),
                    height(),
                    0,
                    format.pixelFormat,
                    format.dataType,
                    null as ByteBuffer?
                )
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            }
            glFramebufferTexture2D(
                GL_FRAMEBUFFER,
                if (new) format.attachment else GL_DEPTH_ATTACHMENT,
                GL_TEXTURE_2D,
                depthAttachment,
                0
            )
        }
    }

    private fun bindTextureTo(textureID: Int, fc: Runnable) {
        val prev = glGetInteger(GL_TEXTURE_BINDING_2D)
        try {
            glBindTexture(GL_TEXTURE_2D, textureID)
            fc.run()
        } finally {
            glBindTexture(GL_TEXTURE_2D, prev)
        }
    }

    private fun normalizeMagFilter(filter: Int): Int {
        return when (filter) {
            GL_NEAREST,
            GL_NEAREST_MIPMAP_NEAREST,
            GL_NEAREST_MIPMAP_LINEAR -> GL_NEAREST
            else -> GL_LINEAR
        }
    }

    /** 返回当前尺寸实际能够分配的 mip 层数。 */
    private fun resolvedMipLevels(): Int {
        var levels = 1
        var size = maxOf(width(), height())
        while (size > 1 && levels < requestedMipLevels) {
            size = maxOf(1, size / 2)
            levels++
        }
        return levels
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
        val requestedWidth = width.coerceAtLeast(1)
        val requestedHeight = height.coerceAtLeast(1)
        if (fixedWidth == requestedWidth && fixedHeight == requestedHeight && initialized) {
            return
        }
        fixedWidth = requestedWidth
        fixedHeight = requestedHeight
        if (!initialized) {
            return
        }
        val resizedFramebuffer = fbo
        val previousRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
        val previousDraw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        try {
            release()
            init()
        } finally {
            glBindFramebuffer(
                GL_READ_FRAMEBUFFER,
                if (previousRead == resizedFramebuffer) fbo else previousRead
            )
            glBindFramebuffer(
                GL_DRAW_FRAMEBUFFER,
                if (previousDraw == resizedFramebuffer) fbo else previousDraw
            )
        }
    }

    /**
     * 复制或合并 `当前组件` 的 `copyDepthBuffer` 数据，并返回可继续使用的结果。
     *
     * 示例：`copyDepthBuffer(srcFBO = srcFBO)`。
     *
     * @param srcFBO 用于定位目标资源、实体或运行时实例的唯一标识
     */
    override fun copyDepthBuffer(srcFBO: Int) {
        val lastReadReader = glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
        val lastDrawReader = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        val sourceWidth = ClientRenderPipelineManager.currentRenderWidth()
        val sourceHeight = ClientRenderPipelineManager.currentRenderHeight()
        val targetWidth = width()
        val targetHeight = height()

        // 绑定读和写的FBO
        try {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, srcFBO)
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fbo)
            glBlitFramebuffer(
                0, 0, sourceWidth, sourceHeight,  // 源区域
                0, 0, targetWidth, targetHeight,  // 目标区域
                GL_DEPTH_BUFFER_BIT,
                GL_NEAREST
            )
        } finally {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, lastReadReader)
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, lastDrawReader)
        }
    }

}
