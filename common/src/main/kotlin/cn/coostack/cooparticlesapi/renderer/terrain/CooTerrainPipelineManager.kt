package cn.coostack.cooparticlesapi.renderer.terrain

import cn.coostack.cooparticlesapi.CooParticlesAPIClient
import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.compat.IrisCompat
import cn.coostack.cooparticlesapi.compat.IrisTerrainDepthTexture
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneResources
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneTargets
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderPipelineManager
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderSceneResourcesResolver
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderTargetResolver
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectCollector
import cn.coostack.cooparticlesapi.renderer.pipeline.CooBlockPipelines
import cn.coostack.cooparticlesapi.renderer.pipeline.CooCompiledAttachment
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineCompiler
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineNodeKind
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelinePostEffectCompiler
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineOutputPort
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineInputPort
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineTextureSource
import cn.coostack.cooparticlesapi.renderer.pipeline.CooRenderPipeline
import cn.coostack.cooparticlesapi.renderer.pipeline.CooTerrainLayer
import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import cn.coostack.cooparticlesapi.renderer.post.OpenGlPostEffectExecutionBackend
import cn.coostack.cooparticlesapi.renderer.post.PostEffectFrameExecutor
import cn.coostack.cooparticlesapi.renderer.post.PostEffectRuntimeRegistry
import cn.coostack.cooparticlesapi.renderer.shader.ShaderReloadBus
import cn.coostack.cooparticlesapi.renderer.shader.ShaderReloadSignal
import cn.coostack.cooparticlesapi.renderer.shader.CooShaderSourceLoader
import com.mojang.blaze3d.shaders.Uniform
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.IoSupplier
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceProvider
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import org.joml.Matrix4f
import org.lwjgl.opengl.GL33.GL_DRAW_FRAMEBUFFER_BINDING
import org.lwjgl.opengl.GL33.GL_DRAW_FRAMEBUFFER
import org.lwjgl.opengl.GL33.GL_DEPTH_ATTACHMENT
import org.lwjgl.opengl.GL33.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
import org.lwjgl.opengl.GL33.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
import org.lwjgl.opengl.GL33.GL_FRAMEBUFFER_COMPLETE
import org.lwjgl.opengl.GL33.GL_NONE
import org.lwjgl.opengl.GL33.GL_RENDERBUFFER
import org.lwjgl.opengl.GL33.GL_TEXTURE
import org.lwjgl.opengl.GL33.GL_TEXTURE_2D
import org.lwjgl.opengl.GL33.glBindFramebuffer
import org.lwjgl.opengl.GL33.glCheckFramebufferStatus
import org.lwjgl.opengl.GL33.glFramebufferRenderbuffer
import org.lwjgl.opengl.GL33.glFramebufferTexture2D
import org.lwjgl.opengl.GL33.glGetFramebufferAttachmentParameteri
import org.lwjgl.opengl.GL33.glGetInteger
import java.util.IdentityHashMap
import java.util.Optional

internal data class CooTerrainShaderCacheKey(
    val shaderId: ResourceLocation,
    val descriptor: String
)

/** 客户端 terrain pipeline 编译、shader 生命周期和 section rebuild 协调器。 */
internal object CooTerrainPipelineManager {
    private val terrainLayers = LinkedHashMap<RenderType, RenderType>()
    private val terrainPipelines = LinkedHashMap<RenderType, CooRenderPipeline<BlockState>>()
    private val pipelineSubjects = IdentityHashMap<CooRenderPipeline<BlockState>, BlockState>()
    private val pendingPostDraws = LinkedHashMap<RenderType, Runnable>()
    private val shaders = LinkedHashMap<CooTerrainShaderCacheKey, ShaderInstance>()
    private val failedShaders = linkedSetOf<CooTerrainShaderCacheKey>()
    private var terrainEffectRevision = -1L
    private var initialized = false
    @Volatile
    private var sodiumLoaded = false
    @Volatile
    private var irisShaderPackActive = false
    @Volatile
    private var terrainOverlayDisabled = false
    private var terrainColorTextureId: Int? = null
    private var terrainDepthTextureId: Int? = null
    private var terrainSceneResources = RenderSceneResources.empty()
    private var terrainColorWidth = 1
    private var terrainColorHeight = 1
    private var irisDepthAttachmentRestore: IrisDepthAttachmentRestore? = null
    private val warnedRequiredSamplerFallbacks = linkedSetOf<String>()
    private var warnedTargetResolutionFallback = false
    private var warnedPostCaptureFailure = false
    private var deferredFrameFinish: DeferredFrameFinish? = null
    private var framePartialTick = 0F

