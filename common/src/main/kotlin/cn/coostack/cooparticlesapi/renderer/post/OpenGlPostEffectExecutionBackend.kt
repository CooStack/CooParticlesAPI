package cn.coostack.cooparticlesapi.renderer.post

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.compat.IrisCompat
import cn.coostack.cooparticlesapi.cparticle.CParticleIndexedBlendState
import cn.coostack.cooparticlesapi.compat.IrisTerrainDepthTexture
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneResource
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneResources
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneTargets
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderPipelineManager
import cn.coostack.cooparticlesapi.renderer.pipeline.setUniform
import cn.coostack.cooparticlesapi.renderer.shader.AdvancedShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.CooTextureFormat
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.glsl.GlDepthTextureFormat
import cn.coostack.cooparticlesapi.renderer.shader.glsl.IdentifierShader
import cn.coostack.cooparticlesapi.renderer.shader.glsl.SimpleFrameBuffer
import cn.coostack.cooparticlesapi.renderer.shader.texture.IdentifierTexture
import cn.coostack.cooparticlesapi.renderer.shader.vertex.SimpleVertexBuffer
import cn.coostack.cooparticlesapi.renderer.shader.vertex.VertexBuffers
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import java.nio.ByteBuffer
import org.lwjgl.opengl.GL33.GL_ACTIVE_TEXTURE
import org.lwjgl.opengl.GL33.GL_BLEND
import org.lwjgl.opengl.GL33.GL_BLEND_DST_ALPHA
import org.lwjgl.opengl.GL33.GL_BLEND_DST_RGB
import org.lwjgl.opengl.GL33.GL_BLEND_EQUATION_ALPHA
import org.lwjgl.opengl.GL33.GL_BLEND_EQUATION_RGB
import org.lwjgl.opengl.GL33.GL_BLEND_SRC_ALPHA
import org.lwjgl.opengl.GL33.GL_BLEND_SRC_RGB
import org.lwjgl.opengl.GL33.GL_COLOR
import org.lwjgl.opengl.GL33.GL_COLOR_BUFFER_BIT
import org.lwjgl.opengl.GL33.GL_COLOR_WRITEMASK
import org.lwjgl.opengl.GL33.GL_COLOR_ATTACHMENT0
import org.lwjgl.opengl.GL33.GL_CULL_FACE
import org.lwjgl.opengl.GL33.GL_CURRENT_PROGRAM
import org.lwjgl.opengl.GL33.GL_DEPTH_BUFFER_BIT
import org.lwjgl.opengl.GL33.GL_DEPTH_FUNC
import org.lwjgl.opengl.GL33.GL_DEPTH_ATTACHMENT
import org.lwjgl.opengl.GL33.GL_DEPTH_COMPONENT
import org.lwjgl.opengl.GL33.GL_DEPTH_COMPONENT16
import org.lwjgl.opengl.GL33.GL_DEPTH_COMPONENT24
import org.lwjgl.opengl.GL33.GL_DEPTH_COMPONENT32
import org.lwjgl.opengl.GL33.GL_DEPTH_COMPONENT32F
import org.lwjgl.opengl.GL33.GL_DEPTH24_STENCIL8
import org.lwjgl.opengl.GL33.GL_DEPTH32F_STENCIL8
import org.lwjgl.opengl.GL33.GL_DEPTH_STENCIL
import org.lwjgl.opengl.GL33.GL_DEPTH_STENCIL_ATTACHMENT
import org.lwjgl.opengl.GL33.GL_FLOAT
import org.lwjgl.opengl.GL33.GL_FLOAT_32_UNSIGNED_INT_24_8_REV
import org.lwjgl.opengl.GL33.GL_CLAMP_TO_EDGE
import org.lwjgl.opengl.GL33.GL_TEXTURE_MAG_FILTER
import org.lwjgl.opengl.GL33.GL_TEXTURE_MIN_FILTER
import org.lwjgl.opengl.GL33.GL_TEXTURE_WRAP_S
import org.lwjgl.opengl.GL33.GL_TEXTURE_WRAP_T
import org.lwjgl.opengl.GL33.GL_DEPTH_TEST
import org.lwjgl.opengl.GL33.GL_DEPTH_WRITEMASK
import org.lwjgl.opengl.GL33.GL_DRAW_FRAMEBUFFER
import org.lwjgl.opengl.GL33.GL_DRAW_FRAMEBUFFER_BINDING
import org.lwjgl.opengl.GL33.GL_DRAW_BUFFER0
import org.lwjgl.opengl.GL33.GL_FRAMEBUFFER
import org.lwjgl.opengl.GL33.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
import org.lwjgl.opengl.GL33.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
import org.lwjgl.opengl.GL33.GL_FRAMEBUFFER_COMPLETE
import org.lwjgl.opengl.GL33.GL_LINEAR
import org.lwjgl.opengl.GL33.GL_LINEAR_MIPMAP_LINEAR
import org.lwjgl.opengl.GL33.GL_MAX_DRAW_BUFFERS
import org.lwjgl.opengl.GL33.GL_NEAREST
import org.lwjgl.opengl.GL33.GL_NONE
import org.lwjgl.opengl.GL33.GL_NO_ERROR
import org.lwjgl.opengl.GL33.GL_READ_FRAMEBUFFER
import org.lwjgl.opengl.GL33.GL_READ_FRAMEBUFFER_BINDING
import org.lwjgl.opengl.GL33.GL_READ_BUFFER
import org.lwjgl.opengl.GL33.GL_RENDERBUFFER
import org.lwjgl.opengl.GL33.GL_RENDERBUFFER_BINDING
import org.lwjgl.opengl.GL33.GL_RENDERBUFFER_INTERNAL_FORMAT
import org.lwjgl.opengl.GL33.GL_RENDERBUFFER_DEPTH_SIZE
import org.lwjgl.opengl.GL33.GL_RENDERBUFFER_STENCIL_SIZE
import org.lwjgl.opengl.GL33.GL_RENDERBUFFER_WIDTH
import org.lwjgl.opengl.GL33.GL_RENDERBUFFER_HEIGHT
import org.lwjgl.opengl.GL33.GL_RGBA
import org.lwjgl.opengl.GL33.GL_RGBA8
import org.lwjgl.opengl.GL33.GL_RGBA16F
import org.lwjgl.opengl.GL33.GL_RGBA32F
import org.lwjgl.opengl.GL33.GL_SCISSOR_BOX
import org.lwjgl.opengl.GL33.GL_SCISSOR_TEST
import org.lwjgl.opengl.GL33.GL_SRGB8_ALPHA8
import org.lwjgl.opengl.GL33.GL_STENCIL_ATTACHMENT
import org.lwjgl.opengl.GL33.GL_TEXTURE0
import org.lwjgl.opengl.GL33.GL_TEXTURE
import org.lwjgl.opengl.GL33.GL_TEXTURE_2D
import org.lwjgl.opengl.GL33.GL_TEXTURE_BASE_LEVEL
import org.lwjgl.opengl.GL33.GL_UNSIGNED_BYTE
import org.lwjgl.opengl.GL33.GL_UNSIGNED_INT
import org.lwjgl.opengl.GL33.GL_UNSIGNED_INT_24_8
import org.lwjgl.opengl.GL33.GL_UNSIGNED_SHORT
import org.lwjgl.opengl.GL33.GL_TEXTURE_BINDING_2D
import org.lwjgl.opengl.GL33.GL_TEXTURE_INTERNAL_FORMAT
import org.lwjgl.opengl.GL33.GL_TEXTURE_DEPTH_SIZE
import org.lwjgl.opengl.GL33.GL_TEXTURE_STENCIL_SIZE
import org.lwjgl.opengl.GL33.GL_TEXTURE_MAX_LEVEL
import org.lwjgl.opengl.GL33.GL_TEXTURE_WIDTH
import org.lwjgl.opengl.GL33.GL_TEXTURE_HEIGHT
import org.lwjgl.opengl.GL33.GL_VIEWPORT
import org.lwjgl.opengl.GL33.GL_VERTEX_ARRAY_BINDING
import org.lwjgl.opengl.GL11.GL_POLYGON_OFFSET_FACTOR
import org.lwjgl.opengl.GL11.GL_POLYGON_OFFSET_FILL
import org.lwjgl.opengl.GL11.GL_POLYGON_OFFSET_UNITS
import org.lwjgl.opengl.GL33.glActiveTexture
import org.lwjgl.opengl.GL33.glBindFramebuffer
import org.lwjgl.opengl.GL33.glBindRenderbuffer
import org.lwjgl.opengl.GL33.glBindTexture
import org.lwjgl.opengl.GL33.glBindVertexArray
import org.lwjgl.opengl.GL33.glBlendEquationSeparate
import org.lwjgl.opengl.GL33.glBlendFuncSeparate
import org.lwjgl.opengl.GL33.glBlitFramebuffer
import org.lwjgl.opengl.GL33.glCheckFramebufferStatus
import org.lwjgl.opengl.GL33.glClearBufferfv
import org.lwjgl.opengl.GL30.glColorMaski
import org.lwjgl.opengl.GL30.glDisablei
import org.lwjgl.opengl.GL30.glEnablei
import org.lwjgl.opengl.GL30.glIsEnabledi
import org.lwjgl.opengl.GL33.glDepthMask
import org.lwjgl.opengl.GL33.glDeleteFramebuffers
import org.lwjgl.opengl.GL33.glDeleteTextures
import org.lwjgl.opengl.GL33.glDepthFunc
import org.lwjgl.opengl.GL33.glDisable
import org.lwjgl.opengl.GL33.glEnable
import org.lwjgl.opengl.GL33.glDrawBuffer
import org.lwjgl.opengl.GL33.glDrawBuffers
import org.lwjgl.opengl.GL33.glFramebufferTexture2D
import org.lwjgl.opengl.GL33.glFramebufferRenderbuffer
import org.lwjgl.opengl.GL33.glTexImage2D
import org.lwjgl.opengl.GL33.glTexParameteri
import org.lwjgl.opengl.GL33.glGenFramebuffers
import org.lwjgl.opengl.GL33.glGenTextures
import org.lwjgl.opengl.GL33.glGetBoolean
import org.lwjgl.opengl.GL11.glGetFloat
import org.lwjgl.opengl.GL33.glGetError
import org.lwjgl.opengl.GL33.glGetFramebufferAttachmentParameteri
import org.lwjgl.opengl.GL33.glGetInteger
import org.lwjgl.opengl.GL33.glGetIntegeri_v
import org.lwjgl.opengl.GL33.glGetIntegerv
import org.lwjgl.opengl.GL33.glGetRenderbufferParameteri
import org.lwjgl.opengl.GL33.glGetTexLevelParameteri
import org.lwjgl.opengl.GL33.glGetTexParameteri
import org.lwjgl.opengl.GL33.glGetUniformLocation
import org.lwjgl.opengl.GL33.glIsEnabled
import org.lwjgl.opengl.GL33.glIsVertexArray
import org.lwjgl.opengl.GL11.glPolygonOffset
import org.lwjgl.opengl.GL33.glReadBuffer
import org.lwjgl.opengl.GL33.glScissor
import org.lwjgl.opengl.GL33.glUseProgram
import org.lwjgl.opengl.GL33.glViewport
import kotlin.math.max

/**
 * Minecraft 客户端 OpenGL 后处理执行后端。
 *
 * 普通自定义 post 效果不应该直接调用这个对象；调用方声明 [PostEffectType] 和 [PostEffectChain] 后，
 * [PostEffectFrameExecutor] 会把可执行 step 交给这里。
 *
 * 它负责的底层工作包括：
 *
 * - 缓存 shader program 和屏幕 quad vertex buffer
 * - 为 pass 输出创建、复用、缩放临时 FBO
 * - 复制 scene color，避免直接读写同一个 framebuffer
 * - 绑定 sampler 到显式或自动分配的 texture slot
 * - 维护同一 instance 内的上游 pass 输出，支持 `A/C/E -> B/D -> Final` 图连接
 * - 上传生命周期、binding、当前相机矩阵、用户参数等 uniform
 *
 * 这层实现替代了每个 post 效果各自手写 GL 状态保存、FBO 生命周期、纹理绑定和 shader uniform 上传。
 */
