package cn.coostack.cooparticlesapi.renderer.post

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneTargets
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderPipelineManager
import cn.coostack.cooparticlesapi.renderer.shader.AdvancedShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.glsl.IdentifierShader
import cn.coostack.cooparticlesapi.renderer.shader.glsl.SimpleFrameBuffer
import cn.coostack.cooparticlesapi.renderer.shader.texture.IdentifierTexture
import cn.coostack.cooparticlesapi.renderer.shader.vertex.SimpleVertexBuffer
import cn.coostack.cooparticlesapi.renderer.shader.vertex.VertexBuffers
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.GL33.GL_BLEND
import org.lwjgl.opengl.GL33.GL_COLOR_BUFFER_BIT
import org.lwjgl.opengl.GL33.GL_CULL_FACE
import org.lwjgl.opengl.GL33.GL_DEPTH_TEST
import org.lwjgl.opengl.GL33.GL_DRAW_FRAMEBUFFER
import org.lwjgl.opengl.GL33.GL_DRAW_FRAMEBUFFER_BINDING
import org.lwjgl.opengl.GL33.GL_FRAMEBUFFER
import org.lwjgl.opengl.GL33.GL_FRAMEBUFFER_BINDING
import org.lwjgl.opengl.GL33.GL_LINEAR
import org.lwjgl.opengl.GL33.GL_LINEAR_MIPMAP_LINEAR
import org.lwjgl.opengl.GL33.GL_READ_FRAMEBUFFER
import org.lwjgl.opengl.GL33.GL_READ_FRAMEBUFFER_BINDING
import org.lwjgl.opengl.GL33.GL_SCISSOR_TEST
import org.lwjgl.opengl.GL33.GL_TEXTURE0
import org.lwjgl.opengl.GL33.GL_TEXTURE_2D
import org.lwjgl.opengl.GL33.GL_TEXTURE_BINDING_2D
import org.lwjgl.opengl.GL33.GL_VIEWPORT
import org.lwjgl.opengl.GL33.glActiveTexture
import org.lwjgl.opengl.GL33.glBindFramebuffer
import org.lwjgl.opengl.GL33.glBindTexture
import org.lwjgl.opengl.GL33.glBlitFramebuffer
import org.lwjgl.opengl.GL33.glDisable
import org.lwjgl.opengl.GL33.glEnable
import org.lwjgl.opengl.GL33.glGetInteger
import org.lwjgl.opengl.GL33.glGetIntegerv
import org.lwjgl.opengl.GL33.glIsEnabled
import org.lwjgl.opengl.GL33.glViewport
import java.util.function.Supplier
import kotlin.math.max