    @JvmStatic
    fun initialize() {
        if (initialized) return
        initialized = true
        sodiumLoaded = CooParticlesServices.PLATFORM.isModLoaded("sodium")
        irisShaderPackActive = isIrisShaderPackActive()
        logCompatibilityMode()
        CooBlockPipelines.addChangeListener { requestSectionRebuild() }
        ShaderReloadBus.register { signal ->
            if (signal is ShaderReloadSignal.FullReload) {
                releaseResources()
                requestSectionRebuild()
            }
            null
        }
    }

    @JvmStatic
    fun updateCompatibilityState() {
        initialize()
        val currentSodium = CooParticlesServices.PLATFORM.isModLoaded("sodium")
        val currentIrisShaderPack = isIrisShaderPackActive()
        if (currentSodium == sodiumLoaded && currentIrisShaderPack == irisShaderPackActive) return
        sodiumLoaded = currentSodium
        irisShaderPackActive = currentIrisShaderPack
        terrainOverlayDisabled = false
        requestSectionRebuild()
        logCompatibilityMode()
    }

    @JvmStatic
    fun beginRenderFrame(partialTick: Float) {
        framePartialTick = partialTick.coerceIn(0F, 1F)
        val level = Minecraft.getInstance().level
        if (level != null) {
            val dimension = level.dimension().location()
            CooTerrainEffectRegistry.advance(dimension, level.gameTime)
            val currentRevision = CooTerrainEffectRegistry.revision()
            if (currentRevision != terrainEffectRevision) {
                terrainEffectRevision = currentRevision
            }
            val changedPositions = CooTerrainEffectRegistry.drainChangedPositions(dimension)
            if (changedPositions.isNotEmpty()) {
                requestSectionRebuild(changedPositions)
            }
        }
        synchronized(pendingPostDraws) {
            pendingPostDraws.clear()
        }
    }

    @JvmStatic
    fun recordPostDraw(renderType: RenderType, render: Runnable) {
        val pipeline = synchronized(terrainLayers) { terrainPipelines[renderType] } ?: return
        val compiled = CooPipelineCompiler.compile(pipeline)
        if (compiled.nodes.none { it.kind != CooPipelineNodeKind.WORLD } &&
            worldPostAttachments(compiled).isEmpty()
        ) {
            return
        }
        synchronized(pendingPostDraws) {
            pendingPostDraws[renderType] = render
        }
    }

    fun collectPostEffects(context: RenderFrameContext, collector: RenderEffectCollector) {
        val draws = synchronized(pendingPostDraws) {
            pendingPostDraws.toList().also { pendingPostDraws.clear() }
        }
        if (draws.isEmpty()) return
        val grouped = draws.groupBy { (renderType, _) ->
            synchronized(terrainLayers) { terrainPipelines[renderType] }
        }
        grouped.forEach { (pipeline, entries) ->
            pipeline ?: return@forEach
            val subject = synchronized(terrainLayers) {
                pipelineSubjects[pipeline]
            } ?: Blocks.AIR.defaultBlockState()
            val compiled = CooPipelineCompiler.compile(pipeline)
            val attachments = worldPostAttachments(compiled)
            val owner = "terrain:${pipeline.id}"
            val captured = attachments.isEmpty() || withPostCaptureInputs(context) {
                attachments.groupBy(CooCompiledAttachment::framebuffer).all { (framebuffer, outputs) ->
                    PostEffectFrameExecutor.captureAttachments(
                        context = context,
                        owner = owner,
                        target = framebuffer,
                        attachmentCount = outputs.maxOf { it.output.attachment } + 1
                    ) {
                        entries.forEach { (_, render) -> render.run() }
                    }
                }
            }
            if (!captured) {
                warnPostCaptureFailure(pipeline)
                return@forEach
            }
            val post = CooPipelinePostEffectCompiler.compile(pipeline, subject) ?: return@forEach
            PostEffectRuntimeRegistry.registerType(post.type)
            val instance = post.type.create(
                instanceId = "$owner:pipeline",
                params = post.defaultParams,
                sourceId = owner
            )
            collector.submit(post.type.toDescriptor(instance))
        }
    }

    @JvmStatic
    fun resolveOverlayRenderType(state: BlockState, original: RenderType, pos: BlockPos): RenderType? {
        initialize()
        if (terrainOverlayDisabled) return null
        val groupPipeline = terrainEffectGroups(pos).firstOrNull()?.pipeline
        val pipeline = groupPipeline ?: CooBlockPipelines.resolve(state)
        if (pipeline === cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelines.BLOCK_DEFAULT) return null
        if (pipeline.terrainShader == null) return null
        val baseLayer = resolveBaseLayer(pipeline.terrainLayer, original) ?: run {
            CooParticlesConstants.logger.warn(
                "Terrain pipeline {} uses unsupported base layer {}; keeping only the vanilla terrain draw",
                pipeline.id,
                original
            )
            return null
        }
        val renderType = CooParticlesServices.PLATFORM.getRenderTypesProvider().terrain(pipeline, baseLayer)
        synchronized(terrainLayers) {
            terrainLayers[renderType] = baseLayer
            terrainPipelines[renderType] = pipeline
            pipelineSubjects.putIfAbsent(pipeline, state)
        }
        return renderType
    }

