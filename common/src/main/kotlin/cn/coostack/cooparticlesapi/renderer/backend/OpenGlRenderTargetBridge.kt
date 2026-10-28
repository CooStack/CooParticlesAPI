package cn.coostack.cooparticlesapi.renderer.backend

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.textures.GpuTexture
import com.mojang.renderpearl.api.textures.GpuSampler
import com.mojang.renderpearl.backend.opengl.GlStateManager
import com.mojang.renderpearl.backend.opengl.GlTexture
import com.mojang.renderpearl.backend.opengl.GlSampler
import org.lwjgl.opengl.GL33.GL_COLOR_ATTACHMENT0
import org.lwjgl.opengl.GL33.GL_DEPTH_ATTACHMENT
import org.lwjgl.opengl.GL33.GL_DEPTH_STENCIL_ATTACHMENT
import org.lwjgl.opengl.GL33.GL_DRAW_FRAMEBUFFER
import org.lwjgl.opengl.GL33.GL_DRAW_FRAMEBUFFER_BINDING
import org.lwjgl.opengl.GL33.GL_FRAMEBUFFER
import org.lwjgl.opengl.GL33.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
import org.lwjgl.opengl.GL33.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
import org.lwjgl.opengl.GL33.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL
import org.lwjgl.opengl.GL33.GL_FRAMEBUFFER_COMPLETE
import org.lwjgl.opengl.GL33.GL_NONE
import org.lwjgl.opengl.GL33.GL_READ_FRAMEBUFFER
import org.lwjgl.opengl.GL33.GL_READ_FRAMEBUFFER_BINDING
import org.lwjgl.opengl.GL33.GL_TEXTURE
import org.lwjgl.opengl.GL33.GL_TEXTURE_2D
import org.lwjgl.opengl.GL33.glBindFramebuffer
import org.lwjgl.opengl.GL33.glBindTexture
import org.lwjgl.opengl.GL33.glCheckFramebufferStatus
import org.lwjgl.opengl.GL33.glDrawBuffer
import org.lwjgl.opengl.GL33.glFramebufferTexture2D
import org.lwjgl.opengl.GL33.glGenFramebuffers
import org.lwjgl.opengl.GL33.glGetFramebufferAttachmentParameteri
import org.lwjgl.opengl.GL33.glGetInteger
import org.lwjgl.opengl.GL33.glReadBuffer
import org.lwjgl.opengl.GL33.glViewport
import java.util.IdentityHashMap

/**
 * 为仍使用 OpenGL 的渲染后端提供 26.3 目标桥接。
 *
 * 原版目标只拥有 GPU 纹理，本桥接仅拥有引用这些纹理的 FBO，不会关闭原版或 Iris 的 attachment。
 * [framebufferId] 在客户端渲染线程调用；目标销毁时必须经 [release] 删除桥接 FBO。
 * Vulkan 目标没有 OpenGL 句柄，访问时明确报错，不伪装成可用目标。
 */
object OpenGlRenderTargetBridge {
    /** 以目标对象身份索引的可变资源账本，生命周期由 RenderTarget Mixin 驱动。 */
    private val targets = IdentityHashMap<RenderTarget, TargetFramebuffer>()

    /**
     * 确认当前原版设备使用 OpenGL；只能在渲染线程、设备初始化后调用。
     *
     * 示例：`OpenGlRenderTargetBridge.requireOpenGlBackend()`。
     * @throws IllegalStateException 当前设备不是 OpenGL 后端
     */
    @JvmStatic
    fun requireOpenGlBackend() {
        RenderSystem.assertOnRenderThread()
        val backend = RenderSystem.getDevice().deviceInfo.backendName()
        check(backend.equals("OpenGL", ignoreCase = true)) {
            "CooParticlesAPI OpenGL rendering requires the OpenGL backend, active backend: $backend"
        }
    }