internal object OpenGlPostEffectExecutionBackend : PostEffectExecutionBackend,
    PostEffectFramePreparationBackend,
    PostEffectForegroundReplayBackend,
    PostEffectResourceBackend,
    PostEffectAttachmentPreparationBackend {
    private val screenVertexId: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "pipeline/vertexes/screen.vsh")
    private val bindingMaskFragmentId: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "post/binding_mask.fsh")

    private val programs = LinkedHashMap<PostProgramKey, CooShaderProgram>()
    private val customTextures = LinkedHashMap<ResourceLocation, IdentifierTexture>()
    private val targets = LinkedHashMap<String, ManagedTarget>()
    private val namedTargetTextures = LinkedHashMap<NamedAttachment, Int>()
    private val instanceStates = LinkedHashMap<String, InstanceFrameState>()
    private val instanceLastSeenFrame = LinkedHashMap<String, Long>()
    private var frameCounter: Long = 0
    private var screenBuffer: SimpleVertexBuffer? = null
    private var irisDepthReadFramebuffer = 0
    private var terrainOpaqueDepthCapture: DepthCapture? = null
    private var terrainTranslucentBeforeDepthCapture: DepthCapture? = null
    private var terrainTranslucentAfterDepthCapture: DepthCapture? = null
    private var cParticleCoverageDepthCapture: DepthCapture? = null
    private var cParticleCoverageColorCapture: ColorCapture? = null
    private var terrainDepthIrisSourceResolved = false
    private var terrainDepthIrisSource: IrisTerrainDepthTexture? = null
    private var terrainDepthSourceCache: DepthSourceCache? = null
    private var terrainOpaqueDepthValid = false
    private var terrainTranslucentBeforeDepthValid = false
    private var terrainTranslucentAfterDepthValid = false
    private var cParticleCoverageValid = false
    private var sceneCopy: ManagedTarget? = null
    private var preparedSceneFrame: FrameKey? = null
    private var preparedSceneCopySpec: PostEffectAttachmentSpec? = null
    private var chainedSceneFramebufferId: Int? = null
    private var warnedSceneCopyFailure = false
    private var warnedTerrainSceneCopyFailure = false
    private val warnedTextureContractFailures = linkedSetOf<String>()

    /**
     * 初始化或准备 `OpenGlPostEffectExecutionBackend` 的 `prepareFrame` 阶段，使后续渲染调用可以使用相关资源。
     *
     * 示例：`prepareFrame(context = context)`。
     *
     * @param context 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    override fun prepareFrame(context: RenderFrameContext) {
        preparedSceneFrame = null
        preparedSceneCopySpec = null
        chainedSceneFramebufferId = null
        instanceStates.clear()
        namedTargetTextures.clear()
        frameCounter++
        evictStaleTargets()
    }

    /** Iris final pass 后只刷新 scene copy 的帧标记，保留已捕获 attachment 和 pass 状态。 */
    override fun refreshSceneFrame(context: RenderFrameContext) {
        preparedSceneFrame = null
        preparedSceneCopySpec = null
        chainedSceneFramebufferId = null
    }

    /** 只使颜色副本失效；直接前景绘制后的 chain framebuffer 仍是后续 SceneColor 来源。 */
    private fun invalidatePreparedSceneCopy() {
        preparedSceneFrame = null
        preparedSceneCopySpec = null
    }

    override fun invalidateSceneColorCopy() {
        invalidatePreparedSceneCopy()
    }

    /** 把延迟前景绘制到 Mapping 已写入的最终目标，并继续维护 SceneColor 合成链。 */
    override fun replayForeground(context: RenderFrameContext, render: () -> Unit): Boolean {
        RenderSystem.assertOnRenderThread()
        val target = context.finalCompositeTarget ?: Minecraft.getInstance().mainRenderTarget
        val framebuffer = context.finalCompositeFramebufferId?.takeIf { it > 0 } ?: target.frameBufferId
        if (framebuffer <= 0) return false
        val width = max(1, context.targetWidth ?: target.width)
        val height = max(1, context.targetHeight ?: target.height)
        val previousReadFramebuffer = glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
        val previousDrawFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        val previousViewport = IntArray(4)
        glGetIntegerv(GL_VIEWPORT, previousViewport)
        try {
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer)
            glViewport(0, 0, width, height)
            if (glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) return false
            val depthAttachmentType = glGetFramebufferAttachmentParameteri(
                GL_DRAW_FRAMEBUFFER,
                GL_DEPTH_ATTACHMENT,
                GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE,
            )
            if (depthAttachmentType == GL_NONE) return false
            render()
        } finally {
            glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3])
            glBindFramebuffer(GL_READ_FRAMEBUFFER, previousReadFramebuffer)
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer)
        }
        chainedSceneFramebufferId = framebuffer
        invalidatePreparedSceneCopy()
        return true
    }


    internal fun beginTerrainDepthFrame() {
        terrainDepthIrisSourceResolved = false
        terrainDepthIrisSource = null
        terrainDepthSourceCache = null
        terrainOpaqueDepthValid = false
        terrainTranslucentBeforeDepthValid = false
        terrainTranslucentAfterDepthValid = false
        cParticleCoverageValid = false
    }

    /** 在实体绘制前捕获当前 opaque terrain 深度。 */
    internal fun captureTerrainOpaqueDepth() {
        terrainOpaqueDepthValid = captureCurrentDepth(terrainOpaqueDepthCapture) { capture ->
            terrainOpaqueDepthCapture = capture
        }
    }

    /** 在半透明 terrain 绘制前捕获深度；同一帧只保留第一次快照。 */
    internal fun captureTerrainTranslucentDepthBefore() {
        if (terrainTranslucentBeforeDepthValid) return
        if (captureCurrentDepth(terrainTranslucentBeforeDepthCapture) { capture ->
                terrainTranslucentBeforeDepthCapture = capture
            }
        ) {
            terrainTranslucentBeforeDepthValid = true
        }
    }

    /** 在半透明 terrain 绘制后捕获深度，供 shader 识别实际改变深度的液体像素。 */
    internal fun captureTerrainTranslucentDepthAfter() {
        if (!terrainTranslucentBeforeDepthValid || terrainTranslucentAfterDepthValid) return
        terrainTranslucentAfterDepthValid = captureCurrentDepth(terrainTranslucentAfterDepthCapture) { capture ->
            terrainTranslucentAfterDepthCapture = capture
        }
    }

    /** 返回本帧实际 opaque terrain 深度快照纹理。 */
    internal fun terrainOpaqueDepthTexture(): Int? {
        return terrainOpaqueDepthCapture?.textureId?.takeIf { terrainOpaqueDepthValid && it > 0 }
    }

    /**
     * 返回半透明 terrain 绘制前的深度；未执行半透明 pass 时，opaque 深度就是等价的 before 快照。
     */
    internal fun terrainTranslucentDepthBeforeTexture(): Int? {
        return selectTranslucentDepthSnapshot(
            primary = null,
            before = terrainTranslucentBeforeDepthCapture?.textureId
                ?.takeIf { terrainTranslucentBeforeDepthValid },
            opaque = terrainOpaqueDepthCapture?.textureId?.takeIf { terrainOpaqueDepthValid },
        )
    }

    /**
     * 返回半透明 terrain 绘制后的深度；after 缺失时依次复用 before、opaque，保持无半透明地形帧可执行。
     */
    internal fun terrainTranslucentDepthAfterTexture(): Int? {
        return selectTranslucentDepthSnapshot(
            primary = terrainTranslucentAfterDepthCapture?.textureId
                ?.takeIf { terrainTranslucentAfterDepthValid },
            before = terrainTranslucentBeforeDepthCapture?.textureId
                ?.takeIf { terrainTranslucentBeforeDepthValid },
            opaque = terrainOpaqueDepthCapture?.textureId?.takeIf { terrainOpaqueDepthValid },
        )
    }

    /** 按 after、before、opaque 顺序选择第一个有效深度纹理。 */
    internal fun selectTranslucentDepthSnapshot(primary: Int?, before: Int?, opaque: Int?): Int? {
        return primary?.takeIf { it > 0 }
            ?: before?.takeIf { it > 0 }
            ?: opaque?.takeIf { it > 0 }
    }

    /** 返回本帧全部可见 CParticle 实际通过隔离深度测试后的覆盖蒙版。 */
    internal fun cParticleCoverageTexture(): Int? {
        return cParticleCoverageColorCapture?.textureId?.takeIf { cParticleCoverageValid && it > 0 }
    }

    /**
     * 复制当前场景深度并在隔离 framebuffer 中绘制 CParticle 覆盖蒙版。
     *
     * 回调只能执行 CParticle 的覆盖绘制；该 framebuffer 不会写回 Iris 或原版场景深度。
     */
    internal fun captureCParticleCoverage(render: () -> Unit): Boolean {
        RenderSystem.assertOnRenderThread()
        val previousDepthCapture = cParticleCoverageDepthCapture
        if (!captureCurrentDepth(cParticleCoverageDepthCapture) { capture ->
                cParticleCoverageDepthCapture = capture
            }
        ) return false
        val depthCapture = cParticleCoverageDepthCapture ?: return false
        val previousColorCapture = cParticleCoverageColorCapture
        val colorCapture = ensureCParticleCoverageColor(depthCapture)
        val clearMask = !cParticleCoverageValid ||
            depthCapture !== previousDepthCapture || colorCapture !== previousColorCapture
        val previousDrawFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        val previousViewport = IntArray(4)
        glGetIntegerv(GL_VIEWPORT, previousViewport)
        val previousDepthMask = glGetBoolean(GL_DEPTH_WRITEMASK)
        val previousColorMask = IntArray(4)
        glGetIntegeri_v(GL_COLOR_WRITEMASK, 0, previousColorMask)
        val previousScissorEnabled = glIsEnabled(GL_SCISSOR_TEST)
        val previousScissorBox = IntArray(4)
        glGetIntegerv(GL_SCISSOR_BOX, previousScissorBox)
        var rendered = false
        clearPendingGlErrors()
        try {
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, depthCapture.framebufferId)
            glFramebufferTexture2D(
                GL_DRAW_FRAMEBUFFER,
                GL_COLOR_ATTACHMENT0,
                GL_TEXTURE_2D,
                colorCapture.textureId,
                0
            )
            glDrawBuffer(GL_COLOR_ATTACHMENT0)
            if (glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) return false
            glViewport(0, 0, depthCapture.width, depthCapture.height)
            glColorMaski(0, true, true, true, true)
            glDepthMask(false)
            glDisable(GL_SCISSOR_TEST)
            if (clearMask) {
                glClearBufferfv(GL_COLOR, 0, floatArrayOf(0F, 0F, 0F, 0F))
            }
            render()
            rendered = operationCompletedWithoutGlError()
        } finally {
            glDepthMask(previousDepthMask)
            glColorMaski(
                0,
                previousColorMask[0] != 0,
                previousColorMask[1] != 0,
                previousColorMask[2] != 0,
                previousColorMask[3] != 0
            )
            if (previousScissorEnabled) glEnable(GL_SCISSOR_TEST) else glDisable(GL_SCISSOR_TEST)
            glScissor(
                previousScissorBox[0],
                previousScissorBox[1],
                previousScissorBox[2],
                previousScissorBox[3]
            )
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer)
            glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3])
        }
        if (rendered) cParticleCoverageValid = true
        return rendered
    }

    private fun ensureCParticleCoverageColor(depthCapture: DepthCapture): ColorCapture {
        val current = cParticleCoverageColorCapture
        if (current != null && current.width == depthCapture.width && current.height == depthCapture.height) {
            return current
        }
        current?.release()
        cParticleCoverageColorCapture = null
        val previousActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE)
        glActiveTexture(GL_TEXTURE0)
        val previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D)
        val textureId = glGenTextures()
        return try {
            glBindTexture(GL_TEXTURE_2D, textureId)
            glTexImage2D(
                GL_TEXTURE_2D,
                0,
                GL_RGBA8,
                depthCapture.width,
                depthCapture.height,
                0,
                GL_RGBA,
                GL_UNSIGNED_BYTE,
                null as ByteBuffer?
            )
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            ColorCapture(textureId, depthCapture.width, depthCapture.height).also {
                cParticleCoverageColorCapture = it
            }
        } catch (error: RuntimeException) {
            glDeleteTextures(textureId)
            throw error
        } finally {
            glBindTexture(GL_TEXTURE_2D, previousTexture)
            glActiveTexture(previousActiveTexture)
        }
    }

    private fun currentDepthSourceFramebuffer(): Int {
        return glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING).takeIf { it > 0 }
            ?: Minecraft.getInstance().mainRenderTarget.frameBufferId
    }

    private fun captureCurrentDepth(
        existing: DepthCapture?,
        accept: (DepthCapture) -> Unit
    ): Boolean {
        val irisDepth = if (!terrainDepthIrisSourceResolved) {
            terrainDepthIrisSourceResolved = true
            terrainDepthIrisSource = IrisCompat.currentSceneDepthTexture()
            terrainDepthIrisSource
        } else {
            terrainDepthIrisSource
        }
        val fallbackSourceFramebuffer = currentDepthSourceFramebuffer()
        if (irisDepth == null && fallbackSourceFramebuffer <= 0) return false
        val cachedSource = terrainDepthSourceCache?.takeIf {
            it.irisTextureId == (irisDepth?.textureId ?: 0) &&
                it.framebufferId == (if (irisDepth == null) fallbackSourceFramebuffer else 0)
        }
        val source = cachedSource ?: run {
            val sourceFormat = if (irisDepth != null) {
                resolveDepthTextureFormat(irisDepth.textureId)
            } else {
                resolveDepthFramebufferFormat(fallbackSourceFramebuffer)
            } ?: return false
            val fallbackSourceSize = if (irisDepth == null) {
                resolveDepthFramebufferSize(fallbackSourceFramebuffer)
            } else {
                null
            }
            DepthSourceCache(
                irisTextureId = irisDepth?.textureId ?: 0,
                framebufferId = if (irisDepth == null) fallbackSourceFramebuffer else 0,
                width = (irisDepth?.width ?: fallbackSourceSize?.first
                    ?: Minecraft.getInstance().mainRenderTarget.width).coerceAtLeast(1),
                height = (irisDepth?.height ?: fallbackSourceSize?.second
                    ?: Minecraft.getInstance().mainRenderTarget.height).coerceAtLeast(1),
                format = sourceFormat,
            )
        }.also { terrainDepthSourceCache = it }
        val sourceFormat = source.format
        val sourceWidth = source.width
        val sourceHeight = source.height
        val capture = if (
            existing != null &&
            existing.width == sourceWidth &&
            existing.height == sourceHeight &&
            existing.format == sourceFormat
        ) {
            existing
        } else {
            createDepthCapture(sourceWidth, sourceHeight, sourceFormat)
        }
        val previousReadFramebuffer = glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
        val previousDrawFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        var sourceFramebuffer = fallbackSourceFramebuffer
        var previousReadBuffer = GL_NONE
        var previousDrawBuffer = GL_NONE
        var copied = false
        clearPendingGlErrors()
        try {
            if (irisDepth != null) {
                if (irisDepthReadFramebuffer <= 0) {
                    irisDepthReadFramebuffer = glGenFramebuffers()
                }
                sourceFramebuffer = irisDepthReadFramebuffer
                glBindFramebuffer(GL_READ_FRAMEBUFFER, sourceFramebuffer)
                glFramebufferTexture2D(
                    GL_READ_FRAMEBUFFER,
                    GL_DEPTH_ATTACHMENT,
                    GL_TEXTURE_2D,
                    0,
                    0
                )
                glFramebufferTexture2D(
                    GL_READ_FRAMEBUFFER,
                    GL_STENCIL_ATTACHMENT,
                    GL_TEXTURE_2D,
                    0,
                    0
                )
                glFramebufferTexture2D(
                    GL_READ_FRAMEBUFFER,
                    sourceFormat.attachment,
                    GL_TEXTURE_2D,
                    irisDepth.textureId,
                    0
                )
                glReadBuffer(GL_NONE)
                if (glCheckFramebufferStatus(GL_READ_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) return false
            } else {
                glBindFramebuffer(GL_READ_FRAMEBUFFER, sourceFramebuffer)
                previousReadBuffer = glGetInteger(GL_READ_BUFFER)
                val depthType = glGetFramebufferAttachmentParameteri(
                    GL_READ_FRAMEBUFFER,
                    GL_DEPTH_ATTACHMENT,
                    GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
                )
                if (depthType == GL_NONE) return false
                glReadBuffer(GL_NONE)
            }
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, capture.framebufferId)
            previousDrawBuffer = glGetInteger(GL_DRAW_BUFFER0)
            glDrawBuffer(GL_NONE)
            if (!operationCompletedWithoutGlError()) return false
            clearPendingGlErrors()
            glBlitFramebuffer(
                0,
                0,
                sourceWidth,
                sourceHeight,
                0,
                0,
                capture.width,
                capture.height,
                GL_DEPTH_BUFFER_BIT,
                GL_NEAREST
            )
            copied = operationCompletedWithoutGlError()
        } finally {
            if (irisDepth == null && sourceFramebuffer > 0) {
                glBindFramebuffer(GL_READ_FRAMEBUFFER, sourceFramebuffer)
                glReadBuffer(previousReadBuffer)
            }
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, capture.framebufferId)
            glDrawBuffer(previousDrawBuffer)
            glBindFramebuffer(GL_READ_FRAMEBUFFER, previousReadFramebuffer)
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer)
            if (!copied && capture !== existing) {
                capture.release()
            }
        }
        if (copied) {
            if (capture !== existing) {
                existing?.release()
            }
            accept(capture)
        }
        return copied
    }

    private fun clearPendingGlErrors() {
        while (glGetError() != GL_NO_ERROR) {
            // 清空操作前遗留的错误，避免把旧错误归因到本次 blit。
        }
    }

    private fun operationCompletedWithoutGlError(): Boolean {
        var error = glGetError()
        if (error == GL_NO_ERROR) return true
        while (error != GL_NO_ERROR) {
            error = glGetError()
        }
        return false
    }

    private fun resolveDepthTextureFormat(textureId: Int): DepthTextureFormat? {
        if (textureId <= 0) return null
        val previousActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE)
        glActiveTexture(GL_TEXTURE0)
        val previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D)
        return try {
            glBindTexture(GL_TEXTURE_2D, textureId)
            val width = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH)
            if (width <= 0) {
                null
            } else {
                val internalFormat = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_INTERNAL_FORMAT)
                val depthBits = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_DEPTH_SIZE)
                val stencilBits = if (internalFormat == GL_DEPTH_STENCIL ||
                    internalFormat == GL_DEPTH24_STENCIL8 ||
                    internalFormat == GL_DEPTH32F_STENCIL8
                ) {
                    glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_STENCIL_SIZE)
                } else {
                    0
                }
                depthTextureFormat(internalFormat, depthBits, stencilBits)
            }
        } finally {
            glBindTexture(GL_TEXTURE_2D, previousTexture)
            glActiveTexture(previousActiveTexture)
        }
    }

    private fun resolveDepthFramebufferFormat(framebufferId: Int): DepthTextureFormat? {
        if (framebufferId <= 0) return null
        val previousReadFramebuffer = glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
        val previousRenderbuffer = glGetInteger(GL_RENDERBUFFER_BINDING)
        return try {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, framebufferId)
            val depthType = glGetFramebufferAttachmentParameteri(
                GL_READ_FRAMEBUFFER,
                GL_DEPTH_ATTACHMENT,
                GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
            )
            val depthName = if (depthType == GL_NONE) {
                0
            } else {
                glGetFramebufferAttachmentParameteri(
                    GL_READ_FRAMEBUFFER,
                    GL_DEPTH_ATTACHMENT,
                    GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
                )
            }
            when (depthType) {
                GL_TEXTURE -> resolveDepthTextureFormat(depthName)
                GL_RENDERBUFFER -> {
                    if (depthName <= 0) {
                        null
                    } else {
                        glBindRenderbuffer(GL_RENDERBUFFER, depthName)
                        depthTextureFormat(
                            glGetRenderbufferParameteri(GL_RENDERBUFFER, GL_RENDERBUFFER_INTERNAL_FORMAT),
                            glGetRenderbufferParameteri(GL_RENDERBUFFER, GL_RENDERBUFFER_DEPTH_SIZE),
                            glGetRenderbufferParameteri(GL_RENDERBUFFER, GL_RENDERBUFFER_STENCIL_SIZE)
                        )
                    }
                }
                else -> null
            }
        } finally {
            glBindRenderbuffer(GL_RENDERBUFFER, previousRenderbuffer)
            glBindFramebuffer(GL_READ_FRAMEBUFFER, previousReadFramebuffer)
        }
    }

    private fun resolveTextureSize(textureId: Int): Pair<Int, Int>? {
        if (textureId <= 0) return null
        val previousActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE)
        glActiveTexture(GL_TEXTURE0)
        val previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D)
        return try {
            glBindTexture(GL_TEXTURE_2D, textureId)
            val width = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH)
            val height = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT)
            (width to height).takeIf { width > 0 && height > 0 }
        } finally {
            glBindTexture(GL_TEXTURE_2D, previousTexture)
            glActiveTexture(previousActiveTexture)
        }
    }

    private fun resolveDepthFramebufferSize(framebufferId: Int): Pair<Int, Int>? {
        if (framebufferId <= 0) return null
        val previousReadFramebuffer = glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
        val previousRenderbuffer = glGetInteger(GL_RENDERBUFFER_BINDING)
        return try {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, framebufferId)
            val depthType = glGetFramebufferAttachmentParameteri(
                GL_READ_FRAMEBUFFER,
                GL_DEPTH_ATTACHMENT,
                GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
            )
            val size = when (depthType) {
                GL_TEXTURE -> {
                    val texture = glGetFramebufferAttachmentParameteri(
                        GL_READ_FRAMEBUFFER,
                        GL_DEPTH_ATTACHMENT,
                        GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
                    )
                    resolveTextureSize(texture)
                }
                GL_RENDERBUFFER -> {
                    val renderbuffer = glGetFramebufferAttachmentParameteri(
                        GL_READ_FRAMEBUFFER,
                        GL_DEPTH_ATTACHMENT,
                        GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
                    )
                    if (renderbuffer <= 0) {
                        null
                    } else {
                        glBindRenderbuffer(GL_RENDERBUFFER, renderbuffer)
                        glGetRenderbufferParameteri(GL_RENDERBUFFER, GL_RENDERBUFFER_WIDTH) to
                            glGetRenderbufferParameteri(GL_RENDERBUFFER, GL_RENDERBUFFER_HEIGHT)
                    }
                }
                else -> null
            }
            size?.takeIf { it.first > 0 && it.second > 0 }
        } finally {
            glBindRenderbuffer(GL_RENDERBUFFER, previousRenderbuffer)
            glBindFramebuffer(GL_READ_FRAMEBUFFER, previousReadFramebuffer)
        }
    }

    private fun depthTextureFormat(
        internalFormat: Int,
        reportedDepthBits: Int,
        reportedStencilBits: Int
    ): DepthTextureFormat? {
        val depthBits = when {
            reportedDepthBits > 0 -> reportedDepthBits
            internalFormat == GL_DEPTH_COMPONENT16 -> 16
            internalFormat == GL_DEPTH_COMPONENT24 -> 24
            internalFormat == GL_DEPTH_COMPONENT32 || internalFormat == GL_DEPTH_COMPONENT32F -> 32
            internalFormat == GL_DEPTH24_STENCIL8 -> 24
            internalFormat == GL_DEPTH32F_STENCIL8 -> 32
            else -> 0
        }
        val stencilBits = when {
            reportedStencilBits > 0 -> reportedStencilBits
            internalFormat == GL_DEPTH24_STENCIL8 -> 8
            internalFormat == GL_DEPTH32F_STENCIL8 -> 8
            else -> 0
        }
        return when {
            internalFormat == GL_DEPTH_COMPONENT16 ||
                (internalFormat == GL_DEPTH_COMPONENT && depthBits in 1..16) -> DepthTextureFormat(
                GL_DEPTH_COMPONENT16,
                GL_DEPTH_COMPONENT,
                GL_UNSIGNED_SHORT,
                GL_DEPTH_ATTACHMENT,
                DepthBlitClass(16, 0)
            )
            internalFormat == GL_DEPTH_COMPONENT24 ||
                (internalFormat == GL_DEPTH_COMPONENT && depthBits in 17..24) -> DepthTextureFormat(
                GL_DEPTH_COMPONENT24,
                GL_DEPTH_COMPONENT,
                GL_UNSIGNED_INT,
                GL_DEPTH_ATTACHMENT,
                DepthBlitClass(24, 0)
            )
            internalFormat == GL_DEPTH_COMPONENT32 -> DepthTextureFormat(
                GL_DEPTH_COMPONENT32,
                GL_DEPTH_COMPONENT,
                GL_UNSIGNED_INT,
                GL_DEPTH_ATTACHMENT,
                DepthBlitClass(32, 0)
            )
            internalFormat == GL_DEPTH_COMPONENT32F -> DepthTextureFormat(
                GL_DEPTH_COMPONENT32F,
                GL_DEPTH_COMPONENT,
                GL_FLOAT,
                GL_DEPTH_ATTACHMENT,
                DepthBlitClass(32, 0, floatingPoint = true)
            )
            internalFormat == GL_DEPTH_STENCIL && depthBits in 1..24 -> DepthTextureFormat(
                GL_DEPTH24_STENCIL8,
                GL_DEPTH_STENCIL,
                GL_UNSIGNED_INT_24_8,
                GL_DEPTH_STENCIL_ATTACHMENT,
                DepthBlitClass(24, maxOf(8, stencilBits))
            )
            internalFormat == GL_DEPTH24_STENCIL8 -> DepthTextureFormat(
                GL_DEPTH24_STENCIL8,
                GL_DEPTH_STENCIL,
                GL_UNSIGNED_INT_24_8,
                GL_DEPTH_STENCIL_ATTACHMENT,
                DepthBlitClass(24, 8)
            )
            internalFormat == GL_DEPTH32F_STENCIL8 ||
                (internalFormat == GL_DEPTH_STENCIL && depthBits >= 25) -> DepthTextureFormat(
                GL_DEPTH32F_STENCIL8,
                GL_DEPTH_STENCIL,
                GL_FLOAT_32_UNSIGNED_INT_24_8_REV,
                GL_DEPTH_STENCIL_ATTACHMENT,
                DepthBlitClass(32, maxOf(8, stencilBits), floatingPoint = true)
            )
            internalFormat == GL_DEPTH_COMPONENT && depthBits >= 25 -> DepthTextureFormat(
                GL_DEPTH_COMPONENT32,
                GL_DEPTH_COMPONENT,
                GL_UNSIGNED_INT,
                GL_DEPTH_ATTACHMENT,
                DepthBlitClass(32, 0)
            )
            // 未定型 depth 格式不能证明实际位深或浮点编码；拒绝它，避免执行未经证明兼容的 depth-only blit。
            internalFormat == GL_DEPTH_STENCIL || internalFormat == GL_DEPTH_COMPONENT -> null
            else -> null
        }
    }

    private fun createDepthCapture(
        width: Int,
        height: Int,
        format: DepthTextureFormat
    ): DepthCapture {
        val previousReadFramebuffer = glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
        val previousDrawFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        val previousActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE)
        glActiveTexture(GL_TEXTURE0)
        val previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D)
        val textureId = glGenTextures()
        val framebufferId = glGenFramebuffers()
        try {
            glBindTexture(GL_TEXTURE_2D, textureId)
            glTexImage2D(
                GL_TEXTURE_2D,
                0,
                format.internalFormat,
                width,
                height,
                0,
                format.pixelFormat,
                format.dataType,
                null as ByteBuffer?
            )
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            glBindFramebuffer(GL_FRAMEBUFFER, framebufferId)
            glFramebufferTexture2D(
                GL_FRAMEBUFFER,
                format.attachment,
                GL_TEXTURE_2D,
                textureId,
                0
            )
            glDrawBuffer(GL_NONE)
            glReadBuffer(GL_NONE)
            check(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) {
                "Terrain depth capture framebuffer is incomplete"
            }
            return DepthCapture(framebufferId, textureId, width, height, format)
        } catch (error: RuntimeException) {
            glDeleteFramebuffers(framebufferId)
            glDeleteTextures(textureId)
            throw error
        } finally {
            glBindTexture(GL_TEXTURE_2D, previousTexture)
            glActiveTexture(previousActiveTexture)
            glBindFramebuffer(GL_READ_FRAMEBUFFER, previousReadFramebuffer)
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer)
        }
    }

    /**
     * 在最终世界 framebuffer 上临时挂载 HDR attachment，并让一次 draw 同时写入世界和 mask。
     *
     * 任何 attachment 占用、draw-buffer 布局或 framebuffer 完整性冲突都会在执行回调前返回 `false`。
     */
    override fun captureInlineAttachments(
        context: RenderFrameContext,
        owner: String,
        target: ResourceLocation,
        attachments: List<PostEffectAttachmentSpec>,
        render: () -> Unit
    ): Boolean {
        if (attachments.size < 2) return false
        val formats = attachments.map(PostEffectAttachmentSpec::format).distinct()
        val mipCounts = attachments.map(PostEffectAttachmentSpec::mipLevels).distinct()
        require(formats.size == 1 && mipCounts.size == 1) {
            "Framebuffer '$target' must use one color format and mip count for all attachments"
        }
        val worldFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        if (worldFramebuffer <= 0 || context.boundFramebufferId?.let { it != worldFramebuffer } == true) return false
        val maxDrawBuffers = glGetInteger(GL_MAX_DRAW_BUFFERS)
        if (attachments.size > maxDrawBuffers) return false
        val previousDrawBuffers = IntArray(maxDrawBuffers) { index ->
            glGetInteger(GL_DRAW_BUFFER0 + index)
        }
        if (previousDrawBuffers.firstOrNull() != GL_COLOR_ATTACHMENT0 ||
            previousDrawBuffers.drop(1).any { buffer -> buffer != GL_NONE }
        ) {
            return false
        }
        val inlineAttachments = 1 until attachments.size
        if (inlineAttachments.any { attachment ->
                glGetFramebufferAttachmentParameteri(
                    GL_DRAW_FRAMEBUFFER,
                    GL_COLOR_ATTACHMENT0 + attachment,
                    GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
                ) != GL_NONE
            }
        ) {
            return false
        }
        val previousDepthType = glGetFramebufferAttachmentParameteri(
            GL_DRAW_FRAMEBUFFER,
            GL_DEPTH_ATTACHMENT,
            GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
        )
        val previousDepthName = if (previousDepthType == GL_NONE) {
            0
        } else {
            glGetFramebufferAttachmentParameteri(
                GL_DRAW_FRAMEBUFFER,
                GL_DEPTH_ATTACHMENT,
                GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
            )
        }
        val opaqueDepth = IrisCompat.currentTerrainDepthTexture()
        val replaceDepth = opaqueDepth != null &&
            (previousDepthType != GL_TEXTURE || previousDepthName != opaqueDepth.textureId)
        if (!replaceDepth && previousDepthType == GL_NONE) return false
        if (replaceDepth && previousDepthType != GL_NONE &&
            previousDepthType != GL_TEXTURE && previousDepthType != GL_RENDERBUFFER
        ) {
            return false
        }

        val managed = targetFor(
            context = context,
            key = "$owner:pipeline:$target",
            colorAttachmentCount = attachments.size,
            format = formats.single(),
            mipLevels = mipCounts.single()
        )
        managed.buffer.writeFrameBufferWith(false) {}
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, worldFramebuffer)
        inlineAttachments.forEach { attachment ->
            glFramebufferTexture2D(
                GL_DRAW_FRAMEBUFFER,
                GL_COLOR_ATTACHMENT0 + attachment,
                GL_TEXTURE_2D,
                managed.buffer.colorAttachments[attachment],
                0
            )
        }
        if (replaceDepth) {
            glFramebufferTexture2D(
                GL_DRAW_FRAMEBUFFER,
                GL_DEPTH_ATTACHMENT,
                GL_TEXTURE_2D,
                requireNotNull(opaqueDepth).textureId,
                0
            )
        }
        glDrawBuffers(IntArray(attachments.size) { attachment -> GL_COLOR_ATTACHMENT0 + attachment })
        fun restoreAttachments() {
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, worldFramebuffer)
            inlineAttachments.forEach { attachment ->
                glFramebufferTexture2D(
                    GL_DRAW_FRAMEBUFFER,
                    GL_COLOR_ATTACHMENT0 + attachment,
                    GL_TEXTURE_2D,
                    0,
                    0
                )
            }
            if (replaceDepth) {
                when (previousDepthType) {
                    GL_TEXTURE -> glFramebufferTexture2D(
                        GL_DRAW_FRAMEBUFFER,
                        GL_DEPTH_ATTACHMENT,
                        GL_TEXTURE_2D,
                        previousDepthName,
                        0
                    )
                    GL_RENDERBUFFER -> glFramebufferRenderbuffer(
                        GL_DRAW_FRAMEBUFFER,
                        GL_DEPTH_ATTACHMENT,
                        GL_RENDERBUFFER,
                        previousDepthName
                    )
                    else -> glFramebufferTexture2D(
                        GL_DRAW_FRAMEBUFFER,
                        GL_DEPTH_ATTACHMENT,
                        GL_TEXTURE_2D,
                        0,
                        0
                    )
                }
            }
            glDrawBuffers(previousDrawBuffers)
        }
        if (glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            restoreAttachments()
            return false
        }

        try {
            render()
        } finally {
            restoreAttachments()
        }
        // render() 直接改写 world color；后续 fullscreen pass 必须重新复制，但继续沿用当前 chain FBO。
        chainedSceneFramebufferId = worldFramebuffer
        invalidatePreparedSceneCopy()
        if (mipCounts.single() > 1) {
            managed.buffer.generateMipmaps()
        }
        inlineAttachments.forEach { attachment ->
            namedTargetTextures[NamedAttachment(target, attachment)] = managed.buffer.colorAttachments[attachment]
        }
        instanceLastSeenFrame[owner] = frameCounter
        return true
    }

    /**
     * 执行 `OpenGlPostEffectExecutionBackend` 定义的 `captureAttachment` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`captureAttachment(context = context, owner = owner, target = target, attachment = attachment, render = render)`。
     *
     * @param context 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param owner 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param target 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param attachment 数量或从零开始的索引值，具体上限由当前资源配置决定
     *
     * @param render 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    override fun captureAttachment(
        context: RenderFrameContext,
        owner: String,
        target: ResourceLocation,
        attachment: Int,
        render: () -> Unit
    ): Boolean {
        return captureAttachments(context, owner, target, attachment + 1, render)
    }

    /**
     * 执行 `OpenGlPostEffectExecutionBackend` 定义的 `captureAttachments` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`captureAttachments(context = context, owner = owner, target = target, attachmentCount = attachmentCount, render = render)`。
     *
     * @param context 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param owner 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param target 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param attachmentCount 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param render 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    override fun captureAttachments(
        context: RenderFrameContext,
        owner: String,
        target: ResourceLocation,
        attachmentCount: Int,
        render: () -> Unit
    ): Boolean {
        return captureAttachments(
            context,
            owner,
            target,
            List(attachmentCount) { PostEffectAttachmentSpec() },
            render
        )
    }

    override fun captureAttachments(
        context: RenderFrameContext,
        owner: String,
        target: ResourceLocation,
        attachments: List<PostEffectAttachmentSpec>,
        render: () -> Unit
    ): Boolean {
        require(attachments.isNotEmpty()) { "A captured framebuffer must have at least one color attachment" }
        val formats = attachments.map(PostEffectAttachmentSpec::format).distinct()
        val mipCounts = attachments.map(PostEffectAttachmentSpec::mipLevels).distinct()
        require(formats.size == 1 && mipCounts.size == 1) {
            "Framebuffer '$target' must use one color format and mip count for all attachments"
        }
        val managed = targetFor(
            context = context,
            key = "$owner:pipeline:$target",
            colorAttachmentCount = attachments.size,
            format = formats.single(),
            mipLevels = mipCounts.single(),
            depthFormat = resolveSceneDepthFormat(context)
        )
        val sceneDepthAvailable = hasSceneDepthSource(context)
        var depthReady = !sceneDepthAvailable
        instanceLastSeenFrame[owner] = frameCounter
        managed.buffer.writeFrameBufferWith {
            depthReady = copySceneDepth(context, managed)
            if (depthReady || !sceneDepthAvailable) {
                render()
            }
        }
        if (!depthReady && sceneDepthAvailable) return false
        managed.buffer.colorAttachments.forEachIndexed { attachment, texture ->
            namedTargetTextures[NamedAttachment(target, attachment)] = texture
        }
        return managed.buffer.colorAttachments.size == attachments.size &&
            managed.buffer.colorAttachments.all { it > 0 }
    }

    /**
     * 执行 `OpenGlPostEffectExecutionBackend` 定义的 `hasAttachment` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`hasAttachment(target = target, attachment = attachment)`。
     *
     * @param target 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param attachment 数量或从零开始的索引值，具体上限由当前资源配置决定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    override fun hasAttachment(target: ResourceLocation, attachment: Int): Boolean {
        return namedTargetTextures[NamedAttachment(target, attachment)]?.let { it > 0 } == true
    }

    /**
     * 执行 `OpenGlPostEffectExecutionBackend` 定义的 `execute` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`execute(step = step)`。
     *
     * @param step 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    override fun execute(step: PostEffectExecutionStep) {
        val state = instanceStates.getOrPut(step.instance.instanceId) { InstanceFrameState() }
        instanceLastSeenFrame[step.instance.instanceId] = frameCounter
        val frameKey = frameKey(step.context)
        if (state.frameKey != frameKey || step.passIndex == 0) {
            state.frameKey = frameKey
            state.lastOutputTextures.clear()
            state.lastPassOutputTextures.clear()
        }

        if (step.output.output == PostEffectOutput.FINAL_SCREEN) {
            drawToFinal(step, state)
            return
        }

        val target = targetFor(step)
        target.buffer.writeFrameBufferWith(step.output.generateMipmaps) {
            drawStep(step, state)
        }
        target.buffer.colorAttachments.forEachIndexed { attachment, texture ->
            state.lastPassOutputTextures[PassAttachment(step.pass.name, attachment)] = texture
            namedTargetTextures[NamedAttachment(step.output.targetId, attachment)] = texture
        }
        val colorTexture = target.buffer.colorAttachments.firstOrNull() ?: 0
        state.lastOutputTextures[step.output.output] = colorTexture
    }

    /**
     * 释放 `OpenGlPostEffectExecutionBackend` 在 `release` 中管理的资源；再次使用前必须重新初始化。
     *
     * 示例：`release()`。
     */
    override fun release() {
        releaseTerrainColorCapture()
        targets.values.forEach { it.buffer.release() }
        targets.clear()
        namedTargetTextures.clear()
        programs.values.forEach { it.release() }
        programs.clear()
        customTextures.values.forEach { it.release() }
        customTextures.clear()
        screenBuffer?.release()
        screenBuffer = null
        if (irisDepthReadFramebuffer > 0) {
            glDeleteFramebuffers(irisDepthReadFramebuffer)
            irisDepthReadFramebuffer = 0
        }
        instanceStates.clear()
        instanceLastSeenFrame.clear()
        frameCounter = 0
        preparedSceneFrame = null
        preparedSceneCopySpec = null
        chainedSceneFramebufferId = null
        warnedSceneCopyFailure = false
        warnedTerrainSceneCopyFailure = false
        warnedTextureContractFailures.clear()
    }

    /**
     * 把当前 terrain 输出复制到后处理后端管理的 scene-copy target。
     *
     * 返回值只允许在复制完成后作为 sampler 输入使用；源 FBO 始终保持为后续绘制目标，
     * 因而不会形成同一 attachment 的读写反馈。
     *
     * @param sourceFramebufferId 当前 terrain 输出所在的 framebuffer 对象名，必须大于 `0`
     * @param width 源 framebuffer 宽度，最小按 `1` 处理
     * @param height 源 framebuffer 高度，最小按 `1` 处理
     * @param sceneResources 当前帧已有的场景资源，用于补齐复制上下文
     * @return 包含独立颜色和可用深度纹理的场景资源；源目标无效或复制失败时返回 `null`
     */
    internal fun captureTerrainScene(
        sourceFramebufferId: Int,
        width: Int,
        height: Int,
        sceneResources: RenderSceneResources
    ): RenderSceneResources? {
        RenderSystem.assertOnRenderThread()
        if (sourceFramebufferId <= 0) {
            warnTerrainSceneCopyFailure("invalid source framebuffer=$sourceFramebufferId")
            return null
        }

        val context = RenderFrameContext(
            tickDelta = 0F,
            viewMatrix = Matrix4f(),
            projMatrix = Matrix4f(),
            backend = ClientRenderPipelineManager.activeBackend,
            sceneResources = sceneResources,
            sceneColorFramebufferId = sourceFramebufferId,
            sceneDepthFramebufferId = sourceFramebufferId,
            boundFramebufferId = sourceFramebufferId,
            externalFramebuffer = true,
            targetWidth = width.coerceAtLeast(1),
            targetHeight = height.coerceAtLeast(1)
        )
        val target = try {
            ensureManagedTarget(
                current = sceneCopy,
                key = "scene_copy",
                context = context,
                depthFormat = resolveSceneDepthFormat(context)
            ).also { sceneCopy = it }
        } catch (error: RuntimeException) {
            warnTerrainSceneCopyFailure(error.message ?: error.javaClass.simpleName)
            return null
        }
        if (sourceFramebufferId == target.buffer.fbo()) {
            warnTerrainSceneCopyFailure("source and destination framebuffer are identical")
            return null
        }

        val previousRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
        var sourceColorTexture = 0
        val sourceComplete = try {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, sourceFramebufferId)
            val sourceColorType = glGetFramebufferAttachmentParameteri(
                GL_READ_FRAMEBUFFER,
                GL_COLOR_ATTACHMENT0,
                GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
            )
            if (sourceColorType == GL_TEXTURE) {
                sourceColorTexture = glGetFramebufferAttachmentParameteri(
                    GL_READ_FRAMEBUFFER,
                    GL_COLOR_ATTACHMENT0,
                    GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
                )
            }
            glCheckFramebufferStatus(GL_READ_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE
        } finally {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, previousRead)
        }
        if (!sourceComplete) {
            warnTerrainSceneCopyFailure("source framebuffer is incomplete")
            return null
        }
        if (sourceColorTexture > 0 && sourceColorTexture in target.buffer.colorAttachments) {
            warnTerrainSceneCopyFailure("source and destination use the same color texture")
            return null
        }

        return try {
            copyColor(
                sourceFramebufferId,
                width.coerceAtLeast(1),
                height.coerceAtLeast(1),
                target,
                context
            )
            val depthCopied = copySceneDepth(context, target)
            val colorTexture = target.buffer.colorAttachments.firstOrNull()?.takeIf { it > 0 }
                ?: return null
            val depthTexture = target.buffer.getCurrentDepthAttachment().takeIf { depthCopied && it > 0 }
            RenderSceneResources.of(
                RenderSceneResource(
                    id = RenderSceneTargets.SCENE_COLOR,
                    label = "terrain-scene-copy:color",
                    colorTextureIds = listOf(colorTexture),
                    depthTextureId = depthTexture
                ),
                RenderSceneResource(
                    id = RenderSceneTargets.SCENE_DEPTH,
                    label = "terrain-scene-copy:depth",
                    depthTextureId = depthTexture
                )
            )
        } catch (error: RuntimeException) {
            warnTerrainSceneCopyFailure(error.message ?: error.javaClass.simpleName)
            null
        }
    }

    /** 释放地形场景副本，并重置相关帧标记和一次性告警状态。 */
    internal fun releaseTerrainColorCapture() {
        sceneCopy?.buffer?.release()
        sceneCopy = null
        terrainOpaqueDepthCapture?.release()
        terrainOpaqueDepthCapture = null
        terrainTranslucentBeforeDepthCapture?.release()
        terrainTranslucentBeforeDepthCapture = null
        terrainTranslucentAfterDepthCapture?.release()
        terrainTranslucentAfterDepthCapture = null
        cParticleCoverageDepthCapture?.release()
        cParticleCoverageDepthCapture = null
        cParticleCoverageColorCapture?.release()
        cParticleCoverageColorCapture = null
        cParticleCoverageValid = false
        terrainDepthIrisSourceResolved = false
        terrainDepthIrisSource = null
        terrainDepthSourceCache = null
        terrainOpaqueDepthValid = false
        terrainTranslucentBeforeDepthValid = false
        terrainTranslucentAfterDepthValid = false
        preparedSceneFrame = null
        preparedSceneCopySpec = null
        warnedTerrainSceneCopyFailure = false
    }

    /**
     * 查询后处理后端当前保存的命名颜色 attachment。
     *
     * @param target 命名渲染目标 ID
     * @param attachment 颜色 attachment 索引
     * @return 有效 OpenGL 纹理对象名；尚未捕获或对象名无效时返回 `null`
     */
    internal fun resolveNamedColorTexture(target: ResourceLocation, attachment: Int): Int? {
        return namedTargetTextures[NamedAttachment(target, attachment)]?.takeIf { it > 0 }
    }

    private fun evictStaleTargets() {
        if (instanceLastSeenFrame.isEmpty()) {
            return
        }
        val staleInstances = mutableSetOf<String>()
        instanceLastSeenFrame.entries.removeAll { (instanceId, lastSeen) ->
            val drop = frameCounter - lastSeen > 60L
            if (drop) {
                staleInstances += instanceId
            }
            drop
        }
        if (staleInstances.isEmpty()) {
            return
        }
        val toRelease = mutableListOf<ManagedTarget>()
        targets.entries.removeAll { entry ->
            val owner = entry.key.substringBefore(':')
            val drop = owner in staleInstances
            if (drop) {
                toRelease += entry.value
            }
            drop
        }
        toRelease.forEach { it.buffer.release() }
    }

    private fun ensureSceneCopy(
        context: RenderFrameContext,
        spec: PostEffectAttachmentSpec
    ): ManagedTarget? {
        val frameKey = frameKey(context)
        if (preparedSceneFrame == frameKey && preparedSceneCopySpec == spec) {
            return sceneCopy
        }

        val source = context.sceneResources[RenderSceneTargets.SCENE_COLOR]?.target
            ?: context.finalCompositeTarget
            ?: Minecraft.getInstance().mainRenderTarget
        val sourceFramebufferId = chainedSceneFramebufferId ?: context.sceneColorFramebufferId ?: source.frameBufferId
        if (sourceFramebufferId <= 0) {
            warnSceneCopyFailure("invalid source framebuffer=$sourceFramebufferId")
            return null
        }

        val target = ensureManagedTarget(
            sceneCopy,
            "scene_copy",
            context,
            format = spec.format,
            mipLevels = spec.mipLevels,
            depthFormat = resolveSceneDepthFormat(context)
        ).also { sceneCopy = it }
        return try {
            val sourceWidth = max(1, context.targetWidth ?: source.width)
            val sourceHeight = max(1, context.targetHeight ?: source.height)
            copyColor(sourceFramebufferId, sourceWidth, sourceHeight, target, context)
            if (spec.mipLevels > 1) {
                target.buffer.generateMipmaps()
            }
            preparedSceneFrame = frameKey
            preparedSceneCopySpec = spec
            target
        } catch (error: RuntimeException) {
            warnSceneCopyFailure(error.message ?: error.javaClass.simpleName)
            null
        }
    }

    private fun drawToFinal(step: PostEffectExecutionStep, state: InstanceFrameState) {
        val target = step.context.finalCompositeTarget ?: Minecraft.getInstance().mainRenderTarget
        val framebuffer = step.context.finalCompositeFramebufferId?.takeIf { it > 0 } ?: target.frameBufferId
        val width = max(1, step.context.targetWidth ?: target.width)
        val height = max(1, step.context.targetHeight ?: target.height)
        val previousReadFramebuffer = glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
        val previousDrawFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        val previousViewport = IntArray(4)
        glGetIntegerv(GL_VIEWPORT, previousViewport)
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer)
        glViewport(0, 0, width, height)
        try {
            drawStep(step, state)
        } finally {
            glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3])
            glBindFramebuffer(GL_READ_FRAMEBUFFER, previousReadFramebuffer)
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer)
        }
        chainedSceneFramebufferId = framebuffer
        preparedSceneFrame = null
        preparedSceneCopySpec = null
    }

    private fun drawStep(step: PostEffectExecutionStep, state: InstanceFrameState) {
        val program = programFor(step.pass.vertex, step.pass.fragment)
        val buffer = screenBuffer()
        withFlatPostState {
            program.useOnContext {
                uploadBuiltInUniforms(this, step)
                uploadUniforms(this, step.uniforms)
                bindInputs(step, state, this) {
                    buffer.draw()
                }
            }
        }
        if (step.output.output == PostEffectOutput.FINAL_SCREEN) {
            val colorTexture = step.context.finalCompositeTarget?.colorTextureId ?: 0
            state.lastOutputTextures[step.output.output] = colorTexture
            state.lastPassOutputTextures[PassAttachment(step.pass.name, 0)] = colorTexture
            namedTargetTextures[NamedAttachment(step.output.targetId, 0)] = colorTexture
        }
    }

    private fun bindInputs(
        step: PostEffectExecutionStep,
        state: InstanceFrameState,
        program: CooShaderProgram,
        draw: () -> Unit
    ) {
        val missingRequiredInputs = mutableListOf<String>()
        val explicitSlots = step.inputs.mapNotNull { it.textureSlot }.toSet()
        val usedSlots = linkedSetOf<Int>()
        var nextAutoSlot = 0
        val availableInputs = step.inputs.mapNotNull { input ->
            if (!input.available) {
                if (!input.optional) {
                    missingRequiredInputs += input.samplerName
                }
                return@mapNotNull null
            }
            val texture = resolveInputTexture(step, state, input)
            if (texture == null) {
                if (!input.optional) {
                    missingRequiredInputs += input.samplerName
                }
                return@mapNotNull null
            }
            val slot = input.textureSlot ?: run {
                while (nextAutoSlot in explicitSlots || nextAutoSlot in usedSlots) {
                    nextAutoSlot++
                }
                nextAutoSlot
            }
            usedSlots += slot
            BoundInput(input.samplerName, texture, slot)
        }
        if (missingRequiredInputs.isNotEmpty()) {
            CooParticlesConstants.logger.debug(
                "Skipping post effect type={} id={} pass={} because runtime input texture(s) are missing: {}",
                step.instance.type.id,
                step.instance.instanceId,
                step.pass.name,
                missingRequiredInputs.joinToString()
            )
            return
        }
        val previousActive = glGetInteger(GL_ACTIVE_TEXTURE)
        val previousBindings = linkedMapOf<Int, Int>()
        try {
            availableInputs.forEach { input ->
                glActiveTexture(GL_TEXTURE0 + input.textureSlot)
                previousBindings[input.textureSlot] = glGetInteger(GL_TEXTURE_BINDING_2D)
                glBindTexture(GL_TEXTURE_2D, input.textureId)
                program.setInt(input.samplerName, input.textureSlot)
                val mipLevelsUniform = "${input.samplerName}MipLevels"
                if (glGetUniformLocation(program.program, mipLevelsUniform) >= 0) {
                    program.setInt(mipLevelsUniform, textureMipLevelCount())
                }
            }
            draw()
        } finally {
            previousBindings.entries.reversed().forEach { (textureSlot, previousBinding) ->
                glActiveTexture(GL_TEXTURE0 + textureSlot)
                glBindTexture(GL_TEXTURE_2D, previousBinding)
            }
            glActiveTexture(previousActive)
        }
    }

    private fun resolveInputTexture(
        step: PostEffectExecutionStep,
        state: InstanceFrameState,
        input: PostEffectResolvedInput
    ): Int? {
        val texture = when (input.source) {
            PostEffectInputSource.SCENE_COLOR -> ensureSceneCopy(
                step.context,
                PostEffectAttachmentSpec(
                    input.expectedFormat ?: CooTextureFormat.RGBA8,
                    input.minimumMipLevels
                )
            )
                ?.buffer
                ?.colorAttachments
                ?.firstOrNull()
            PostEffectInputSource.SCENE_DEPTH -> resolveSceneDepthTexture(input)
            PostEffectInputSource.SCENE_DEPTH_NO_HAND -> input.textureId
            PostEffectInputSource.TERRAIN_DEPTH -> resolveTerrainDepthTexture(step, input)
            PostEffectInputSource.TERRAIN_OPAQUE_DEPTH,
            PostEffectInputSource.TERRAIN_TRANSLUCENT_DEPTH_BEFORE,
            PostEffectInputSource.TERRAIN_TRANSLUCENT_DEPTH_AFTER -> input.textureId
            PostEffectInputSource.MASK -> state.lastOutputTextures[PostEffectOutput.MASK]
                ?: ensureBindingMask(step, state)
            PostEffectInputSource.BRIGHT_COLOR -> state.lastOutputTextures[PostEffectOutput.BLOOM]
                ?: input.textureId
            PostEffectInputSource.CUSTOM_TEXTURE -> customTexture(input, step.instance)
            PostEffectInputSource.PASS_OUTPUT -> input.producedByPassName?.let { passName ->
                state.lastPassOutputTextures[PassAttachment(passName, input.producedByPassAttachment)]
            }
            PostEffectInputSource.SCENE_RESOURCE -> input.textureId
                ?: input.sourceResourceId?.let { resource ->
                    namedTargetTextures[NamedAttachment(resource, input.sourceResourceAttachment)]
                }
        }?.takeIf { it > 0 } ?: return null
        return texture.takeIf { validateTextureContract(step, input, texture) }
    }

    /** 解析 terrain opaque depth；不可用时返回 null，不回退到颜色或方块图集。 */
    private fun resolveTerrainDepthTexture(
        step: PostEffectExecutionStep,
        input: PostEffectResolvedInput
    ): Int? {
        if (step.context.externalFramebuffer) {
            return IrisCompat.currentTerrainDepthTexture()?.textureId?.takeIf { textureId -> textureId > 0 }
        }
        return input.textureId?.takeIf { textureId -> textureId > 0 }
            ?: step.context.sceneResources[RenderSceneTargets.TERRAIN_DEPTH]?.depthTextureId
    }

    /** Iris shader pack 激活时始终优先使用其当前场景深度，避免主目标绑定状态误导资源选择。 */
    private fun resolveSceneDepthTexture(input: PostEffectResolvedInput): Int? {
        IrisCompat.currentSceneDepthTexture()?.textureId?.takeIf { textureId -> textureId > 0 }?.let {
            return it
        }
        return input.textureId?.takeIf { textureId -> textureId > 0 }
    }

    private fun validateTextureContract(
        step: PostEffectExecutionStep,
        input: PostEffectResolvedInput,
        texture: Int
    ): Boolean {
        if (input.source == PostEffectInputSource.PASS_OUTPUT) return true
        if (input.expectedFormat == null && input.minimumMipLevels <= 1) return true

        val previous = glGetInteger(GL_TEXTURE_BINDING_2D)
        val actualFormat: Int
        val mipLevelsMatch: Boolean
        try {
            glBindTexture(GL_TEXTURE_2D, texture)
            actualFormat = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_INTERNAL_FORMAT)
            val baseLevel = glGetTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL)
            val maxLevel = glGetTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL)
            mipLevelsMatch = baseLevel == 0 &&
                maxLevel >= input.minimumMipLevels - 1 &&
                (0 until input.minimumMipLevels).all { level ->
                    glGetTexLevelParameteri(GL_TEXTURE_2D, level, GL_TEXTURE_WIDTH) > 0
                }
        } finally {
            glBindTexture(GL_TEXTURE_2D, previous)
        }

        val formatMatches = when (input.expectedFormat) {
            null -> true
            CooTextureFormat.RGBA8 -> actualFormat == GL_RGBA8 ||
                actualFormat == GL_SRGB8_ALPHA8 || actualFormat == GL_RGBA
            CooTextureFormat.RGBA16F -> actualFormat == GL_RGBA16F
            CooTextureFormat.RGBA32F -> actualFormat == GL_RGBA32F
        }
        if (formatMatches && mipLevelsMatch) return true

        val key = "${step.instance.type.id}:${step.pass.name}:${input.samplerName}"
        if (warnedTextureContractFailures.add(key)) {
            CooParticlesConstants.logger.warn(
                "Skipping post input {}.{} because texture {} does not satisfy format={} mipLevels={}",
                step.pass.name,
                input.samplerName,
                texture,
                input.expectedFormat ?: "any",
                input.minimumMipLevels
            )
        }
        return false
    }

    /** 返回当前已绑定二维纹理可采样的连续 mip 层数。 */
    private fun textureMipLevelCount(): Int {
        val baseLevel = glGetTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL)
        val maxLevel = glGetTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL)
        if (maxLevel < baseLevel) return 0
        var levels = 0
        for (level in baseLevel..maxLevel) {
            if (glGetTexLevelParameteri(GL_TEXTURE_2D, level, GL_TEXTURE_WIDTH) <= 0) break
            levels++
        }
        return levels
    }

    private fun customTexture(input: PostEffectResolvedInput, instance: PostEffectInstance): Int? {
        val value = instance.params[input.samplerName] ?: return null
        if (value is PostEffectParamValue.IntValue) {
            return value.value
        }
        if (value is PostEffectParamValue.LongValue) {
            return value.value.toInt()
        }
        val id = (value as? PostEffectParamValue.ResourceValue)?.value ?: return null
        val texture = customTextures.getOrPut(id) {
            IdentifierTexture(id).also { it.init() }
        }
        return texture.textureID()
    }

    private fun ensureBindingMask(step: PostEffectExecutionStep, state: InstanceFrameState): Int? {
        val target = targetFor(step.context, "${step.instance.instanceId}:binding_mask")
        val program = programFor(null, bindingMaskFragmentId)
        val buffer = screenBuffer()
        target.buffer.writeFrameBufferWith {
            withFlatPostState {
                program.useOnContext {
                    uploadBuiltInUniforms(this, step)
                    val radius = normalizedScreenRadius(step.instance.floatParam("screenRadius") ?: step.instance.floatParam("radius") ?: 0.22F)
                    val feather = step.instance.floatParam("feather") ?: 0.08F
                    setFloat("radius", radius)
                    setFloat("feather", feather)
                    buffer.draw()
                }
            }
        }
        val texture = target.buffer.colorAttachments.firstOrNull()?.takeIf { it > 0 }
        if (texture != null) {
            state.lastOutputTextures[PostEffectOutput.MASK] = texture
        }
        return texture
    }

    private fun uploadBuiltInUniforms(program: CooShaderProgram, step: PostEffectExecutionStep) {
        val width = max(1, step.context.targetWidth ?: ClientRenderPipelineManager.currentRenderWidth())
        val height = max(1, step.context.targetHeight ?: ClientRenderPipelineManager.currentRenderHeight())
        val center = resolveBindingCenter(step.context, step.instance.binding) ?: Vector2f(0.5F, 0.5F)
        val sourceDepth = resolveBindingDepth(step.context, step.instance.binding) ?: 1F
        val hasDepth = step.inputs.any { input ->
            when {
                input.source == PostEffectInputSource.SCENE_DEPTH -> resolveSceneDepthTexture(input) != null
                input.source == PostEffectInputSource.SCENE_DEPTH_NO_HAND -> input.textureId != null
                input.source == PostEffectInputSource.TERRAIN_DEPTH ||
                    input.source == PostEffectInputSource.TERRAIN_OPAQUE_DEPTH ||
                    input.source == PostEffectInputSource.TERRAIN_TRANSLUCENT_DEPTH_BEFORE ||
                    input.source == PostEffectInputSource.TERRAIN_TRANSLUCENT_DEPTH_AFTER -> input.textureId != null
                    input.sourceResourceChannel == PostEffectResourceChannel.DEPTH -> input.available
                else -> false
            }
        }
        val viewProjection = Matrix4f(step.context.projMatrix).mul(step.context.viewMatrix)
        val inverseViewProjection = Matrix4f(viewProjection).invert()
        val camera = Minecraft.getInstance().gameRenderer.mainCamera.position
        program.setFloat("progress", step.instance.progress)
        program.setFloat2("center", center)
        program.setFloat("sourceDepth", sourceDepth)
        program.setBoolean("hasDepth", hasDepth)
        val hasTerrainDepth = step.inputs.any { input ->
            input.source == PostEffectInputSource.TERRAIN_DEPTH ||
                input.source == PostEffectInputSource.TERRAIN_OPAQUE_DEPTH ||
                input.source == PostEffectInputSource.TERRAIN_TRANSLUCENT_DEPTH_BEFORE ||
                input.source == PostEffectInputSource.TERRAIN_TRANSLUCENT_DEPTH_AFTER
        } && step.inputs.any { input ->
            when (input.source) {
                PostEffectInputSource.TERRAIN_DEPTH -> resolveTerrainDepthTexture(step, input) != null
                PostEffectInputSource.TERRAIN_OPAQUE_DEPTH,
                PostEffectInputSource.TERRAIN_TRANSLUCENT_DEPTH_BEFORE,
                PostEffectInputSource.TERRAIN_TRANSLUCENT_DEPTH_AFTER -> input.textureId != null
                else -> false
            }
        }
        program.setBoolean("hasTerrainDepth", hasTerrainDepth)
        program.setFloat2("screenSize", Vector2f(width.toFloat(), height.toFloat()))
        program.setFloat2("texelSize", Vector2f(1F / width.toFloat(), 1F / height.toFloat()))
        program.setMatrix4("cooViewProjection", viewProjection)
        program.setMatrix4("cooInverseViewProjection", inverseViewProjection)
        program.setFloat3(
            "cooCameraPosition",
            Vector3f(camera.x.toFloat(), camera.y.toFloat(), camera.z.toFloat())
        )
        val effectCenter = (step.uniforms["effectCenter"] as? PostEffectParamValue.Vec3Value)?.let { value ->
            Triple(value.x, value.y, value.z)
        } ?: run {
            val x = numericUniform(step.uniforms, "cooEffectCenterX")
            val y = numericUniform(step.uniforms, "cooEffectCenterY")
            val z = numericUniform(step.uniforms, "cooEffectCenterZ")
            if (x == null || y == null || z == null) null else Triple(x, y, z)
        }
        program.setFloat3("cooEffectCenterRelative", Vector3f())
        effectCenter?.let { (x, y, z) ->
            program.setFloat3(
                "cooEffectCenterRelative",
                Vector3f(
                    (x - camera.x).toFloat(),
                    (y - camera.y).toFloat(),
                    (z - camera.z).toFloat()
                )
            )
        }
    }

    private fun numericUniform(uniforms: Map<String, PostEffectParamValue>, name: String): Double? {
        return when (val value = uniforms[name]) {
            is PostEffectParamValue.IntValue -> value.value.toDouble()
            is PostEffectParamValue.LongValue -> value.value.toDouble()
            is PostEffectParamValue.FloatValue -> value.value.toDouble()
            is PostEffectParamValue.DoubleValue -> value.value
            else -> null
        }
    }

    private fun uploadUniforms(program: CooShaderProgram, uniforms: Map<String, PostEffectParamValue>) {
        uniforms.forEach { (name, value) ->
            when (value) {
                is PostEffectParamValue.BoolValue -> program.setBoolean(name, value.value)
                is PostEffectParamValue.IntValue -> program.setInt(name, value.value)
                is PostEffectParamValue.LongValue -> program.setFloat(name, value.value.toFloat())
                is PostEffectParamValue.FloatValue -> program.setFloat(name, value.value)
                is PostEffectParamValue.DoubleValue -> program.setFloat(name, value.value.toFloat())
                is PostEffectParamValue.StringValue -> Unit
                is PostEffectParamValue.ResourceValue -> Unit
                is PostEffectParamValue.Vec2Value -> program.setFloat2(name, Vector2f(value.x, value.y))
                is PostEffectParamValue.Vec3Value -> program.setFloat3(name, Vector3f(value.x.toFloat(), value.y.toFloat(), value.z.toFloat()))
                is PostEffectParamValue.ColorValue -> program.setFloat4(name, Vector4f(value.red, value.green, value.blue, value.alpha))
                is PostEffectParamValue.UniformValue -> program.setUniform(name, value.value)
            }
        }
    }

    private fun resolveBindingCenter(context: RenderFrameContext, binding: PostEffectBinding): Vector2f? {
        return when (binding) {
            PostEffectBinding.Screen -> Vector2f(0.5F, 0.5F)
            is PostEffectBinding.ScreenPoint -> Vector2f(binding.x, binding.y)
            is PostEffectBinding.WorldPos -> projectWorld(context, binding.x, binding.y, binding.z)
            is PostEffectBinding.Block -> {
                val offset = binding.offset ?: PostEffectParamValue.Vec3Value(0.5, 0.5, 0.5)
                projectWorld(
                    context,
                    binding.pos.x + offset.x,
                    binding.pos.y + offset.y,
                    binding.pos.z + offset.z
                )
            }
            is PostEffectBinding.Entity -> {
                val entity = Minecraft.getInstance().level?.getEntity(binding.entityId) ?: return null
                projectWorld(context, entity.x, entity.y + entity.bbHeight * 0.5, entity.z)
            }
            is PostEffectBinding.Player -> {
                val player = Minecraft.getInstance().level?.getPlayerByUUID(binding.playerId) ?: return null
                projectWorld(context, player.x, player.y + player.bbHeight * 0.5, player.z)
            }
            is PostEffectBinding.Item -> Vector2f(0.5F, 0.5F)
            is PostEffectBinding.Custom -> null
        }
    }

    private fun projectWorld(context: RenderFrameContext, x: Double, y: Double, z: Double): Vector2f? {
        return projectWorldClip(context, x, y, z)?.let { Vector2f(it.x, it.y) }
    }

    private fun resolveBindingDepth(context: RenderFrameContext, binding: PostEffectBinding): Float? {
        return when (binding) {
            PostEffectBinding.Screen -> 1F
            is PostEffectBinding.ScreenPoint -> 1F
            is PostEffectBinding.WorldPos -> projectWorldClip(context, binding.x, binding.y, binding.z)?.z
            is PostEffectBinding.Block -> {
                val offset = binding.offset ?: PostEffectParamValue.Vec3Value(0.5, 0.5, 0.5)
                projectWorldClip(
                    context,
                    binding.pos.x + offset.x,
                    binding.pos.y + offset.y,
                    binding.pos.z + offset.z
                )?.z
            }
            is PostEffectBinding.Entity -> {
                val entity = Minecraft.getInstance().level?.getEntity(binding.entityId) ?: return null
                projectWorldClip(context, entity.x, entity.y + entity.bbHeight * 0.5, entity.z)?.z
            }
            is PostEffectBinding.Player -> {
                val player = Minecraft.getInstance().level?.getPlayerByUUID(binding.playerId) ?: return null
                projectWorldClip(context, player.x, player.y + player.bbHeight * 0.5, player.z)?.z
            }
            is PostEffectBinding.Item -> 1F
            is PostEffectBinding.Custom -> null
        }
    }

    private fun projectWorldClip(context: RenderFrameContext, x: Double, y: Double, z: Double): Vector3f? {
        val camera = Minecraft.getInstance().gameRenderer.mainCamera.position
        val clip = Vector4f(
            (x - camera.x).toFloat(),
            (y - camera.y).toFloat(),
            (z - camera.z).toFloat(),
            1F
        )
        context.viewMatrix.transform(clip)
        context.projMatrix.transform(clip)
        if (clip.w <= 0.0001F) {
            return null
        }
        val invW = 1F / clip.w
        val ndcX = clip.x * invW
        val ndcY = clip.y * invW
        if (ndcX.isNaN() || ndcY.isNaN()) {
            return null
        }
        val ndcZ = clip.z * invW
        return Vector3f(
            (ndcX * 0.5F + 0.5F).coerceIn(0F, 1F),
            (ndcY * 0.5F + 0.5F).coerceIn(0F, 1F),
            (ndcZ * 0.5F + 0.5F).coerceIn(0F, 1F)
        )
    }

    /** 解析当前场景深度的真实格式；无法识别时返回 `null`。 */
    private fun resolveSceneDepthFormat(context: RenderFrameContext): DepthTextureFormat? {
        IrisCompat.currentSceneDepthTexture()?.let { depth ->
            return resolveDepthTextureFormat(depth.textureId)
        }
        val framebuffer = context.sceneDepthFramebufferId
            ?: context.sceneResources[RenderSceneTargets.SCENE_DEPTH]?.target?.frameBufferId
        return framebuffer?.let(::resolveDepthFramebufferFormat)
    }

    /** 判断当前帧是否存在需要保留的场景深度来源。 */
    private fun hasSceneDepthSource(context: RenderFrameContext): Boolean {
        if (IrisCompat.currentSceneDepthTexture()?.textureId?.let { it > 0 } == true) return true
        if (context.sceneDepthTextureId?.let { it > 0 } == true) return true
        if (context.sceneDepthFramebufferId?.let { it > 0 } == true) return true
        return context.sceneResources[RenderSceneTargets.SCENE_DEPTH]?.target?.frameBufferId
            ?.let { it > 0 } == true
    }

    private fun targetFor(step: PostEffectExecutionStep): ManagedTarget {
        val key = if (step.pass.reuseOutputTarget) {
            "${step.instance.instanceId}:${step.output.targetKey}"
        } else {
            "${step.instance.instanceId}:${step.passIndex}:${step.output.targetKey}:${step.output.output.name.lowercase()}"
        }
        return targetFor(
            step.context,
            key,
            scaleDivisor = step.output.scaleDivisor,
            colorAttachmentCount = step.output.colorAttachmentCount,
            format = step.output.format,
            mipLevels = step.output.mipLevels
        )
    }

    private fun targetFor(
        context: RenderFrameContext,
        key: String,
        scaleDivisor: Int = 1,
        colorAttachmentCount: Int = 1,
        format: CooTextureFormat = CooTextureFormat.RGBA8,
        mipLevels: Int = 1,
        depthFormat: DepthTextureFormat? = null
    ): ManagedTarget {
        val current = targets[key]
        val target = ensureManagedTarget(
            current,
            key,
            context,
            scaleDivisor,
            colorAttachmentCount,
            format,
            mipLevels,
            depthFormat
        )
        targets[key] = target
        return target
    }

    private fun ensureManagedTarget(
        current: ManagedTarget?,
        key: String,
        context: RenderFrameContext,
        scaleDivisor: Int = 1,
        colorAttachmentCount: Int = 1,
        format: CooTextureFormat = CooTextureFormat.RGBA8,
        mipLevels: Int = 1,
        depthFormat: DepthTextureFormat? = null
    ): ManagedTarget {
        val divisor = scaleDivisor.coerceAtLeast(1)
        val attachmentCount = colorAttachmentCount.coerceAtLeast(1)
        val width = max(1, (context.targetWidth ?: ClientRenderPipelineManager.currentRenderWidth()) / divisor)
        val height = max(1, (context.targetHeight ?: ClientRenderPipelineManager.currentRenderHeight()) / divisor)
        val requestedMipLevels = mipLevels.coerceAtLeast(1)
        if (
            current == null ||
            current.colorAttachmentCount != attachmentCount ||
            current.format != format ||
            current.mipLevels != requestedMipLevels ||
            current.depthFormat != depthFormat
        ) {
            current?.buffer?.release()
            val buffer = SimpleFrameBuffer(
                attachmentCount,
                { -1 },
                format,
                requestedMipLevels,
                width,
                height
            ).also {
                it.setDepthTextureFormat(depthFormat?.toGlDepthTextureFormat())
                it.setTextureFilterMod(if (requestedMipLevels > 1) GL_LINEAR_MIPMAP_LINEAR else GL_LINEAR)
                it.init()
            }
            return ManagedTarget(
                key,
                buffer,
                width,
                height,
                attachmentCount,
                format,
                requestedMipLevels,
                depthFormat
            )
        }
        if (current.width != width || current.height != height) {
            current.buffer.resize(width, height)
            current.width = width
            current.height = height
        }
        return current
    }

    private fun programFor(vertex: ResourceLocation?, fragment: ResourceLocation): CooShaderProgram {
        val key = PostProgramKey(vertex ?: screenVertexId, fragment)
        val program = programs.getOrPut(key) {
            AdvancedShaderProgramBuilder()
                .vertex(IdentifierShader(key.vertex, GlShaderType.VERTEX))
                .fragment(IdentifierShader(fragment, GlShaderType.FRAGMENT))
                .attributeLocation("position", 0)
                .attributeLocation("uv", 1)
                .managedId(managedProgramId(key))
                .build()
                .also { it.init() }
        }
        if (program.program == 0) {
            program.init()
        }
        return program
    }

    private fun screenBuffer(): SimpleVertexBuffer {
        return screenBuffer ?: VertexBuffers.getScreenBuffer().also {
            it.init()
            screenBuffer = it
        }
    }

    private fun copyColor(sourceFramebufferId: Int, sourceWidth: Int, sourceHeight: Int, target: ManagedTarget, context: RenderFrameContext) {
        val previousRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
        val previousDraw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        var previousReadBuffer = GL_COLOR_ATTACHMENT0
        clearPendingGlErrors()
        try {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, sourceFramebufferId)
            previousReadBuffer = glGetInteger(GL_READ_BUFFER)
            glReadBuffer(GL_COLOR_ATTACHMENT0)
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, target.buffer.fbo())
            check(operationCompletedWithoutGlError()) {
                "Scene color framebuffer setup failed before blit"
            }
            clearPendingGlErrors()
            glBlitFramebuffer(
                0,
                0,
                sourceWidth,
                sourceHeight,
                0,
                0,
                max(1, context.targetWidth ?: sourceWidth),
                max(1, context.targetHeight ?: sourceHeight),
                GL_COLOR_BUFFER_BIT,
                GL_NEAREST
            )
            check(operationCompletedWithoutGlError()) {
                "Scene color blit failed"
            }
        } finally {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, sourceFramebufferId)
            glReadBuffer(previousReadBuffer)
            glBindFramebuffer(GL_READ_FRAMEBUFFER, previousRead)
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previousDraw)
        }
    }

    private fun copySceneDepth(context: RenderFrameContext, target: ManagedTarget): Boolean {
        val irisDepth = IrisCompat.currentSceneDepthTexture()
        val fallbackSource = context.sceneDepthFramebufferId
            ?: context.sceneResources[RenderSceneTargets.SCENE_DEPTH]?.target?.frameBufferId
        if (irisDepth == null && fallbackSource == null) return false
        val sourceFormat = if (irisDepth != null) {
            resolveDepthTextureFormat(irisDepth.textureId)
        } else {
            resolveDepthFramebufferFormat(requireNotNull(fallbackSource))
        } ?: return false
        val targetFormat = resolveDepthTextureFormat(target.buffer.getCurrentDepthAttachment()) ?: return false
        if (sourceFormat.blitClass.depthBits != targetFormat.blitClass.depthBits ||
            sourceFormat.blitClass.floatingPoint != targetFormat.blitClass.floatingPoint
        ) return false
        val fallbackSourceSize = if (irisDepth == null) {
            resolveDepthFramebufferSize(requireNotNull(fallbackSource))
        } else {
            null
        }
        val sourceWidth = max(
            1,
            irisDepth?.width ?: fallbackSourceSize?.first
                ?: context.targetWidth ?: ClientRenderPipelineManager.currentRenderWidth()
        )
        val sourceHeight = max(
            1,
            irisDepth?.height ?: fallbackSourceSize?.second
                ?: context.targetHeight ?: ClientRenderPipelineManager.currentRenderHeight()
        )
        val previousRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
        val previousDraw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        clearPendingGlErrors()
        try {
            val source = if (irisDepth != null) {
                if (irisDepthReadFramebuffer <= 0) {
                    irisDepthReadFramebuffer = glGenFramebuffers()
                }
                glBindFramebuffer(GL_FRAMEBUFFER, irisDepthReadFramebuffer)
                glFramebufferTexture2D(
                    GL_FRAMEBUFFER,
                    GL_DEPTH_ATTACHMENT,
                    GL_TEXTURE_2D,
                    0,
                    0
                )
                glFramebufferTexture2D(
                    GL_FRAMEBUFFER,
                    GL_STENCIL_ATTACHMENT,
                    GL_TEXTURE_2D,
                    0,
                    0
                )
                glFramebufferTexture2D(
                    GL_FRAMEBUFFER,
                    sourceFormat.attachment,
                    GL_TEXTURE_2D,
                    irisDepth.textureId,
                    0
                )
                glDrawBuffer(GL_NONE)
                glReadBuffer(GL_NONE)
                if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) return false
                irisDepthReadFramebuffer
            } else {
                requireNotNull(fallbackSource)
            }
            glBindFramebuffer(GL_READ_FRAMEBUFFER, source)
            val hasDepthAttachment = glGetFramebufferAttachmentParameteri(
                GL_READ_FRAMEBUFFER,
                GL_DEPTH_ATTACHMENT,
                GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
            ) != GL_NONE
            if (!hasDepthAttachment) {
                return false
            }
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, target.buffer.fbo())
            if (glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
                return false
            }
            if (!operationCompletedWithoutGlError()) return false
            clearPendingGlErrors()
            glBlitFramebuffer(
                0,
                0,
                sourceWidth,
                sourceHeight,
                0,
                0,
                target.width,
                target.height,
                GL_DEPTH_BUFFER_BIT,
                GL_NEAREST
            )
            return operationCompletedWithoutGlError()
        } finally {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, previousRead)
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previousDraw)
        }
    }

    /**
     * 执行自定义绘制，并在离开作用域时恢复调用方的 RenderSystem 与底层 OpenGL 状态。
     *
     * 保存范围包括混合、深度、裁剪、颜色写入、polygon offset、shader、VAO、前 12 个纹理单元、
     * framebuffer 和 viewport。即使 [block] 抛出异常也会执行恢复。
     *
     * 示例：`withPreservedGlState { drawTerrainOverlay() }`。
     *
     * @param block 允许临时修改上述状态的绘制代码
     */
    internal fun withPreservedGlState(block: () -> Unit) {
        // 自定义绘制会改动 program、混合、深度、viewport、FBO 和活动纹理，进入前必须保存这些状态。
        val depthEnabled = glIsEnabled(GL_DEPTH_TEST)
        val cullEnabled = glIsEnabled(GL_CULL_FACE)
        val scissorEnabled = glIsEnabled(GL_SCISSOR_TEST)
        val blendEnabled = glIsEnabled(GL_BLEND)
        val indexedBlendStates = captureIndexedBlendStates()
        val depthMask = glGetBoolean(GL_DEPTH_WRITEMASK)
        val depthFunc = glGetInteger(GL_DEPTH_FUNC)
        val polygonOffsetEnabled = glIsEnabled(GL_POLYGON_OFFSET_FILL)
        val previousPolygonOffsetFactor = glGetFloat(GL_POLYGON_OFFSET_FACTOR)
        val previousPolygonOffsetUnits = glGetFloat(GL_POLYGON_OFFSET_UNITS)
        val blendSrcRgb = glGetInteger(GL_BLEND_SRC_RGB)
        val blendDstRgb = glGetInteger(GL_BLEND_DST_RGB)
        val blendSrcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA)
        val blendDstAlpha = glGetInteger(GL_BLEND_DST_ALPHA)
        val blendEqRgb = glGetInteger(GL_BLEND_EQUATION_RGB)
        val blendEqAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA)
        val previousShader = RenderSystem.getShader()
        val previousProgram = glGetInteger(GL_CURRENT_PROGRAM)
        val previousActive = glGetInteger(GL_ACTIVE_TEXTURE)
        val previousVertexArray = glGetInteger(GL_VERTEX_ARRAY_BINDING)
        val previousShaderTextures = IntArray(12) { RenderSystem.getShaderTexture(it) }
        val previousTextureBindings = IntArray(previousShaderTextures.size) { index ->
            glActiveTexture(GL_TEXTURE0 + index)
            glGetInteger(GL_TEXTURE_BINDING_2D)
        }
        glActiveTexture(previousActive)
        val previousColorMask = IntArray(4)
        glGetIntegerv(GL_COLOR_WRITEMASK, previousColorMask)
        val previousScissorBox = IntArray(4)
        glGetIntegerv(GL_SCISSOR_BOX, previousScissorBox)
        val previousReadFbo = glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
        val previousDrawFbo = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        val previousViewport = IntArray(4)
        glGetIntegerv(GL_VIEWPORT, previousViewport)
        try {
            block()
        } finally {
            // 同时恢复 RenderSystem 缓存和真实 GL 状态，避免 Iris 绕过缓存后留下错误绑定。
            RenderSystem.blendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha)
            glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha)
            glBlendEquationSeparate(blendEqRgb, blendEqAlpha)
            RenderSystem.depthMask(depthMask)
            glDepthMask(depthMask)
            RenderSystem.depthFunc(depthFunc)
            glDepthFunc(depthFunc)
            RenderSystem.polygonOffset(previousPolygonOffsetFactor, previousPolygonOffsetUnits)
            glPolygonOffset(previousPolygonOffsetFactor, previousPolygonOffsetUnits)
            if (polygonOffsetEnabled) {
                RenderSystem.enablePolygonOffset()
                glEnable(GL_POLYGON_OFFSET_FILL)
            } else {
                RenderSystem.disablePolygonOffset()
                glDisable(GL_POLYGON_OFFSET_FILL)
            }
            glUseProgram(previousProgram)
            RenderSystem.setShader { previousShader }
            val safePreviousVertexArray = if (previousVertexArray == 0 || glIsVertexArray(previousVertexArray)) {
                 previousVertexArray
             } else {
                 0
             }
             glBindVertexArray(safePreviousVertexArray)
            previousShaderTextures.forEachIndexed { index, texture ->
                RenderSystem.setShaderTexture(index, texture)
                RenderSystem.activeTexture(GL_TEXTURE0 + index)
                glActiveTexture(GL_TEXTURE0 + index)
                RenderSystem.bindTexture(previousTextureBindings[index])
                glBindTexture(GL_TEXTURE_2D, previousTextureBindings[index])
            }
            RenderSystem.activeTexture(previousActive)
            glActiveTexture(previousActive)
            glBindFramebuffer(GL_READ_FRAMEBUFFER, previousReadFbo)
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previousDrawFbo)
            RenderSystem.viewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3])
            glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3])
            RenderSystem.colorMask(
                previousColorMask[0] != 0,
                previousColorMask[1] != 0,
                previousColorMask[2] != 0,
                previousColorMask[3] != 0
            )
            if (blendEnabled) {
                RenderSystem.enableBlend()
            } else {
                RenderSystem.disableBlend()
            }
            restoreIndexedBlendStates(indexedBlendStates)
            if (depthEnabled) {
                RenderSystem.enableDepthTest()
            } else {
                RenderSystem.disableDepthTest()
            }
            if (cullEnabled) {
                RenderSystem.enableCull()
            } else {
                RenderSystem.disableCull()
            }
            if (scissorEnabled) {
                RenderSystem.enableScissor(
                    previousScissorBox[0],
                    previousScissorBox[1],
                    previousScissorBox[2],
                    previousScissorBox[3]
                )
            } else {
                RenderSystem.disableScissor()
            }
        }
    }

    /** 保存每个 draw buffer 的独立混合状态，避免全屏 pass 破坏 Iris 辅助附件。 */
    private fun captureIndexedBlendStates(): List<IndexedBlendState> {
        if (!CParticleIndexedBlendState.isAvailable()) return emptyList()
        return List(glGetInteger(GL_MAX_DRAW_BUFFERS)) { drawBuffer ->
            IndexedBlendState(
                enabled = glIsEnabledi(GL_BLEND, drawBuffer),
                sourceRgb = indexedInteger(GL_BLEND_SRC_RGB, drawBuffer),
                destinationRgb = indexedInteger(GL_BLEND_DST_RGB, drawBuffer),
                sourceAlpha = indexedInteger(GL_BLEND_SRC_ALPHA, drawBuffer),
                destinationAlpha = indexedInteger(GL_BLEND_DST_ALPHA, drawBuffer),
                equationRgb = indexedInteger(GL_BLEND_EQUATION_RGB, drawBuffer),
                equationAlpha = indexedInteger(GL_BLEND_EQUATION_ALPHA, drawBuffer),
            )
        }
    }

    private fun restoreIndexedBlendStates(states: List<IndexedBlendState>) {
        states.forEachIndexed { drawBuffer, state ->
            CParticleIndexedBlendState.setFactors(
                drawBuffer,
                state.sourceRgb,
                state.destinationRgb,
                state.sourceAlpha,
                state.destinationAlpha,
            )
            CParticleIndexedBlendState.setEquation(drawBuffer, state.equationRgb, state.equationAlpha)
            if (state.enabled) {
                glEnablei(GL_BLEND, drawBuffer)
            } else {
                glDisablei(GL_BLEND, drawBuffer)
            }
        }
    }

    private fun indexedInteger(parameter: Int, drawBuffer: Int): Int {
        val value = IntArray(1)
        glGetIntegeri_v(parameter, drawBuffer, value)
        return value[0]
    }

    private data class IndexedBlendState(
        val enabled: Boolean,
        val sourceRgb: Int,
        val destinationRgb: Int,
        val sourceAlpha: Int,
        val destinationAlpha: Int,
        val equationRgb: Int,
        val equationAlpha: Int,
    )

    /** 在完整状态保护内切换到全屏后处理使用的无混合、无深度、无裁剪状态。 */
    private fun withFlatPostState(block: () -> Unit) {
        withPreservedGlState {
            RenderSystem.disableBlend()
            RenderSystem.disableDepthTest()
            RenderSystem.disableCull()
            if (glIsEnabled(GL_SCISSOR_TEST)) {
                RenderSystem.disableScissor()
            }
            block()
        }
    }

    private fun frameKey(context: RenderFrameContext): FrameKey {
        val scene = context.sceneResources[RenderSceneTargets.SCENE_COLOR]
        val width = max(1, context.targetWidth ?: ClientRenderPipelineManager.currentRenderWidth())
        val height = max(1, context.targetHeight ?: ClientRenderPipelineManager.currentRenderHeight())
        return FrameKey(
            width = width,
            height = height,
            sourceFbo = chainedSceneFramebufferId ?: context.sceneColorFramebufferId ?: scene?.target?.frameBufferId ?: context.finalCompositeTarget?.frameBufferId ?: -1,
            sourceTexture = context.sceneColorTextureId ?: scene?.colorTextureId ?: -1,
            finalFbo = context.finalCompositeFramebufferId ?: context.finalCompositeTarget?.frameBufferId ?: -1
        )
    }

    private fun managedProgramId(key: PostProgramKey): ResourceLocation {
        val vertexPath = key.vertex.path.replace('.', '_').replace('/', '_')
        val fragmentPath = key.fragment.path.replace('.', '_').replace('/', '_')
        return ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "post/runtime/${key.vertex.namespace}_${vertexPath}/${key.fragment.namespace}_$fragmentPath"
        )
    }

    private fun warnSceneCopyFailure(reason: String) {
        if (warnedSceneCopyFailure) {
            return
        }
        warnedSceneCopyFailure = true
        CooParticlesConstants.logger.warn("Post effect scene color copy is unavailable: {}", reason)
    }

    private fun warnTerrainSceneCopyFailure(reason: String) {
        if (warnedTerrainSceneCopyFailure) {
            return
        }
        warnedTerrainSceneCopyFailure = true
        CooParticlesConstants.logger.warn(
            "Terrain scene attachment copy is unavailable: {}; explicit terrain scene inputs use their configured fallback",
            reason
        )
    }

    private fun normalizedScreenRadius(value: Float): Float {
        return if (value > 1F) {
            (value / 16F).coerceIn(0.03F, 1.25F)
        } else {
            value.coerceIn(0.03F, 1.25F)
        }
    }

    private fun PostEffectInstance.floatParam(name: String): Float? {
        return when (val value = params[name]) {
            is PostEffectParamValue.FloatValue -> value.value
            is PostEffectParamValue.DoubleValue -> value.value.toFloat()
            is PostEffectParamValue.IntValue -> value.value.toFloat()
            else -> null
        }
    }

    private data class DepthTextureFormat(
        val internalFormat: Int,
        val pixelFormat: Int,
        val dataType: Int,
        val attachment: Int,
        val blitClass: DepthBlitClass
    ) {
        fun toGlDepthTextureFormat(): GlDepthTextureFormat {
            return GlDepthTextureFormat(internalFormat, pixelFormat, dataType, attachment)
        }
    }

    /** depth-only glBlitFramebuffer 需要 depth 位深与浮点编码兼容；stencil 不参与本次复制。 */
    private data class DepthBlitClass(
        val depthBits: Int,
        val stencilBits: Int,
        val floatingPoint: Boolean = false
    )

    private data class DepthSourceCache(
        val irisTextureId: Int,
        val framebufferId: Int,
        val width: Int,
        val height: Int,
        val format: DepthTextureFormat
    )

    private data class ColorCapture(
        val textureId: Int,
        val width: Int,
        val height: Int
    ) {
        fun release() {
            glDeleteTextures(textureId)
        }
    }

    private data class DepthCapture(
        val framebufferId: Int,
        val textureId: Int,
        val width: Int,
        val height: Int,
        val format: DepthTextureFormat
    ) {
        fun release() {
            glDeleteFramebuffers(framebufferId)
            glDeleteTextures(textureId)
        }
    }
    private data class ManagedTarget(
        val key: String,
        val buffer: SimpleFrameBuffer,
        var width: Int,
        var height: Int,
        val colorAttachmentCount: Int,
        val format: CooTextureFormat,
        val mipLevels: Int,
        val depthFormat: DepthTextureFormat?
    )



    private data class BoundInput(
        val samplerName: String,
        val textureId: Int,
        val textureSlot: Int
    )

    private data class InstanceFrameState(
        var frameKey: FrameKey? = null,
        val lastOutputTextures: MutableMap<PostEffectOutput, Int> = linkedMapOf(),
        val lastPassOutputTextures: MutableMap<PassAttachment, Int> = linkedMapOf()
    )

    private data class PassAttachment(val pass: String, val attachment: Int)

    private data class PostProgramKey(
        val vertex: ResourceLocation,
        val fragment: ResourceLocation
    )

    private data class NamedAttachment(val target: ResourceLocation, val attachment: Int)

    private data class FrameKey(
        val width: Int,
        val height: Int,
        val sourceFbo: Int,
        val sourceTexture: Int,
        val finalFbo: Int
    )
}