    @JvmStatic
    fun resolveOverlayRenderType(state: BlockState, original: RenderType): RenderType? {
        return resolveOverlayRenderType(state, original, BlockPos.ZERO)
    }

    @JvmStatic
    fun decorateVertexConsumer(renderType: RenderType, pos: BlockPos, consumer: VertexConsumer): VertexConsumer {
        val pipeline = synchronized(terrainLayers) { terrainPipelines[renderType] } ?: return consumer
        val level = Minecraft.getInstance().level
        val activatedAt = level?.let {
            CooTerrainEffectRegistry.activationAt(
                it.dimension().location(),
                pos,
                pipeline,
                it.gameTime
            )
        } ?: 0L
        return CooEffectUvVertexConsumer(consumer, pipeline.effectUvMode, pos, activatedAt)
    }

    @JvmStatic
    fun vertexFormat(renderType: RenderType, original: VertexFormat): VertexFormat {
        return if (isTerrainRenderType(renderType)) CooTerrainVertexFormats.BLOCK_EFFECT else original
    }

    @JvmStatic
    fun isTerrainRenderType(renderType: RenderType): Boolean {
        return synchronized(terrainLayers) { renderType in terrainLayers }
    }

    @JvmStatic
    fun pipelineFor(renderType: RenderType): CooRenderPipeline<BlockState>? {
        return synchronized(terrainLayers) { terrainPipelines[renderType] }
    }

    @JvmStatic
    fun usesSodiumTerrainOverlay(): Boolean {
        return sodiumLoaded
    }

    @JvmStatic
    fun isSodiumTerrainOverlayEnabled(): Boolean {
        return sodiumLoaded && !terrainOverlayDisabled
    }

    @JvmStatic
    fun isTerrainOverlayEnabled(): Boolean {
        return !terrainOverlayDisabled
    }

    @JvmStatic
    fun shouldPreserveVanillaTerrainGeometry(): Boolean {
        return terrainOverlayDisabled || irisShaderPackActive
    }

    @JvmStatic
    fun deferFrameFinish(tickDelta: Float, viewMatrix: Matrix4f, projectionMatrix: Matrix4f): Boolean {
        if (!sodiumLoaded || !irisShaderPackActive || terrainOverlayDisabled) return false
        deferredFrameFinish = DeferredFrameFinish(
            tickDelta,
            Matrix4f(viewMatrix),
            Matrix4f(projectionMatrix)
        )
        return true
    }

    @JvmStatic
    fun finishDeferredFrame() {
        val deferred = deferredFrameFinish ?: return
        deferredFrameFinish = null
        ClientRenderPipelineManager.finishLevelRender(
            deferred.tickDelta,
            deferred.viewMatrix,
            deferred.projectionMatrix
        )
    }

    @JvmStatic
    fun requiresTerrainSorting(renderType: RenderType): Boolean {
        return synchronized(terrainLayers) { terrainLayers[renderType]?.sortOnUpload() == true }
    }

    @JvmStatic
    fun layersFor(baseLayer: RenderType): List<RenderType> {
        return synchronized(terrainLayers) {
            terrainLayers.filterValues { it === baseLayer }.keys.toList()
        }
    }

    @JvmStatic
    fun beginOverlayBatch(renderTypes: List<RenderType>) {
        restoreIrisDepthAttachment()
        terrainColorTextureId = null
        terrainDepthTextureId = null
        terrainSceneResources = RenderSceneResources.empty()
        try {
            val resources = ClientRenderSceneResourcesResolver.resolveCurrentResources()
            val targets = ClientRenderTargetResolver.resolveCurrentTargets()
            terrainSceneResources = resources
            val sources = synchronized(terrainLayers) {
                renderTypes.mapNotNull(terrainPipelines::get)
                    .flatMap { it.lines }
                    .map { it.output }
            }
            val sourceFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
            terrainColorWidth = targets.width.coerceAtLeast(1)
            terrainColorHeight = targets.height.coerceAtLeast(1)
            if (sources.any { it === CooPipelineTextureSource.SceneColor || it === CooPipelineTextureSource.SceneDepth }) {
                val captured = OpenGlPostEffectExecutionBackend.captureTerrainScene(
                    sourceFramebuffer,
                    terrainColorWidth,
                    terrainColorHeight,
                    resources
                )
                terrainColorTextureId = captured?.get(RenderSceneTargets.SCENE_COLOR)?.colorTextureId
                terrainDepthTextureId = captured?.get(RenderSceneTargets.SCENE_DEPTH)?.depthTextureId
            }
            if (irisShaderPackActive) {
                val irisDepth = IrisCompat.currentTerrainDepthTexture()
                if (irisDepth == null || !attachIrisTerrainDepth(sourceFramebuffer, irisDepth)) {
                    handleSodiumOverlayFailure(
                        "Iris terrain depth attachment",
                        renderTypes.firstOrNull(),
                        IllegalStateException("Iris terrain depth texture is unavailable or incompatible")
                    )
                    return
                }
            }
        } catch (error: RuntimeException) {
            if (irisShaderPackActive) {
                handleSodiumOverlayFailure("Iris terrain target resolution", renderTypes.firstOrNull(), error)
                return
            }
            if (!warnedTargetResolutionFallback) {
                warnedTargetResolutionFallback = true
                CooParticlesConstants.logger.error(
                    "Terrain target resolution failed; explicit scene inputs use their configured fallback",
                    error
                )
            }
        }
    }