    /**
     * 返回引用目标当前纹理和 mip 的 FBO，原版纹理替换后自动重建。
     *
     * 示例：`val fbo = OpenGlRenderTargetBridge.framebufferId(target)`。
     * @param target 已创建 attachment 的原版渲染目标
     * @return 本桥接拥有的完整 FBO，不是原版内部缓存 FBO 的身份
     * @throws IllegalStateException 非 OpenGL、目标未初始化或 attachment 不完整
     */
    @JvmStatic
    fun framebufferId(target: RenderTarget): Int {
        requireOpenGlBackend()
        val color = checkNotNull(target.colorTexture) { "Render target color texture is not initialized" }
        val depth = target.depthTexture
        val colorMip = target.colorTextureView?.baseMipLevel() ?: 0
        val depthMip = target.depthTextureView?.baseMipLevel() ?: 0
        targets[target]?.let { cached ->
            if (cached.color === color && cached.depth === depth &&
                cached.colorMip == colorMip && cached.depthMip == depthMip
            ) {
                return cached.id
            }
        }
        release(target)
        val colorId = color.glTextureId
        val depthId = depth?.glTextureId
        val previousRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
        val previousDraw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        val framebuffer = glGenFramebuffers()
        try {
            bindFramebuffer(GL_FRAMEBUFFER, framebuffer)
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorId, colorMip)
            if (depthId != null) {
                val attachment = if (depth?.format?.hasStencilAspect() == true) GL_DEPTH_STENCIL_ATTACHMENT else GL_DEPTH_ATTACHMENT
                glFramebufferTexture2D(GL_FRAMEBUFFER, attachment, GL_TEXTURE_2D, depthId, depthMip)
            }
            glDrawBuffer(GL_COLOR_ATTACHMENT0)
            glReadBuffer(GL_COLOR_ATTACHMENT0)
            val status = glCheckFramebufferStatus(GL_FRAMEBUFFER)
            check(status == GL_FRAMEBUFFER_COMPLETE) {
                "Render target bridge framebuffer is incomplete: 0x${status.toString(16)}"
            }
            targets[target] = TargetFramebuffer(framebuffer, color, depth, colorMip, depthMip)
            return framebuffer
        } catch (failure: Throwable) {
            GlStateManager._glDeleteFramebuffers(framebuffer)
            throw failure
        } finally {
            bindFramebuffer(GL_READ_FRAMEBUFFER, previousRead)
            bindFramebuffer(GL_DRAW_FRAMEBUFFER, previousDraw)
        }
    }

    /**
     * 按真实纹理与 mip 判断 FBO 是否使用目标 attachment，而非比较不同所有者的 FBO 编号。
     *
     * 示例：`OpenGlRenderTargetBridge.matches(target, boundFbo)`。
     * @param target 参与候选匹配的原版目标
     * @param framebuffer 当前查询的 FBO，默认窗口 FBO 不匹配
     * @return 颜色和深度 attachment 均与目标一致时为 true
     */
    @JvmStatic
    fun matches(target: RenderTarget, framebuffer: Int): Boolean {
        requireOpenGlBackend()
        if (framebuffer <= 0) return false
        val color = target.colorTexture ?: return false
        val previousRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
        return try {
            bindFramebuffer(GL_READ_FRAMEBUFFER, framebuffer)
            attachmentMatches(GL_COLOR_ATTACHMENT0, color, target.colorTextureView?.baseMipLevel() ?: 0) &&
                attachmentMatches(GL_DEPTH_ATTACHMENT, target.depthTexture, target.depthTextureView?.baseMipLevel() ?: 0)
        } finally {
            bindFramebuffer(GL_READ_FRAMEBUFFER, previousRead)
        }
    }

    /**
     * 删除指定目标的桥接 FBO，不删除目标纹理；未创建桥接时无副作用。
     *
     * 示例：目标 `destroyBuffers()` 的 HEAD 调用 `release(target)`。
     * @param target 即将销毁或替换 attachment 的目标
     */
    @JvmStatic
    fun release(target: RenderTarget) {
        val cached = targets.remove(target) ?: return
        RenderSystem.assertOnRenderThread()
        GlStateManager._glDeleteFramebuffers(cached.id)
    }

    /**
     * 同步 RenderPearl 缓存与真实 framebuffer 绑定，用于恢复可能被 Iris 直接修改的 GL 状态。
     *
     * 示例：`bindFramebuffer(GL_DRAW_FRAMEBUFFER, previousDraw)`。
     * @param target GL_READ_FRAMEBUFFER、GL_DRAW_FRAMEBUFFER 或 GL_FRAMEBUFFER
     * @param framebuffer 待绑定的 FBO；恢复时须传入调用方实际捕获的编号
     */
    @JvmStatic
    fun bindFramebuffer(target: Int, framebuffer: Int) {
        GlStateManager._glBindFramebuffer(target, framebuffer)
        glBindFramebuffer(target, framebuffer)
    }

    /** 查询单个 attachment；无纹理目标只接受完全未绑定的 attachment。 */
    private fun attachmentMatches(attachment: Int, texture: GpuTexture?, mip: Int): Boolean {
        val kind = glGetFramebufferAttachmentParameteri(GL_READ_FRAMEBUFFER, attachment, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)
        if (texture == null) return kind == GL_NONE
        return kind == GL_TEXTURE &&
            glGetFramebufferAttachmentParameteri(GL_READ_FRAMEBUFFER, attachment, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME) == texture.glTextureId &&
            glGetFramebufferAttachmentParameteri(GL_READ_FRAMEBUFFER, attachment, GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL) == mip
    }

    /**
     * 桥接 FBO 的 attachment 身份快照，不拥有纹理。
     * @property id 本桥接拥有的 FBO
     * @property color 原版颜色纹理身份
     * @property depth 原版深度纹理身份，无深度时为 null
     * @property colorMip 颜色视图的起始 mip
     * @property depthMip 深度视图的起始 mip
     */
    private data class TargetFramebuffer(
        val id: Int,
        val color: GpuTexture,
        val depth: GpuTexture?,
        val colorMip: Int,
        val depthMip: Int,
    )
}

