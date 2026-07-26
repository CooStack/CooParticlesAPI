package cn.coostack.cooparticlesapi.renderer.effects.builtin

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderPipelineManager
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectDescriptor
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectExecutor
import cn.coostack.cooparticlesapi.renderer.shader.AdvancedShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlFrameBuffer
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.data.VertexData
import cn.coostack.cooparticlesapi.renderer.shader.glsl.IdentifierShader
import cn.coostack.cooparticlesapi.renderer.shader.glsl.SimpleFrameBuffer
import cn.coostack.cooparticlesapi.renderer.shader.vertex.SimpleVertexBuffer
import cn.coostack.cooparticlesapi.renderer.shader.vertex.VertexBuffers
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.opengl.GL33.GL_ACTIVE_TEXTURE
import org.lwjgl.opengl.GL33.GL_BLEND
import org.lwjgl.opengl.GL33.GL_BLEND_DST_ALPHA
import org.lwjgl.opengl.GL33.GL_BLEND_DST_RGB
import org.lwjgl.opengl.GL33.GL_BLEND_SRC_ALPHA
import org.lwjgl.opengl.GL33.GL_BLEND_SRC_RGB
import org.lwjgl.opengl.GL33.GL_COLOR_BUFFER_BIT
import org.lwjgl.opengl.GL33.GL_CULL_FACE
import org.lwjgl.opengl.GL33.GL_DEPTH_FUNC
import org.lwjgl.opengl.GL33.GL_DEPTH_TEST
import org.lwjgl.opengl.GL33.GL_DEPTH_WRITEMASK
import org.lwjgl.opengl.GL33.GL_FRAMEBUFFER
import org.lwjgl.opengl.GL33.GL_FRAMEBUFFER_BINDING
import org.lwjgl.opengl.GL33.GL_LEQUAL
import org.lwjgl.opengl.GL33.GL_LINEAR
import org.lwjgl.opengl.GL33.GL_ONE
import org.lwjgl.opengl.GL33.GL_SCISSOR_TEST
import org.lwjgl.opengl.GL33.GL_SRC_ALPHA
import org.lwjgl.opengl.GL33.GL_TEXTURE0
import org.lwjgl.opengl.GL33.GL_TEXTURE_2D
import org.lwjgl.opengl.GL33.GL_TEXTURE_BINDING_2D
import org.lwjgl.opengl.GL33.GL_TRIANGLES
import org.lwjgl.opengl.GL33.GL_VIEWPORT
import org.lwjgl.opengl.GL33.GL_ZERO
import org.lwjgl.opengl.GL33.glActiveTexture
import org.lwjgl.opengl.GL33.glBindFramebuffer
import org.lwjgl.opengl.GL33.glBindTexture
import org.lwjgl.opengl.GL33.glBlendFuncSeparate
import org.lwjgl.opengl.GL33.glClear
import org.lwjgl.opengl.GL33.glClearColor
import org.lwjgl.opengl.GL33.glDepthFunc
import org.lwjgl.opengl.GL33.glDepthMask
import org.lwjgl.opengl.GL33.glDisable
import org.lwjgl.opengl.GL33.glEnable
import org.lwjgl.opengl.GL33.glGetBoolean
import org.lwjgl.opengl.GL33.glGetInteger
import org.lwjgl.opengl.GL33.glGetIntegerv
import org.lwjgl.opengl.GL33.glIsEnabled
import org.lwjgl.opengl.GL33.glViewport
import java.util.function.Supplier
import kotlin.math.max
import kotlin.math.pow