    @JvmStatic
    fun endOverlayBatch() {
        restoreIrisDepthAttachment()
        terrainColorTextureId = null
        terrainDepthTextureId = null
        terrainSceneResources = RenderSceneResources.empty()
    }

    @JvmStatic
    fun handleOverlayDrawFailure(renderType: RenderType, error: RuntimeException): Boolean {
        return handleSodiumOverlayFailure("vanilla section draw", renderType, error)
    }

    @JvmStatic
    fun handleSodiumOverlayFailure(
        phase: String,
        renderType: RenderType?,
        error: RuntimeException
    ): Boolean {
        if (terrainOverlayDisabled) return true
        terrainOverlayDisabled = true
        CooParticlesConstants.logger.error(
            "Terrain overlay failed during {} for {}; disabling Coo terrain overlays and rebuilding " +
                "sections with vanilla geometry",
            phase,
            renderType ?: "unknown render type",
            error
        )
        requestSectionRebuild()
        return true
    }

    @JvmStatic
    fun shaderFor(pipeline: CooRenderPipeline<BlockState>, baseLayer: RenderType): ShaderInstance {
        val shaderId = requireNotNull(pipeline.terrainShader)
        val descriptor = buildGeneratedDescriptor(shaderId, pipeline)
        val cacheKey = CooTerrainShaderCacheKey(shaderId, descriptor)
        val shader = synchronized(shaders) {
                shaders[cacheKey] ?: createShader(cacheKey)?.also { shaders[cacheKey] = it }
        } ?: vanillaShader(baseLayer)
        bindInputs(shader, pipeline)
        bindUniforms(shader, pipeline, baseLayer)
        return shader
    }

    private fun createShader(cacheKey: CooTerrainShaderCacheKey): ShaderInstance? {
        return try {
            val minecraftPath = "${cacheKey.shaderId.namespace}/${cacheKey.shaderId.path}"
            val resources = Minecraft.getInstance().resourceManager
            ShaderInstance(
                generatedDescriptorProvider(resources, cacheKey.shaderId, cacheKey.descriptor),
                minecraftPath,
                CooTerrainVertexFormats.BLOCK_EFFECT
            )
        } catch (error: Exception) {
            if (failedShaders.add(cacheKey)) {
                CooParticlesConstants.logger.error(
                    "Failed to load terrain shader {}; using the vanilla terrain shader fallback",
                    cacheKey.shaderId,
                    error
                )
            }
            null
        }
    }

    /** 在缺少 core shader JSON 时提供内存描述符，公开 Pipeline 不需要维护 JSON 文件。 */
    private fun generatedDescriptorProvider(
        resources: ResourceProvider,
        shaderId: ResourceLocation,
        descriptor: String
    ): ResourceProvider {
        val descriptorLocation = ResourceLocation.withDefaultNamespace(
            "shaders/core/${shaderId.namespace}/${shaderId.path}.json"
        )
        val descriptorSource = resources.getResource(
            ResourceLocation.withDefaultNamespace("shaders/core/position_color.fsh")
        ).orElse(null)?.source() ?: return resources
        val descriptorBytes = descriptor.toByteArray(Charsets.UTF_8)
        return ResourceProvider { location ->
            if (location == descriptorLocation) {
                Optional.of(Resource(descriptorSource, IoSupplier { descriptorBytes.inputStream() }))
            } else {
                remapTerrainShaderSource(resources, location).or { resources.getResource(location) }
            }
        }
    }