/**
 * 取得 GPU 纹理的 OpenGL 对象名，不改变其所有权。
 * 示例：`val id = texture.glTextureId`。
 * @receiver 未关闭的 OpenGL 后端纹理
 * @throws IllegalStateException Vulkan 纹理或已关闭纹理没有可用 OpenGL 对象名
 */
val GpuTexture.glTextureId: Int
    get() {
        check(this is GlTexture && !isClosed) { "GPU texture is not a live OpenGL texture: $label" }
        return glId()
    }

/**
 * 获取 GPU sampler 的 OpenGL 对象名，不接管原版 sampler 缓存的生命周期。
 * 示例：`glBindSampler(1, sampler.glSamplerId)`。
 * @receiver 未关闭的 OpenGL sampler
 * @throws IllegalStateException 不是 OpenGL sampler 或已关闭
 */
val GpuSampler.glSamplerId: Int
    get() {
        check(this is GlSampler && !isClosed) { "GPU sampler is not a live OpenGL sampler" }
        return id
    }

/**
 * 读取原版目标的颜色纹理对象名。
 * 示例：`val color = target.colorTextureId`。
 * @receiver 由 OpenGL 后端创建的目标
 * @return 颜色纹理名，目标尚未初始化时为 -1
 */
val RenderTarget.colorTextureId: Int
    get() = colorTexture?.glTextureId ?: -1

/**
 * 读取原版目标的深度纹理对象名。
 * 示例：`val depth = target.depthTextureId`。
 * @receiver 由 OpenGL 后端创建的目标
 * @return 深度纹理名，无深度 attachment 时为 -1
 */
val RenderTarget.depthTextureId: Int
    get() = depthTexture?.glTextureId ?: -1

/**
 * 获取用于旧 OpenGL 调用方的非拥有型 FBO。
 * 示例：`val framebuffer = target.frameBufferId`。
 * @receiver 具有已初始化 OpenGL attachment 的目标
 * @return 本库桥接 FBO，不可用于判断原版或 Iris FBO 身份
 */
val RenderTarget.frameBufferId: Int
    get() = OpenGlRenderTargetBridge.framebufferId(this)

/**
 * 为旧绘制入口绑定目标；调用方必须自行捕获和恢复原先的读写 FBO 与 viewport。
 * 示例：`target.bindWrite(true)`。
 * @receiver 具有已初始化 OpenGL attachment 的目标
 * @param setViewport 是否将 viewport 设为整个目标
 */
fun RenderTarget.bindWrite(setViewport: Boolean) {
    OpenGlRenderTargetBridge.bindFramebuffer(GL_FRAMEBUFFER, frameBufferId)
    if (setViewport) {
        GlStateManager._viewport(0, 0, width, height)
        glViewport(0, 0, width, height)
    }
}

/**
 * 按旧 RenderTarget 语义绑定颜色纹理到当前活动单元，不绑定读 FBO。
 * 示例：`target.bindRead()`。
 * @receiver 具有已初始化 OpenGL 颜色 attachment 的目标
 */
fun RenderTarget.bindRead() {
    OpenGlRenderTargetBridge.requireOpenGlBackend()
    GlStateManager._bindTexture(colorTextureId)
    glBindTexture(GL_TEXTURE_2D, colorTextureId)
}
