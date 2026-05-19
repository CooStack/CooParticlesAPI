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
import org.lwjgl.opengl.GL33.GL_BLEND_DST_ALPHA
import org.lwjgl.opengl.GL33.GL_BLEND_DST_RGB
import org.lwjgl.opengl.GL33.GL_BLEND_EQUATION_ALPHA
import org.lwjgl.opengl.GL33.GL_BLEND_EQUATION_RGB
import org.lwjgl.opengl.GL33.GL_BLEND_SRC_ALPHA
import org.lwjgl.opengl.GL33.GL_BLEND_SRC_RGB
import org.lwjgl.opengl.GL33.GL_COLOR_BUFFER_BIT
import org.lwjgl.opengl.GL33.GL_CULL_FACE
import org.lwjgl.opengl.GL33.GL_CURRENT_PROGRAM
import org.lwjgl.opengl.GL33.GL_DEPTH_TEST
import org.lwjgl.opengl.GL33.GL_DEPTH_WRITEMASK
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
import org.lwjgl.opengl.GL33.glBlendEquationSeparate
import org.lwjgl.opengl.GL33.glBlendFuncSeparate
import org.lwjgl.opengl.GL33.glBlitFramebuffer
import org.lwjgl.opengl.GL33.glDepthMask
import org.lwjgl.opengl.GL33.glDisable
import org.lwjgl.opengl.GL33.glEnable
import org.lwjgl.opengl.GL33.glGetBoolean
import org.lwjgl.opengl.GL33.glGetInteger
import org.lwjgl.opengl.GL33.glGetIntegerv
import org.lwjgl.opengl.GL33.glIsEnabled
import org.lwjgl.opengl.GL33.glUseProgram
import org.lwjgl.opengl.GL33.glViewport
import java.util.function.Supplier
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
 * - 上传生命周期、binding、用户参数等 uniform
 *
 * 这层实现替代了每个 post 效果各自手写 GL 状态保存、FBO 生命周期、纹理绑定和 shader uniform 上传。
 */
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
    private val instanceLastSeenFrame = LinkedHashMap<String, Long>()
    private var frameCounter: Long = 0
    private const val TARGET_EVICTION_GRACE_FRAMES: Long = 60
    private var screenBuffer: SimpleVertexBuffer? = null
    private var sceneCopy: ManagedTarget? = null
    private var preparedSceneFrame: FrameKey? = null
    private var warnedSceneCopyFailure = false

    override fun prepareFrame(context: RenderFrameContext) {
        preparedSceneFrame = null
        instanceStates.clear()
        frameCounter++
        evictStaleTargets()
    }

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
        target.buffer.writeFrameBufferWith {
            drawStep(step, state)
        }
        val colorTexture = target.buffer.colorAttachments.firstOrNull() ?: 0
        state.lastOutputTextures[step.output.output] = colorTexture
        state.lastPassOutputTextures[step.pass.name] = colorTexture
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
        instanceLastSeenFrame.clear()
        frameCounter = 0
        preparedSceneFrame = null
        warnedSceneCopyFailure = false
    }

    private fun evictStaleTargets() {
        if (instanceLastSeenFrame.isEmpty()) {
            return
        }
        val staleInstances = mutableSetOf<String>()
        instanceLastSeenFrame.entries.removeAll { (instanceId, lastSeen) ->
            val drop = frameCounter - lastSeen > TARGET_EVICTION_GRACE_FRAMES
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
            val colorTexture = step.context.finalCompositeTarget?.colorTextureId ?: 0
            state.lastOutputTextures[step.output.output] = colorTexture
            state.lastPassOutputTextures[step.pass.name] = colorTexture
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
        val previousActive = glGetInteger(org.lwjgl.opengl.GL33.GL_ACTIVE_TEXTURE)
        val previousBindings = linkedMapOf<Int, Int>()
        try {
            availableInputs.forEach { input ->
                glActiveTexture(GL_TEXTURE0 + input.textureSlot)
                previousBindings[input.textureSlot] = glGetInteger(GL_TEXTURE_BINDING_2D)
                glBindTexture(GL_TEXTURE_2D, input.textureId)
                program.setInt(input.samplerName, input.textureSlot)
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
        return when (input.source) {
            PostEffectInputSource.SCENE_COLOR -> ensureSceneCopy(step.context)?.buffer?.colorAttachments?.firstOrNull()
                ?: input.textureId
            PostEffectInputSource.SCENE_DEPTH -> input.textureId
            PostEffectInputSource.MASK -> state.lastOutputTextures[PostEffectOutput.MASK]
                ?: ensureBindingMask(step, state)
            PostEffectInputSource.BRIGHT_COLOR -> state.lastOutputTextures[PostEffectOutput.BLOOM]
                ?: input.textureId
            PostEffectInputSource.CUSTOM_TEXTURE -> customTexture(input, step.instance)
            PostEffectInputSource.PASS_OUTPUT -> input.producedByPassName?.let { state.lastPassOutputTextures[it] }
            PostEffectInputSource.SCENE_RESOURCE -> input.textureId
        }?.takeIf { it > 0 }
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
        val hasDepth = step.inputs.any {
            it.available && (it.source == PostEffectInputSource.SCENE_DEPTH ||
                (it.source == PostEffectInputSource.SCENE_RESOURCE &&
                    it.sourceResourceChannel == PostEffectResourceChannel.DEPTH))
        }
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
            }
        }
    }

    private fun resolveBindingCenter(context: RenderFrameContext, binding: PostEffectBinding): Vector2f? {
        return when (binding) {
            PostEffectBinding.Screen -> Vector2f(0.5f, 0.5f)
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
        // We are about to draw a screen quad with our own program/blend state. Iris (and even
        // vanilla under some pipelines) leaves blend equation/func, depth mask, viewport, FBO and
        // active texture in whatever state the previous shader pack expected. Snapshot enough of
        // it so the surrounding pipeline does not see surprise changes when we are done.
        val depthEnabled = glIsEnabled(GL_DEPTH_TEST)
        val cullEnabled = glIsEnabled(GL_CULL_FACE)
        val scissorEnabled = glIsEnabled(GL_SCISSOR_TEST)
        val blendEnabled = glIsEnabled(GL_BLEND)
        val depthMask = glGetBoolean(GL_DEPTH_WRITEMASK)
        val blendSrcRgb = glGetInteger(GL_BLEND_SRC_RGB)
        val blendDstRgb = glGetInteger(GL_BLEND_DST_RGB)
        val blendSrcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA)
        val blendDstAlpha = glGetInteger(GL_BLEND_DST_ALPHA)
        val blendEqRgb = glGetInteger(GL_BLEND_EQUATION_RGB)
        val blendEqAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA)
        val previousProgram = glGetInteger(GL_CURRENT_PROGRAM)
        val previousActive = glGetInteger(org.lwjgl.opengl.GL33.GL_ACTIVE_TEXTURE)
        val previousReadFbo = glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
        val previousDrawFbo = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        val previousViewport = IntArray(4)
        glGetIntegerv(GL_VIEWPORT, previousViewport)
        RenderSystem.disableBlend()
        RenderSystem.disableDepthTest()
        RenderSystem.disableCull()
        if (scissorEnabled) {
            glDisable(GL_SCISSOR_TEST)
        }
        try {
            block()
        } finally {
            // Order matters: restore GL state before higher level RenderSystem flags so the cached
            // RenderSystem state and the actual driver state line up after we leave.
            glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha)
            glBlendEquationSeparate(blendEqRgb, blendEqAlpha)
            glDepthMask(depthMask)
            glUseProgram(previousProgram)
            glActiveTexture(previousActive)
            glBindFramebuffer(GL_READ_FRAMEBUFFER, previousReadFbo)
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previousDrawFbo)
            glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3])
            if (blendEnabled) {
                RenderSystem.enableBlend()
            } else {
                RenderSystem.disableBlend()
            }
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
                glEnable(GL_SCISSOR_TEST)
            } else {
                glDisable(GL_SCISSOR_TEST)
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

    private data class BoundInput(
        val samplerName: String,
        val textureId: Int,
        val textureSlot: Int
    )

    private data class InstanceFrameState(
        var frameKey: FrameKey? = null,
        val lastOutputTextures: MutableMap<PostEffectOutput, Int> = linkedMapOf(),
        val lastPassOutputTextures: MutableMap<String, Int> = linkedMapOf()
    )

    private data class FrameKey(
        val width: Int,
        val height: Int,
        val sourceFbo: Int,
        val sourceTexture: Int,
        val finalFbo: Int
    )
}