    /** 将桥接层的源码请求映射为经过 Coo 预处理的 shader 文本。 */
    private fun remapTerrainShaderSource(
        resources: ResourceProvider,
        location: ResourceLocation
    ): Optional<Resource> {
        val prefix = "shaders/core/"
        if (location.namespace != "minecraft" || !location.path.startsWith(prefix)) {
            return Optional.empty()
        }
        val relative = location.path.removePrefix(prefix)
        val separator = relative.indexOf('/')
        if (separator <= 0 || separator == relative.lastIndex) {
            return Optional.empty()
        }
        val namespace = relative.substring(0, separator)
        val shaderPath = relative.substring(separator + 1)
        val mapped = ResourceLocation.fromNamespaceAndPath(namespace, "$prefix$shaderPath")
        val source = resources.getResource(mapped).orElse(null) ?: return Optional.empty()
        val processed = CooShaderSourceLoader.load(resources, mapped).toByteArray(Charsets.UTF_8)
        return Optional.of(Resource(source.source(), IoSupplier { processed.inputStream() }))
    }

    private fun buildGeneratedDescriptor(
        shaderId: ResourceLocation,
        pipeline: CooRenderPipeline<BlockState>
    ): String {
        val world = pipeline.nodes.firstOrNull { it.kind == CooPipelineNodeKind.WORLD }
        val samplers = linkedSetOf<String>().apply {
            world?.inputs?.forEach { add(it.sampler) }
            add("Sampler2")
        }
        val uniforms = linkedMapOf<String, String>().apply {
            put("ModelViewMat", matrixUniform("ModelViewMat"))
            put("ProjMat", matrixUniform("ProjMat"))
            put("ChunkOffset", floatUniform("ChunkOffset", 3))
            put("CameraPosition", floatUniform("CameraPosition", 3))
            put("CooEffectUvCameraPosition", floatUniform("CooEffectUvCameraPosition", 3))
            put("CooEffectUvMode", intUniform("CooEffectUvMode"))
            put("CooGameTime", floatUniform("CooGameTime"))
            put("CooAlphaCutoff", floatUniform("CooAlphaCutoff"))
            put("ColorModulator", floatUniform("ColorModulator", 4))
            put("FogStart", floatUniform("FogStart"))
            put("FogEnd", floatUniform("FogEnd"))
            put("FogColor", floatUniform("FogColor", 4))
            put("FogShape", intUniform("FogShape"))
            put("ScreenSize", floatUniform("ScreenSize", 2))
            put("CooIrisComposite", intUniform("CooIrisComposite"))
            world?.uniforms?.forEach { (name, provider) ->
                val value = runCatching { provider.resolve(Blocks.AIR.defaultBlockState()) }.getOrNull()
                put(name, value?.let { uniform(name, it) } ?: floatUniform(name))
            }
        }
        return buildString {
            append("{\n  \"vertex\": \"cooparticlesapi/terrain/block_effect\",\n")
            append("  \"fragment\": \"").append(shaderId.namespace).append('/').append(shaderId.path)
                .append("\",\n  \"samplers\": [")
            samplers.toList().forEachIndexed { index, sampler ->
                if (index > 0) append(',')
                append("{\"name\": \"").append(sampler).append("\"}")
            }
            append("],\n  \"uniforms\": [")
            uniforms.values.forEachIndexed { index, uniform ->
                if (index > 0) append(',')
                append(uniform)
            }
            append("]\n}\n")
        }
    }

    private fun matrixUniform(name: String): String =
        "{\"name\":\"$name\",\"type\":\"matrix4x4\",\"count\":16,\"values\":[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]}"

    private fun floatUniform(name: String, count: Int = 1): String =
        "{\"name\":\"$name\",\"type\":\"float\",\"count\":$count,\"values\":[${List(count) { "0" }.joinToString(",")}]}"

    private fun intUniform(name: String): String =
        "{\"name\":\"$name\",\"type\":\"int\",\"count\":1,\"values\":[0]}"

    private fun uniform(name: String, value: CooUniformValue): String {
        val (type, values) = when (value) {
            is CooUniformValue.FloatValue -> "float" to listOf(value.value)
            is CooUniformValue.IntValue -> "int" to listOf(value.value)
            is CooUniformValue.Vec2Value -> "float" to listOf(value.x, value.y)
            is CooUniformValue.Vec3Value -> "float" to listOf(value.x, value.y, value.z)
            is CooUniformValue.Vec4Value -> "float" to listOf(value.x, value.y, value.z, value.w)
        }
        return "{\"name\":\"$name\",\"type\":\"$type\",\"count\":${values.size},\"values\":[${values.joinToString(",")}]}"
    }