object OpenGlPostEffectExecutionBackend : PostEffectExecutionBackend,
    PostEffectFramePreparationBackend,
    PostEffectResourceBackend {
    private val screenVertexId: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "pipe/vertexes/screen.vsh")
    private val bindingMaskFragmentId: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "post/binding_mask.fsh")

    private val programs = LinkedHashMap<ResourceLocation, CooShaderProgram>()
    private val customTextures = LinkedHashMap<ResourceLocation, IdentifierTexture>()
    private val targets = LinkedHashMap<String, ManagedTarget>()
    private val instanceStates = LinkedHashMap<String, InstanceFrameState>()
    private var screenBuffer: SimpleVertexBuffer? = null
    private var sceneCopy: ManagedTarget? = null
    private var preparedSceneFrame: FrameKey? = null
    private var warnedSceneCopyFailure = false

    override fun prepareFrame(context: RenderFrameContext) {
        preparedSceneFrame = null
        instanceStates.clear()
    }

    override fun execute(step: PostEffectExecutionStep) {
        val state = instanceStates.getOrPut(step.instance.instanceId) { InstanceFrameState() }
        val frameKey = frameKey(step.context)
        if (state.frameKey != frameKey || step.passIndex == 0) {
            state.frameKey = frameKey
            state.lastOutputTextures.clear()
        }

        if (step.output.output == PostEffectOutput.FINAL_SCREEN) {
            drawToFinal(step, state)
            return
        }

        val target = targetFor(step)
        target.buffer.writeFrameBufferWith {
            drawStep(step, state)
        }
        state.lastOutputTextures[step.output.output] = target.buffer.colorAttachments.firstOrNull() ?: 0
    }

    override fun release() {
        sceneCopy?.buffer?.release()
        sceneCopy = null
        targets.values.forEach { it.buffer.release() }
        targets.clear()
        programs.values.forEach { it.release() }
        programs.clear()
        customTextures.values.forEach { it.release() }
        customTextures.clear()
        screenBuffer?.release()
        screenBuffer = null
        instanceStates.clear()
        preparedSceneFrame = null
        warnedSceneCopyFailure = false
    }

    private fun ensureSceneCopy(context: RenderFrameContext): ManagedTarget? {
        val frameKey = frameKey(context)
        if (preparedSceneFrame == frameKey) {
            return sceneCopy
        }

        val source = context.sceneResources[RenderSceneTargets.SCENE_COLOR]?.target
            ?: context.finalCompositeTarget
            ?: Minecraft.getInstance().mainRenderTarget
        val sourceFramebufferId = context.sceneColorFramebufferId ?: source.frameBufferId
        if (sourceFramebufferId <= 0) {
            warnSceneCopyFailure("invalid source framebuffer=$sourceFramebufferId")
            return null
        }

        val target = ensureManagedTarget(sceneCopy, "scene_copy", context).also { sceneCopy = it }
        return try {
            val sourceWidth = max(1, context.targetWidth ?: source.width)
            val sourceHeight = max(1, context.targetHeight ?: source.height)
            copyColor(sourceFramebufferId, sourceWidth, sourceHeight, target, context)
            preparedSceneFrame = frameKey
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
        val previousFramebuffer = glGetInteger(GL_FRAMEBUFFER_BINDING)
        val previousViewport = IntArray(4)
        glGetIntegerv(GL_VIEWPORT, previousViewport)
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer)
        glViewport(0, 0, width, height)
        try {
            drawStep(step, state)
        } finally {
            glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3])
            glBindFramebuffer(GL_FRAMEBUFFER, previousFramebuffer)
        }
    }

    private fun drawStep(step: PostEffectExecutionStep, state: InstanceFrameState) {
        val program = programFor(step.pass.fragment)
        val buffer = screenBuffer()
        withFlatPostState {
            program.useOnContext {
                uploadBuiltInUniforms(this, step)
                bindInputs(step, state, this) {
                    uploadUniforms(this, step.uniforms)
                    buffer.draw()
                }
            }
        }
        if (step.output.output == PostEffectOutput.FINAL_SCREEN) {
            state.lastOutputTextures[step.output.output] = step.context.finalCompositeTarget?.colorTextureId ?: 0
        }
    }

    private fun bindInputs(
        step: PostEffectExecutionStep,
        state: InstanceFrameState,
        program: CooShaderProgram,
        draw: () -> Unit
    ) {
        val missingRequiredInputs = mutableListOf<String>()
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
            input.samplerName to texture
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
        val previousActive = glGetInteger(org.lwjgl.opengl.GL33.GL_ACTIVE_TEXTURE)
        val previousBindings = IntArray(availableInputs.size)
        try {
            availableInputs.forEachIndexed { index, (samplerName, textureId) ->
                glActiveTexture(GL_TEXTURE0 + index)
                previousBindings[index] = glGetInteger(GL_TEXTURE_BINDING_2D)
                glBindTexture(GL_TEXTURE_2D, textureId)
                program.setInt(samplerName, index)
            }
            draw()
        } finally {
            availableInputs.indices.reversed().forEach { index ->
                glActiveTexture(GL_TEXTURE0 + index)
                glBindTexture(GL_TEXTURE_2D, previousBindings[index])
            }
            glActiveTexture(previousActive)
        }
    }

    private fun resolveInputTexture(
        step: PostEffectExecutionStep,
        state: InstanceFrameState,
        input: PostEffectResolvedInput
    ): Int? {
        return when (input.source) {
            PostEffectInputSource.SCENE_COLOR -> ensureSceneCopy(step.context)?.buffer?.colorAttachments?.firstOrNull()
                ?: input.textureId
            PostEffectInputSource.SCENE_DEPTH -> input.textureId
            PostEffectInputSource.MASK -> state.lastOutputTextures[PostEffectOutput.MASK]
                ?: ensureBindingMask(step, state)
            PostEffectInputSource.BRIGHT_COLOR -> state.lastOutputTextures[PostEffectOutput.BLOOM]
                ?: input.textureId
            PostEffectInputSource.CUSTOM_TEXTURE -> customTexture(input, step.instance)
        }?.takeIf { it > 0 }
    }

    private fun customTexture(input: PostEffectResolvedInput, instance: PostEffectInstance): Int? {
        val id = (instance.params[input.samplerName] as? PostEffectParamValue.Resource)?.value ?: return null
        val texture = customTextures.getOrPut(id) {
            IdentifierTexture(id).also { it.init() }
        }
        return texture.textureID()
    }

    private fun ensureBindingMask(step: PostEffectExecutionStep, state: InstanceFrameState): Int? {
        val target = targetFor(step.context, "${step.instance.instanceId}:binding_mask")
        val program = programFor(bindingMaskFragmentId)
        val buffer = screenBuffer()
        target.buffer.writeFrameBufferWith {
            withFlatPostState {
                program.useOnContext {
                    uploadBuiltInUniforms(this, step)
                    val radius = normalizedScreenRadius(step.instance.floatParam("screenRadius") ?: step.instance.floatParam("radius") ?: 0.22f)
                    val feather = step.instance.floatParam("feather") ?: 0.08f
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
        val center = resolveBindingCenter(step.context, step.instance.binding) ?: Vector2f(0.5f, 0.5f)
        val sourceDepth = resolveBindingDepth(step.context, step.instance.binding) ?: 1f
        val hasDepth = step.inputs.any { it.source == PostEffectInputSource.SCENE_DEPTH && it.available }
        program.setFloat("progress", step.instance.progress)
        program.setFloat2("center", center)
        program.setFloat("sourceDepth", sourceDepth)
        program.setBoolean("hasDepth", hasDepth)
        program.setFloat2("screenSize", Vector2f(width.toFloat(), height.toFloat()))
        program.setFloat2("texelSize", Vector2f(1f / width.toFloat(), 1f / height.toFloat()))
    }

    private fun uploadUniforms(program: CooShaderProgram, uniforms: Map<String, PostEffectParamValue>) {
        uniforms.forEach { (name, value) ->
            when (value) {
                is PostEffectParamValue.Bool -> program.setBoolean(name, value.value)
                is PostEffectParamValue.IntValue -> program.setInt(name, value.value)
                is PostEffectParamValue.LongValue -> program.setFloat(name, value.value.toFloat())
                is PostEffectParamValue.FloatValue -> program.setFloat(name, value.value)
                is PostEffectParamValue.DoubleValue -> program.setFloat(name, value.value.toFloat())
                is PostEffectParamValue.StringValue -> Unit
                is PostEffectParamValue.Resource -> Unit
                is PostEffectParamValue.Vec2 -> program.setFloat2(name, Vector2f(value.x, value.y))
                is PostEffectParamValue.Vec3 -> program.setFloat3(name, Vector3f(value.x.toFloat(), value.y.toFloat(), value.z.toFloat()))
                is PostEffectParamValue.Color -> program.setFloat4(name, Vector4f(value.red, value.green, value.blue, value.alpha))
            }
        }
    }

    private fun resolveBindingCenter(context: RenderFrameContext, binding: PostEffectBinding): Vector2f? {
        return when (binding) {
            PostEffectBinding.Screen -> Vector2f(0.5f, 0.5f)
            is PostEffectBinding.ScreenPoint -> Vector2f(binding.x, binding.y)
            is PostEffectBinding.WorldPos -> projectWorld(context, binding.x, binding.y, binding.z)
            is PostEffectBinding.Block -> {
                val offset = binding.offset ?: PostEffectParamValue.Vec3(0.5, 0.5, 0.5)
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
            is PostEffectBinding.Item -> Vector2f(0.5f, 0.5f)
            is PostEffectBinding.Custom -> null
        }
    }

    private fun projectWorld(context: RenderFrameContext, x: Double, y: Double, z: Double): Vector2f? {
        return projectWorldClip(context, x, y, z)?.let { Vector2f(it.x, it.y) }
    }

    private fun resolveBindingDepth(context: RenderFrameContext, binding: PostEffectBinding): Float? {
        return when (binding) {
            PostEffectBinding.Screen -> 1f
            is PostEffectBinding.ScreenPoint -> 1f
            is PostEffectBinding.WorldPos -> projectWorldClip(context, binding.x, binding.y, binding.z)?.z
            is PostEffectBinding.Block -> {
                val offset = binding.offset ?: PostEffectParamValue.Vec3(0.5, 0.5, 0.5)
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
            is PostEffectBinding.Item -> 1f
            is PostEffectBinding.Custom -> null
        }
    }

    private fun projectWorldClip(context: RenderFrameContext, x: Double, y: Double, z: Double): Vector3f? {
        val camera = Minecraft.getInstance().gameRenderer.mainCamera.position
        val clip = Vector4f(
            (x - camera.x).toFloat(),
            (y - camera.y).toFloat(),
            (z - camera.z).toFloat(),
            1f
        )
        context.viewMatrix.transform(clip)
        context.projMatrix.transform(clip)
        if (clip.w <= 0.0001f) {
            return null
        }
        val invW = 1f / clip.w
        val ndcX = clip.x * invW
        val ndcY = clip.y * invW
        if (ndcX.isNaN() || ndcY.isNaN()) {
            return null
        }
        val ndcZ = clip.z * invW
        return Vector3f(
            (ndcX * 0.5f + 0.5f).coerceIn(0f, 1f),
            (ndcY * 0.5f + 0.5f).coerceIn(0f, 1f),
            (ndcZ * 0.5f + 0.5f).coerceIn(0f, 1f)
        )
    }

    private fun targetFor(step: PostEffectExecutionStep): ManagedTarget {
        return targetFor(
            step.context,
            "${step.instance.instanceId}:${step.passIndex}:${step.output.targetKey}:${step.output.output.name.lowercase()}",
            useMipmaps = step.output.output == PostEffectOutput.BLOOM,
            scaleDivisor = step.output.scaleDivisor
        )
    }

    private fun targetFor(
        context: RenderFrameContext,
        key: String,
        useMipmaps: Boolean = false,
        scaleDivisor: Int = 1
    ): ManagedTarget {
        val current = targets[key]
        val target = ensureManagedTarget(current, key, context, useMipmaps, scaleDivisor)
        targets[key] = target
        return target
    }

    private fun ensureManagedTarget(
        current: ManagedTarget?,
        key: String,
        context: RenderFrameContext,
        useMipmaps: Boolean = false,
        scaleDivisor: Int = 1
    ): ManagedTarget {
        val divisor = scaleDivisor.coerceAtLeast(1)
        val width = max(1, (context.targetWidth ?: ClientRenderPipelineManager.currentRenderWidth()) / divisor)
        val height = max(1, (context.targetHeight ?: ClientRenderPipelineManager.currentRenderHeight()) / divisor)
        if (current == null) {
            val buffer = SimpleFrameBuffer(1, Supplier { -1 }, width, height).also {
                if (useMipmaps) {
                    it.useMipmap()
                    it.setTextureFilterMod(GL_LINEAR_MIPMAP_LINEAR)
                } else {
                    it.setTextureFilterMod(GL_LINEAR)
                }
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

    private fun screenBuffer(): SimpleVertexBuffer {
        return screenBuffer ?: VertexBuffers.getScreenBuffer().also {
            it.init()
            screenBuffer = it
        }
    }

    private fun copyColor(sourceFramebufferId: Int, sourceWidth: Int, sourceHeight: Int, target: ManagedTarget, context: RenderFrameContext) {
        val previousRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
        val previousDraw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        try {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, sourceFramebufferId)
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, target.buffer.fbo())
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
                GL_LINEAR
            )
        } finally {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, previousRead)
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previousDraw)
        }
    }

    private fun withFlatPostState(block: () -> Unit) {
        val depthEnabled = glIsEnabled(GL_DEPTH_TEST)
        val cullEnabled = glIsEnabled(GL_CULL_FACE)
        val scissorEnabled = glIsEnabled(GL_SCISSOR_TEST)
        val blendEnabled = glIsEnabled(GL_BLEND)
        RenderSystem.disableBlend()
        RenderSystem.disableDepthTest()
        RenderSystem.disableCull()
        if (scissorEnabled) {
            glDisable(GL_SCISSOR_TEST)
        }
        try {
            block()
        } finally {
            if (blendEnabled) {
                RenderSystem.enableBlend()
            }
            if (depthEnabled) {
                RenderSystem.enableDepthTest()
            }
            if (cullEnabled) {
                RenderSystem.enableCull()
            }
            if (scissorEnabled) {
                glEnable(GL_SCISSOR_TEST)
            }
        }
    }

    private fun frameKey(context: RenderFrameContext): FrameKey {
        val scene = context.sceneResources[RenderSceneTargets.SCENE_COLOR]
        val width = max(1, context.targetWidth ?: ClientRenderPipelineManager.currentRenderWidth())
        val height = max(1, context.targetHeight ?: ClientRenderPipelineManager.currentRenderHeight())
        return FrameKey(
            width = width,
            height = height,
            sourceFbo = context.sceneColorFramebufferId ?: scene?.target?.frameBufferId ?: context.finalCompositeTarget?.frameBufferId ?: -1,
            sourceTexture = context.sceneColorTextureId ?: scene?.colorTextureId ?: -1,
            finalFbo = context.finalCompositeFramebufferId ?: context.finalCompositeTarget?.frameBufferId ?: -1
        )
    }

    private fun managedProgramId(fragment: ResourceLocation): ResourceLocation {
        val safePath = fragment.path.replace('.', '_')
        return ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "post/runtime/${fragment.namespace}/$safePath"
        )
    }

    private fun warnSceneCopyFailure(reason: String) {
        if (warnedSceneCopyFailure) {
            return
        }
        warnedSceneCopyFailure = true
        CooParticlesConstants.logger.warn("Post effect scene color copy is unavailable: {}", reason)
    }

    private fun normalizedScreenRadius(value: Float): Float {
        return if (value > 1f) {
            (value / 16f).coerceIn(0.03f, 1.25f)
        } else {
            value.coerceIn(0.03f, 1.25f)
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

    private data class ManagedTarget(
        val key: String,
        val buffer: SimpleFrameBuffer,
        var width: Int,
        var height: Int
    )

    private data class InstanceFrameState(
        var frameKey: FrameKey? = null,
        val lastOutputTextures: MutableMap<PostEffectOutput, Int> = linkedMapOf()
    )

    private data class FrameKey(
        val width: Int,
        val height: Int,
        val sourceFbo: Int,
        val sourceTexture: Int,
        val finalFbo: Int
    )
}