object OpenGlMaskBloomEffectExecutor : RenderEffectExecutor {
    private val screenVertexId: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "pipe/vertexes/screen.vsh")
    private val brightExtractId: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "post/bloom_bright_extract.fsh")
    private val blurHorizontalId: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "post/bloom_blur_horizontal.fsh")
    private val blurVerticalId: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "post/bloom_blur_vertical.fsh")
    private val compositeId: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "post/mask_bloom_composite.fsh")
    private val texturedBillboardVertexId: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "core/vertex/billboard_from_model_uv.vsh")
    private val texturedBillboardFragmentId: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "core/fragment/mask_bloom_textured_billboard.fsh")

    private val programs = LinkedHashMap<ResourceLocation, CooShaderProgram>()
    private val targets = LinkedHashMap<String, ManagedTarget>()
    private var screenBuffer: SimpleVertexBuffer? = null
    private var texturedBillboardProgram: CooShaderProgram? = null
    private var texturedBillboardBuffer: SimpleVertexBuffer? = null

    override fun render(context: RenderFrameContext, effects: List<RenderEffectDescriptor>) {
        effects.forEach { descriptor ->
            val request = descriptor.payload as? MaskBloomRenderRequest ?: return@forEach
            renderRequest(context, descriptor, request)
        }
    }

    fun release() {
        targets.values.forEach { it.buffer.release() }
        targets.clear()
        programs.values.forEach { it.release() }
        programs.clear()
        texturedBillboardProgram?.release()
        texturedBillboardProgram = null
        screenBuffer?.release()
        screenBuffer = null
        texturedBillboardBuffer?.release()
        texturedBillboardBuffer = null
    }

    private fun renderRequest(
        context: RenderFrameContext,
        descriptor: RenderEffectDescriptor,
        request: MaskBloomRenderRequest
    ) {
        val sceneDepthFramebuffer = context.sceneDepthFramebufferId?.takeIf { it > 0 }
        val source = targetFor(context, "${descriptor.effectId}:source")
        val bright = targetFor(context, "${descriptor.effectId}:bright")
        val blurA = targetFor(context, "${descriptor.effectId}:blur_h")
        val blurB = targetFor(context, "${descriptor.effectId}:blur_v")

        source.writeWithCleanColor {
            sceneDepthFramebuffer?.let { copyDepthBuffer(it) }
            request.renderMask(maskContext(context, request))
        }
        val sourceTexture = source.textureId() ?: return

        drawBrightExtract(
            target = bright,
            sourceTexture = sourceTexture,
            config = request.config
        )
        val brightTexture = bright.textureId() ?: return

        val blurPasses: Int
        val blurIterations: Int
        when (request.config.bloomMode) {
            MaskBloomMode.SOFT -> {
                blurPasses = 1
                blurIterations = 1
            }

            MaskBloomMode.STRONG -> {
                blurPasses = STRONG_MODE_BLUR_PASSES
                blurIterations = STRONG_MODE_BLUR_ITERATIONS
            }
        }
        var blurredTexture = brightTexture
        repeat(blurPasses) {
            drawBlur(
                context = context,
                target = blurA,
                fragment = blurHorizontalId,
                sourceTexture = blurredTexture,
                config = request.config,
                iterations = blurIterations
            )
            val blurATexture = blurA.textureId() ?: return
            drawBlur(
                context = context,
                target = blurB,
                fragment = blurVerticalId,
                sourceTexture = blurATexture,
                config = request.config,
                iterations = blurIterations
            )
            blurredTexture = blurB.textureId() ?: return
        }
        compositeToFinal(context, blurredTexture, sourceTexture, request.config, exposureFor(context, request))
    }

    /**
     * 根据泛光模式、曝光补偿和距离聚光补偿计算最终合成阶段的曝光倍率。
     *
     * 默认配置（SOFT / ev = 0 / 无距离补偿）返回 1.0，与旧版本行为一致。
     */
    private fun exposureFor(context: RenderFrameContext, request: MaskBloomRenderRequest): Float {
        val config = request.config
        var exposure = when (config.bloomMode) {
            MaskBloomMode.SOFT -> 1.0f
            MaskBloomMode.STRONG -> STRONG_MODE_EXPOSURE_BOOST
        }
        if (config.exposureCompensation != 0.0f) {
            exposure *= 2.0f.pow(config.exposureCompensation)
        }
        val compensation = config.distanceCompensation ?: return exposure
        val entity = request.sourceEntity ?: return exposure
        val camera = Minecraft.getInstance().gameRenderer.mainCamera.position
        val renderPos = entity.lastRenderPos.lerp(entity.pos, context.tickDelta.toDouble())
        val distance = camera.distanceTo(renderPos).toFloat()
        val range = (compensation.fullDistance - compensation.startDistance).coerceAtLeast(1.0E-3f)
        val progress = ((distance - compensation.startDistance) / range).coerceIn(0f, 1f)
        exposure *= 1f + progress * (compensation.maxBoost - 1f)
        return exposure
    }

    private fun maskContext(
        context: RenderFrameContext,
        request: MaskBloomRenderRequest
    ): MaskBloomMaskRenderContext {
        val camera = Minecraft.getInstance().gameRenderer.mainCamera.position
        return MaskBloomMaskRenderContext(
            frameContext = context,
            sourceEntity = request.sourceEntity,
            sourceInstanceId = request.sourceInstanceId,
            cameraWorldPos = Vector3f(camera.x.toFloat(), camera.y.toFloat(), camera.z.toFloat()),
            screenSize = Vector2f(
                max(1, context.targetWidth ?: ClientRenderPipelineManager.currentRenderWidth()).toFloat(),
                max(1, context.targetHeight ?: ClientRenderPipelineManager.currentRenderHeight()).toFloat()
            ),
            texturedBillboardDrawer = { content ->
                drawTexturedBillboard(context, content)
            }
        )
    }

    private fun drawTexturedBillboard(context: RenderFrameContext, content: MaskBloomTexturedBillboard) {
        if (content.textures.getTextureCounts() <= 0) {
            CooParticlesConstants.logger.debug("Mask bloom textured billboard skipped because it has no texture")
            return
        }
        val program = texturedBillboardProgram()
        val buffer = texturedBillboardBuffer()
        withTexturedBillboardState {
            program.useOnContext {
                setMatrix4("projMat", context.projMatrix)
                setMatrix4("viewMat", context.viewMatrix)
                setMatrix4("transMat", content.modelMatrix)
                setFloat4("tint", content.tint)
                setFloat("sourceBoost", content.sourceBoost.coerceAtLeast(0f))
                setFloat("alphaCutoff", content.alphaCutoff.coerceAtLeast(0f))
                setFloat("emissiveCutoff", content.emissiveCutoff.coerceAtLeast(0f))
                setFloat("alphaWeight", content.alphaWeight.coerceAtLeast(0f))
                setFloat("emissiveWeight", content.emissiveWeight.coerceAtLeast(0f))
                setBoolean("fullQuadMask", content.fullQuadMask)
                setFloat("fullQuadMaskSoftness", content.fullQuadMaskSoftness.coerceIn(0.001f, 1f))
                setInt("tex", 0)
                repeat(content.textures.getTextureCounts()) { index ->
                    setInt("tex$index", index)
                    setInt("texture$index", index)
                }
                content.textures.drawWith(Runnable { buffer.draw() })
            }
        }
    }

    private fun drawBlur(
        context: RenderFrameContext,
        target: ManagedTarget,
        fragment: ResourceLocation,
        sourceTexture: Int,
        config: MaskBloomConfig,
        iterations: Int = 1
    ) {
        target.writeWithCleanColor {
            val program = programFor(fragment)
            withFlatState {
                program.useOnContext {
                    setFloat("blurRadius", config.blurRange.coerceAtLeast(1f))
                    setInt("iterations", iterations.coerceAtLeast(1))
                    bindTexture(this, "bright", sourceTexture) {
                        screenBuffer().draw()
                    }
                }
            }
        }
    }

    private fun drawBrightExtract(
        target: ManagedTarget,
        sourceTexture: Int,
        config: MaskBloomConfig
    ) {
        target.writeWithCleanColor {
            val program = programFor(brightExtractId)
            withFlatState {
                program.useOnContext {
                    setFloat("threshold", config.threshold)
                    setFloat("softKnee", config.thresholdSoftness)
                    bindTexture(this, "scene", sourceTexture) {
                        screenBuffer().draw()
                    }
                }
            }
        }
    }

    private fun compositeToFinal(
        context: RenderFrameContext,
        bloomTexture: Int,
        maskTexture: Int,
        config: MaskBloomConfig,
        exposure: Float = 1.0f
    ) {
        val target = context.finalCompositeTarget ?: Minecraft.getInstance().mainRenderTarget
        val framebuffer = context.finalCompositeFramebufferId?.takeIf { it > 0 } ?: target.frameBufferId
        val width = max(1, context.targetWidth ?: target.width)
        val height = max(1, context.targetHeight ?: target.height)
        val previousFramebuffer = glGetInteger(GL_FRAMEBUFFER_BINDING)
        val previousViewport = IntArray(4)
        glGetIntegerv(GL_VIEWPORT, previousViewport)
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer)
        glViewport(0, 0, width, height)
        try {
            val program = programFor(compositeId)
            withAdditiveCompositeState {
                program.useOnContext {
                    setFloat("intensity", config.intensity)
                    setFloat("baseMaskIntensity", config.baseMaskIntensity)
                    setFloat("exposure", exposure.coerceAtLeast(0f))
                    setFloat3("tint", config.tint)
                    bindTextures(this, "bloom" to bloomTexture, "mask" to maskTexture) {
                        screenBuffer().draw()
                    }
                }
            }
        } finally {
            glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3])
            glBindFramebuffer(GL_FRAMEBUFFER, previousFramebuffer)
        }
    }

    private fun bindTexture(program: CooShaderProgram, samplerName: String, texture: Int, draw: () -> Unit) {
        bindTextures(program, samplerName to texture, draw = draw)
    }

    private fun bindTextures(
        program: CooShaderProgram,
        vararg textures: Pair<String, Int>,
        draw: () -> Unit
    ) {
        val previousActive = glGetInteger(GL_ACTIVE_TEXTURE)
        val previousBindings = IntArray(textures.size)
        try {
            textures.forEachIndexed { index, (samplerName, texture) ->
                glActiveTexture(GL_TEXTURE0 + index)
                previousBindings[index] = glGetInteger(GL_TEXTURE_BINDING_2D)
                glBindTexture(GL_TEXTURE_2D, texture)
                program.setInt(samplerName, index)
            }
            draw()
        } finally {
            textures.indices.reversed().forEach { index ->
                glActiveTexture(GL_TEXTURE0 + index)
                glBindTexture(GL_TEXTURE_2D, previousBindings[index])
            }
            glActiveTexture(previousActive)
        }
    }

    private fun withFlatState(block: () -> Unit) {
        val depthEnabled = glIsEnabled(GL_DEPTH_TEST)
        val cullEnabled = glIsEnabled(GL_CULL_FACE)
        val scissorEnabled = glIsEnabled(GL_SCISSOR_TEST)
        glDisable(GL_DEPTH_TEST)
        glDisable(GL_CULL_FACE)
        glDisable(GL_SCISSOR_TEST)
        try {
            block()
        } finally {
            if (depthEnabled) glEnable(GL_DEPTH_TEST) else glDisable(GL_DEPTH_TEST)
            if (cullEnabled) glEnable(GL_CULL_FACE) else glDisable(GL_CULL_FACE)
            if (scissorEnabled) glEnable(GL_SCISSOR_TEST) else glDisable(GL_SCISSOR_TEST)
        }
    }

    private fun withAdditiveCompositeState(block: () -> Unit) {
        val blendEnabled = glIsEnabled(GL_BLEND)
        val depthEnabled = glIsEnabled(GL_DEPTH_TEST)
        val cullEnabled = glIsEnabled(GL_CULL_FACE)
        val scissorEnabled = glIsEnabled(GL_SCISSOR_TEST)
        val blendSrcRgb = glGetInteger(GL_BLEND_SRC_RGB)
        val blendDstRgb = glGetInteger(GL_BLEND_DST_RGB)
        val blendSrcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA)
        val blendDstAlpha = glGetInteger(GL_BLEND_DST_ALPHA)
        glEnable(GL_BLEND)
        glBlendFuncSeparate(GL_ONE, GL_ONE, GL_ZERO, GL_ONE)
        glDisable(GL_DEPTH_TEST)
        glDisable(GL_CULL_FACE)
        glDisable(GL_SCISSOR_TEST)
        try {
            block()
        } finally {
            glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha)
            if (blendEnabled) glEnable(GL_BLEND) else glDisable(GL_BLEND)
            if (depthEnabled) glEnable(GL_DEPTH_TEST) else glDisable(GL_DEPTH_TEST)
            if (cullEnabled) glEnable(GL_CULL_FACE) else glDisable(GL_CULL_FACE)
            if (scissorEnabled) glEnable(GL_SCISSOR_TEST) else glDisable(GL_SCISSOR_TEST)
        }
    }

    private fun withTexturedBillboardState(block: () -> Unit) {
        val blendEnabled = glIsEnabled(GL_BLEND)
        val depthEnabled = glIsEnabled(GL_DEPTH_TEST)
        val cullEnabled = glIsEnabled(GL_CULL_FACE)
        val scissorEnabled = glIsEnabled(GL_SCISSOR_TEST)
        val depthMask = glGetBoolean(GL_DEPTH_WRITEMASK)
        val depthFunc = glGetInteger(GL_DEPTH_FUNC)
        val blendSrcRgb = glGetInteger(GL_BLEND_SRC_RGB)
        val blendDstRgb = glGetInteger(GL_BLEND_DST_RGB)
        val blendSrcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA)
        val blendDstAlpha = glGetInteger(GL_BLEND_DST_ALPHA)
        glEnable(GL_BLEND)
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE, GL_SRC_ALPHA, GL_ONE)
        glEnable(GL_DEPTH_TEST)
        glDepthFunc(GL_LEQUAL)
        glDepthMask(false)
        glDisable(GL_CULL_FACE)
        glDisable(GL_SCISSOR_TEST)
        try {
            block()
        } finally {
            glDepthMask(depthMask)
            glDepthFunc(depthFunc)
            glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha)
            if (blendEnabled) glEnable(GL_BLEND) else glDisable(GL_BLEND)
            if (depthEnabled) glEnable(GL_DEPTH_TEST) else glDisable(GL_DEPTH_TEST)
            if (cullEnabled) glEnable(GL_CULL_FACE) else glDisable(GL_CULL_FACE)
            if (scissorEnabled) glEnable(GL_SCISSOR_TEST) else glDisable(GL_SCISSOR_TEST)
        }
    }

    private fun targetFor(
        context: RenderFrameContext,
        key: String
    ): ManagedTarget {
        val current = targets[key]
        val target = ensureTarget(current, key, context)
        targets[key] = target
        return target
    }

    private fun ensureTarget(
        current: ManagedTarget?,
        key: String,
        context: RenderFrameContext
    ): ManagedTarget {
        val width = scaledTargetSize(context.targetWidth ?: ClientRenderPipelineManager.currentRenderWidth())
        val height = scaledTargetSize(context.targetHeight ?: ClientRenderPipelineManager.currentRenderHeight())
        if (current == null) {
            current?.buffer?.release()
            val buffer = SimpleFrameBuffer(1, Supplier { -1 }, width, height).also {
                it.setTextureFilterMod(GL_LINEAR)
                it.init()
            }
            return ManagedTarget(key, buffer, width, height)
        }
        if (current.width != width || current.height != height) {
            current.buffer.resize(width, height)
            current.width = width
            current.height = height
        }
        return current
    }

    private fun programFor(fragment: ResourceLocation): CooShaderProgram {
        val program = programs.getOrPut(fragment) {
            AdvancedShaderProgramBuilder()
                .vertex(IdentifierShader(screenVertexId, GlShaderType.VERTEX))
                .fragment(IdentifierShader(fragment, GlShaderType.FRAGMENT))
                .managedId(managedProgramId(fragment))
                .build()
                .also { it.init() }
        }
        if (program.program == 0) {
            program.init()
        }
        return program
    }

    private fun texturedBillboardProgram(): CooShaderProgram {
        val current = texturedBillboardProgram
        if (current != null) {
            if (current.program == 0) {
                current.init()
            }
            return current
        }
        return AdvancedShaderProgramBuilder()
            .vertex(IdentifierShader(texturedBillboardVertexId, GlShaderType.VERTEX))
            .fragment(IdentifierShader(texturedBillboardFragmentId, GlShaderType.FRAGMENT))
            .managedId(
                ResourceLocation.fromNamespaceAndPath(
                    CooParticlesConstants.MOD_ID,
                    "effect/mask_bloom/runtime/textured_billboard"
                )
            )
            .build()
            .also { created ->
                created.init()
                texturedBillboardProgram = created
            }
    }

    private fun screenBuffer(): SimpleVertexBuffer {
        return screenBuffer ?: VertexBuffers.getScreenBuffer().also {
            it.init()
            screenBuffer = it
        }
    }

    private fun texturedBillboardBuffer(): SimpleVertexBuffer {
        return texturedBillboardBuffer ?: SimpleVertexBuffer().also { created ->
            created.init()
            created.drawMode = GL_TRIANGLES
            created.setVertexes(
                listOf(
                    VertexData(Vector3f(-0.5f, -0.5f, 0.0f), Vector2f(0.0f, 0.0f)),
                    VertexData(Vector3f(0.5f, -0.5f, 0.0f), Vector2f(1.0f, 0.0f)),
                    VertexData(Vector3f(0.5f, 0.5f, 0.0f), Vector2f(1.0f, 1.0f)),
                    VertexData(Vector3f(-0.5f, -0.5f, 0.0f), Vector2f(0.0f, 0.0f)),
                    VertexData(Vector3f(0.5f, 0.5f, 0.0f), Vector2f(1.0f, 1.0f)),
                    VertexData(Vector3f(-0.5f, 0.5f, 0.0f), Vector2f(0.0f, 1.0f))
                ),
                CooVertexFormat.POINT_TEXTURE_UV_FORMAT
            )
            texturedBillboardBuffer = created
        }
    }

    private fun managedProgramId(fragment: ResourceLocation): ResourceLocation {
        val safePath = fragment.path.replace('.', '_')
        return ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "effect/mask_bloom/runtime/${fragment.namespace}/$safePath"
        )
    }

    private fun scaledTargetSize(size: Int): Int {
        return max(1, size / TARGET_SCALE_DIVISOR)
    }

    private fun ManagedTarget.textureId(): Int? {
        return buffer.colorAttachments.firstOrNull()?.takeIf { it > 0 }
    }

    private fun ManagedTarget.writeWithCleanColor(writeScope: GlFrameBuffer.() -> Unit) {
        val previousViewport = IntArray(4)
        glGetIntegerv(GL_VIEWPORT, previousViewport)
        try {
            buffer.writeFrameBufferWith {
                glViewport(0, 0, width, height)
                glClearColor(0f, 0f, 0f, 0f)
                glClear(GL_COLOR_BUFFER_BIT)
                writeScope()
            }
        } finally {
            glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3])
        }
    }

    private data class ManagedTarget(
        val key: String,
        val buffer: SimpleFrameBuffer,
        var width: Int,
        var height: Int
    )

    private const val TARGET_SCALE_DIVISOR = 2
    private const val STRONG_MODE_BLUR_PASSES = 2
    private const val STRONG_MODE_BLUR_ITERATIONS = 2
    private const val STRONG_MODE_EXPOSURE_BOOST = 1.5f
}