    private fun bindInputs(shader: ShaderInstance, pipeline: CooRenderPipeline<BlockState>) {
        val minecraft = Minecraft.getInstance()
        val atlas = minecraft.textureManager.getTexture(TextureAtlas.LOCATION_BLOCKS).id
        val worldNode = pipeline.nodes.firstOrNull { it.kind == CooPipelineNodeKind.WORLD } ?: return
        pipeline.lines.forEach { line ->
            val input = line.input as? cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineInputPort
                ?: return@forEach
            if (input.node != worldNode.name) return@forEach
            val source = line.output
            val resolvedTextureId = when (source) {
                CooPipelineTextureSource.BlockAtlas -> {
                    atlas
                }
                is CooPipelineTextureSource.Texture -> minecraft.textureManager.getTexture(source.texture).id
                CooPipelineTextureSource.SceneColor -> terrainColorTextureId
                CooPipelineTextureSource.SceneDepth -> terrainDepthTextureId
                is CooPipelineTextureSource.FramebufferColor -> resolveNamedColorTexture(
                    source.target,
                    source.attachment
                )
                CooPipelineTextureSource.Mask -> resolveNamedColorTexture(RenderSceneTargets.MASK, 0)
                CooPipelineTextureSource.Temporary -> resolveNamedColorTexture(RenderSceneTargets.TEMPORARY, 0)
                CooPipelineTextureSource.Bloom -> resolveNamedColorTexture(RenderSceneTargets.BLOOM, 0)
                is CooPipelineTextureSource.Parameter,
                is CooPipelineOutputPort -> null
            }?.takeIf { it > 0 }
            val textureId = resolvedTextureId ?: if (input.optional) {
                0
            } else {
                warnRequiredSamplerFallback(pipeline, input, source)
                atlas
            }
            shader.setSampler(input.sampler, textureId)
        }
        minecraft.gameRenderer.lightTexture().turnOnLightLayer()
        shader.setSampler("Sampler2", RenderSystem.getShaderTexture(2))
    }

    private fun bindUniforms(
        shader: ShaderInstance,
        pipeline: CooRenderPipeline<BlockState>,
        baseLayer: RenderType
    ) {
        val camera = Minecraft.getInstance().gameRenderer.mainCamera.position
        val subject = synchronized(terrainLayers) { pipelineSubjects[pipeline] } ?: Blocks.AIR.defaultBlockState()
        shader.getUniform("CameraPosition")?.set(camera.x.toFloat(), camera.y.toFloat(), camera.z.toFloat())
        shader.getUniform("CooEffectUvCameraPosition")?.set(
            CooEffectUvResolver.periodicWorldCoordinate(camera.x),
            CooEffectUvResolver.periodicWorldCoordinate(camera.y),
            CooEffectUvResolver.periodicWorldCoordinate(camera.z)
        )
        shader.getUniform("CooEffectUvMode")?.set(pipeline.effectUvMode.shaderValue)
        val gameTime = currentGameTime().toFloat() + framePartialTick
        shader.getUniform("CooGameTime")?.set(gameTime % 65536F)
        shader.getUniform("CooAlphaCutoff")?.set(alphaCutoff(baseLayer))
        shader.getUniform("CooIrisComposite")?.set(
            if (irisShaderPackActive && terrainColorTextureId != null) 1 else 0
        )
        pipeline.nodes.forEach { node ->
            node.uniforms.forEach { (name, provider) ->
                val value = runCatching { provider.resolve(subject) }.getOrNull() ?: return@forEach
                val uniform = shader.getUniform(name) ?: return@forEach
                setUniform(uniform, value)
            }
        }
        shader.getUniform("ScreenSize")?.set(terrainColorWidth.toFloat(), terrainColorHeight.toFloat())
    }

    private fun setUniform(uniform: Uniform, value: CooUniformValue) {
        when (value) {
            is CooUniformValue.FloatValue -> uniform.set(value.value)
            is CooUniformValue.IntValue -> uniform.set(value.value)
            is CooUniformValue.Vec2Value -> uniform.set(value.x, value.y)
            is CooUniformValue.Vec3Value -> uniform.set(value.x, value.y, value.z)
            is CooUniformValue.Vec4Value -> uniform.set(value.x, value.y, value.z, value.w)
        }
    }

    internal fun alphaCutoff(baseLayer: RenderType): Float {
        return when {
            baseLayer === RenderType.cutoutMipped() -> alphaCutoff(CooTerrainLayer.CUTOUT_MIPPED)
            baseLayer === RenderType.cutout() || baseLayer === RenderType.tripwire() -> {
                alphaCutoff(CooTerrainLayer.CUTOUT)
            }
            else -> alphaCutoff(CooTerrainLayer.SOLID)
        }
    }

    internal fun alphaCutoff(layer: CooTerrainLayer): Float {
        return when (layer) {
            CooTerrainLayer.CUTOUT_MIPPED,
            CooTerrainLayer.CUTOUT -> 0.1F
            CooTerrainLayer.SOLID,
            CooTerrainLayer.TRANSLUCENT,
            CooTerrainLayer.INHERIT -> 0F
        }
    }

    private fun terrainEffectGroups(pos: BlockPos): List<CooResolvedTerrainEffectGroup> {
        val level = Minecraft.getInstance().level ?: return emptyList()
        return CooTerrainEffectRegistry.groupsAt(
            level.dimension().location(),
            pos,
            level.gameTime
        )
    }

    private fun currentGameTime(): Long = Minecraft.getInstance().level?.gameTime ?: 0L

    private fun worldPostAttachments(
        compiled: cn.coostack.cooparticlesapi.renderer.pipeline.CooCompiledPipeline
    ): List<CooCompiledAttachment> {
        val nodes = compiled.nodes.associateBy { it.name }
        return compiled.attachments.filter { attachment ->
            nodes.getValue(attachment.output.node).kind == CooPipelineNodeKind.WORLD
        }
    }

    private fun withPostCaptureInputs(context: RenderFrameContext, capture: () -> Boolean): Boolean {
        terrainSceneResources = context.sceneResources
        terrainColorTextureId = context.sceneColorTextureId
            ?: context.sceneResources[RenderSceneTargets.SCENE_COLOR]?.colorTextureId
        terrainDepthTextureId = context.sceneDepthTextureId
            ?: context.sceneResources[RenderSceneTargets.SCENE_DEPTH]?.depthTextureId
        terrainColorWidth = context.targetWidth?.coerceAtLeast(1) ?: terrainColorWidth
        terrainColorHeight = context.targetHeight?.coerceAtLeast(1) ?: terrainColorHeight
        return try {
            capture()
        } catch (error: RuntimeException) {
            false
        } finally {
            terrainColorTextureId = null
            terrainDepthTextureId = null
            terrainSceneResources = RenderSceneResources.empty()
        }
    }

    private fun warnPostCaptureFailure(pipeline: CooRenderPipeline<BlockState>) {
        if (warnedPostCaptureFailure) return
        warnedPostCaptureFailure = true
        CooParticlesConstants.logger.error(
            "Terrain pipeline {} post graph could not capture its section batch; vanilla terrain remains visible",
            pipeline.id
        )
    }

    private fun resolveNamedColorTexture(target: ResourceLocation, attachment: Int): Int? {
        return terrainSceneResources[target]?.colorTextureId(attachment)
            ?: OpenGlPostEffectExecutionBackend.resolveNamedColorTexture(target, attachment)
    }

    private fun attachIrisTerrainDepth(framebuffer: Int, depth: IrisTerrainDepthTexture): Boolean {
        if (framebuffer <= 0 || depth.width != terrainColorWidth || depth.height != terrainColorHeight) return false
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer)
        val depthAttachment = readFramebufferAttachment(GL_DEPTH_ATTACHMENT)
        detachFramebufferAttachment(GL_DEPTH_ATTACHMENT, depthAttachment.type)
        glFramebufferTexture2D(
            GL_DRAW_FRAMEBUFFER,
            GL_DEPTH_ATTACHMENT,
            GL_TEXTURE_2D,
            depth.textureId,
            0
        )
        if (glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            detachFramebufferAttachment(GL_DEPTH_ATTACHMENT, GL_TEXTURE)
            restoreFramebufferAttachment(GL_DEPTH_ATTACHMENT, depthAttachment)
            return false
        }
        irisDepthAttachmentRestore = IrisDepthAttachmentRestore(
            framebuffer,
            depthAttachment
        )
        return true
    }

    private fun restoreIrisDepthAttachment() {
        val restore = irisDepthAttachmentRestore ?: return
        irisDepthAttachmentRestore = null
        val previousFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, restore.framebuffer)
        detachFramebufferAttachment(GL_DEPTH_ATTACHMENT, GL_TEXTURE)
        restoreFramebufferAttachment(GL_DEPTH_ATTACHMENT, restore.depthAttachment)
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previousFramebuffer)
    }

    private fun readFramebufferAttachment(attachment: Int): FramebufferAttachment {
        val type = glGetFramebufferAttachmentParameteri(
            GL_DRAW_FRAMEBUFFER,
            attachment,
            GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
        )
        val name = if (type == GL_NONE) {
            0
        } else {
            glGetFramebufferAttachmentParameteri(
                GL_DRAW_FRAMEBUFFER,
                attachment,
                GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
            )
        }
        return FramebufferAttachment(type, name)
    }

    private fun detachFramebufferAttachment(attachment: Int, type: Int) {
        when (type) {
            GL_RENDERBUFFER -> glFramebufferRenderbuffer(GL_DRAW_FRAMEBUFFER, attachment, GL_RENDERBUFFER, 0)
            GL_TEXTURE -> glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, attachment, GL_TEXTURE_2D, 0, 0)
        }
    }

    private fun restoreFramebufferAttachment(attachment: Int, value: FramebufferAttachment) {
        when (value.type) {
            GL_RENDERBUFFER -> glFramebufferRenderbuffer(
                GL_DRAW_FRAMEBUFFER,
                attachment,
                GL_RENDERBUFFER,
                value.name
            )
            GL_TEXTURE -> glFramebufferTexture2D(
                GL_DRAW_FRAMEBUFFER,
                attachment,
                GL_TEXTURE_2D,
                value.name,
                0
            )
        }
    }

    private fun vanillaShader(baseLayer: RenderType): ShaderInstance {
        return requireNotNull(when (baseLayer) {
            RenderType.cutoutMipped() -> GameRenderer.getRendertypeCutoutMippedShader()
            RenderType.cutout() -> GameRenderer.getRendertypeCutoutShader()
            RenderType.translucent() -> GameRenderer.getRendertypeTranslucentShader()
            RenderType.tripwire() -> GameRenderer.getRendertypeTripwireShader()
            else -> GameRenderer.getRendertypeSolidShader()
        }) { "Vanilla terrain shader is not initialized" }
    }

    private fun resolveBaseLayer(layer: CooTerrainLayer, original: RenderType): RenderType? {
        val resolved = when (layer) {
            CooTerrainLayer.SOLID -> RenderType.solid()
            CooTerrainLayer.CUTOUT_MIPPED -> RenderType.cutoutMipped()
            CooTerrainLayer.CUTOUT -> RenderType.cutout()
            CooTerrainLayer.TRANSLUCENT -> RenderType.translucent()
            CooTerrainLayer.INHERIT -> original
        }
        return resolved.takeIf { candidate ->
            RenderType.chunkBufferLayers().any { it === candidate }
        }
    }

    private fun isIrisShaderPackActive(): Boolean {
        return runCatching { CooParticlesAPIClient.checkIrisShaderPackUsed() }.getOrDefault(false)
    }

    private fun logCompatibilityMode() {
        if (sodiumLoaded) {
            CooParticlesConstants.logger.warn(
                "Sodium is active; terrain pipelines use the Sodium section overlay and the vanilla " +
                    "SectionCompiler overlay path is disabled"
            )
        }
        if (irisShaderPackActive) {
            CooParticlesConstants.logger.warn(
                "An Iris shader pack is active; vanilla terrain remains in gbuffers_terrain and Coo terrain " +
                    "pipelines replace only their bound pixels after Iris final composition. Pipelines declaring " +
                    "SceneColor receive the Iris-processed frame; BaseSampler remains the block atlas"
            )
        }
    }

    private fun requestSectionRebuild() {
        val minecraft = Minecraft.getInstance()
        minecraft.execute {
            if (minecraft.level != null) minecraft.levelRenderer.allChanged()
        }
    }

    private fun requestSectionRebuild(positions: Collection<BlockPos>) {
        if (positions.isEmpty()) return
        val sections = positions
            .map { position ->
                SectionCoordinate(position.x shr 4, position.y shr 4, position.z shr 4)
            }
            .toSet()
        val minecraft = Minecraft.getInstance()
        minecraft.execute {
            if (minecraft.level == null) return@execute
            sections.forEach { section ->
                minecraft.levelRenderer.setSectionDirty(section.x, section.y, section.z)
            }
        }
    }

    private data class SectionCoordinate(val x: Int, val y: Int, val z: Int)

    private fun warnRequiredSamplerFallback(
        pipeline: CooRenderPipeline<BlockState>,
        input: CooPipelineInputPort,
        source: CooPipelineTextureSource
    ) {
        val key = "${pipeline.id}|${input.node}|${input.sampler}|$source"
        if (!warnedRequiredSamplerFallbacks.add(key)) return
        CooParticlesConstants.logger.warn(
            "Terrain pipeline {} required sampler {} from {} is unavailable; binding the block atlas fallback",
            pipeline.id,
            input.sampler,
            source
        )
    }

    @JvmStatic
    fun releaseResources() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall { releaseResourcesOnRenderThread() }
            return
        }
        releaseResourcesOnRenderThread()
    }

    private fun releaseResourcesOnRenderThread() {
        restoreIrisDepthAttachment()
        synchronized(shaders) {
            shaders.values.forEach(ShaderInstance::close)
            shaders.clear()
        }
        failedShaders.clear()
        terrainColorTextureId = null
        terrainDepthTextureId = null
        terrainSceneResources = RenderSceneResources.empty()
        warnedRequiredSamplerFallbacks.clear()
        warnedTargetResolutionFallback = false
        warnedPostCaptureFailure = false
        terrainOverlayDisabled = false
        deferredFrameFinish = null
        framePartialTick = 0F
        synchronized(pendingPostDraws) {
            pendingPostDraws.clear()
        }
        OpenGlPostEffectExecutionBackend.releaseTerrainColorCapture()
    }

    private data class DeferredFrameFinish(
        val tickDelta: Float,
        val viewMatrix: Matrix4f,
        val projectionMatrix: Matrix4f
    )

    private data class FramebufferAttachment(
        val type: Int,
        val name: Int
    )

    private data class IrisDepthAttachmentRestore(
        val framebuffer: Int,
        val depthAttachment: FramebufferAttachment
    )
}
