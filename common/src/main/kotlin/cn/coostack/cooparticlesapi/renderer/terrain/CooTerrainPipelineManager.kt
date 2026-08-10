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
import cn.coostack.cooparticlesapi.renderer.pipeline.CooCompiledPipeline
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineCompiler
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineNodeKind
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelinePostEffectCompiler
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineOutputPort
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineInputPort
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineTextureSource
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelines
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
import java.util.Optional

/**
 * 管理客户端 terrain Pipeline 的 RenderType、shader、帧输入和 section 重建。
 *
 * 区块编译钩子通过 [resolveOverlayRenderType] 登记 Pipeline 与 RenderType 的对应关系；
 * 世界渲染阶段再按 [beginOverlayBatch]、实际 section 绘制、[endOverlayBatch] 的顺序准备并释放
 * SceneColor、SceneDepth 及 Iris 深度 attachment。Manager 还协调 Sodium 专用覆盖层、Iris 最终合成后的
 * 延迟绘制，以及资源重载时的缓存释放。所有直接操作 Minecraft 客户端或 OpenGL 状态的方法都应在客户端
 * 渲染线程调用。
 */
internal object CooTerrainPipelineManager {
    private val terrainLayers = LinkedHashMap<RenderType, RenderType>()
    private val terrainPipelines = LinkedHashMap<RenderType, CooRenderPipeline<BlockState>>()
    /** 保存每个地形 RenderType 首次解析到的方块状态，用于求值动态 uniform。 */
    private val terrainSubjects = LinkedHashMap<RenderType, BlockState>()
    private val pendingPostDraws = LinkedHashMap<RenderType, Runnable>()
    /** Iris 最终合成完成前不能执行的原版 section 覆盖绘制。 */
    private val deferredVanillaDraws = ArrayList<DeferredVanillaDraw>()
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
    /** Iris 的地形目标在当前帧尚未准备完成。 */
    private var irisTerrainUnavailableThisFrame = false
    private var terrainColorTextureId: Int? = null
    private var terrainDepthTextureId: Int? = null
    private var terrainSceneResources = RenderSceneResources.empty()
    private var terrainColorWidth = 1
    private var terrainColorHeight = 1
    private var irisDepthAttachmentRestore: IrisDepthAttachmentRestore? = null
    private val warnedRequiredSamplerFallbacks = linkedSetOf<String>()
    private var warnedTargetResolutionFallback = false
    private var warnedPostCaptureFailure = false
    /** 避免 Iris 目标过渡期间逐帧重复记录同一条警告。 */
    private var warnedIrisTerrainUnavailable = false
    private var deferredFrameFinish: DeferredFrameFinish? = null
    private var framePartialTick = 0F
    /** 当前是否正在重放地形几何以捕获 Pipeline WORLD 节点 attachment。 */
    private var terrainAttachmentCaptureActive = false

    /** 当前是否正在 Iris 最终合成结果上绘制地形覆盖层。 */
    private var finalCompositeTerrainOverlayActive = false
    @Volatile
    /** 是否需要在下一帧入口触发一次完整 section 重建。 */
    private var fullSectionRebuildPending = false

    /**
     * 注册 Pipeline 变更和 shader reload 监听，并读取初始兼容状态。
     *
     * 可重复调用；完成首次初始化后后续调用不会重复注册监听器。
     */
    @JvmStatic
    fun initialize() {
        if (initialized) return
        initialized = true
        sodiumLoaded = CooParticlesServices.PLATFORM.isModLoaded("sodium")
        irisShaderPackActive = isIrisShaderPackActive()
        logCompatibilityMode()
        CooBlockPipelines.addChangeListener { onBlockBindingsChanged() }
        ShaderReloadBus.register { signal ->
            if (signal is ShaderReloadSignal.FullReload) {
                releaseResources()
                requestSectionRebuild()
            }
            null
        }
    }

    /**
     * 在帧开始时刷新 Sodium 和 Iris shader pack 状态。
     *
     * 兼容状态发生变化时会恢复覆盖层并请求一次完整 section 重建。
     */
    @JvmStatic
    fun updateCompatibilityState() {
        initialize()
        val currentSodium = CooParticlesServices.PLATFORM.isModLoaded("sodium")
        val currentIrisShaderPack = isIrisShaderPackActive()
        if (currentSodium == sodiumLoaded && currentIrisShaderPack == irisShaderPackActive) return
        sodiumLoaded = currentSodium
        irisShaderPackActive = currentIrisShaderPack
        terrainOverlayDisabled = false
        irisTerrainUnavailableThisFrame = false
        warnedIrisTerrainUnavailable = false
        requestSectionRebuild()
        logCompatibilityMode()
    }

    /**
     * 开始一帧地形效果处理。
     *
     * 该入口清理到期组、提交待执行的 section 重建，并清空上一帧未消费的绘制记录。
     *
     * @param partialTick 当前帧的部分 tick，超出 `0.0F..1.0F` 时会被限制
     */
    @JvmStatic
    fun beginRenderFrame(partialTick: Float) {
        irisTerrainUnavailableThisFrame = false
        flushPendingFullSectionRebuild()
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
        synchronized(deferredVanillaDraws) {
            deferredVanillaDraws.clear()
        }
    }

    /**
     * 记录某个 terrain RenderType 的可重放绘制，用于捕获世界节点 attachment 或执行后处理图。
     *
     * 没有非 WORLD 节点且不输出 attachment 的 Pipeline 不会保存回调。
     *
     * @param renderType 已由本 Manager 登记的地形 RenderType
     * @param render 可在当前帧重新提交该 section 层的绘制回调
     */
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

    /**
     * 把本帧记录的地形后处理图编译为统一的渲染效果描述。
     *
     * @param context 当前帧可用的场景纹理、目标尺寸和相机数据
     * @param collector 接收编译后地形效果实例的收集器
     */
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
                entries.firstNotNullOfOrNull { (renderType, _) -> terrainSubjects[renderType] }
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
                        terrainAttachmentCaptureActive = true
                        try {
                            entries.forEach { (_, render) -> render.run() }
                        } finally {
                            terrainAttachmentCaptureActive = false
                        }
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

    /**
     * 为一个方块位置解析需要追加绘制的地形 RenderType。
     *
     * 动态效果组优先于静态 [CooBlockPipelines] 绑定。默认 Pipeline、缺少 terrain shader、
     * 不支持的原版层或覆盖层已降级时均返回 `null`，让调用方只保留原版几何。
     *
     * @param state 当前方块状态
     * @param original 方块原本使用的 terrain RenderType
     * @param pos 当前方块的世界坐标
     * @return 需要额外编译的地形 RenderType；无需覆盖绘制时返回 `null`
     */
    @JvmStatic
    fun resolveOverlayRenderType(state: BlockState, original: RenderType, pos: BlockPos): RenderType? {
        initialize()
        if (terrainOverlayDisabled) return null
        val group = terrainEffectGroups(pos).firstOrNull()
        val pipeline = group?.pipeline ?: CooBlockPipelines.resolve(state)
        if (pipeline === CooPipelines.BLOCK_DEFAULT) return null
        if (pipeline.terrainShader == null) return null
        val baseLayer = resolveBaseLayer(pipeline.terrainLayer, original) ?: run {
            CooParticlesConstants.logger.warn(
                "Terrain pipeline {} uses unsupported base layer {}; keeping only the vanilla terrain draw",
                pipeline.id,
                original
            )
            return null
        }
        val batchKey = group?.snapshot?.let { snapshot ->
            CooTerrainEffectBatchKey(snapshot.dimension, snapshot.id)
        } ?: pipeline
        val renderType = CooParticlesServices.PLATFORM.getRenderTypesProvider().terrain(
            pipeline,
            baseLayer,
            batchKey
        )
        synchronized(terrainLayers) {
            terrainLayers[renderType] = baseLayer
            terrainPipelines[renderType] = pipeline
            terrainSubjects.putIfAbsent(renderType, state)
        }
        return renderType
    }

    /**
     * 为没有方块坐标上下文的旧调用路径解析静态地形 RenderType。
     *
     * 此重载使用 [BlockPos.ZERO]，因此不会匹配按位置同步的动态效果组。
     *
     * @param state 当前方块状态
     * @param original 方块原本使用的 terrain RenderType
     * @return 静态 Pipeline 对应的覆盖 RenderType；无需覆盖时返回 `null`
     */
    @JvmStatic
    fun resolveOverlayRenderType(state: BlockState, original: RenderType): RenderType? {
        return resolveOverlayRenderType(state, original, BlockPos.ZERO)
    }

    /**
     * 为地形覆盖层包装顶点消费者，使其写入 EffectUV 和位置生效 tick。
     *
     * @param renderType 当前正在编译的 RenderType
     * @param pos 方块世界坐标
     * @param consumer 原区块编译顶点消费者
     * @return 已登记地形层对应的包装器；普通 RenderType 原样返回 [consumer]
     */
    @JvmStatic
    fun decorateVertexConsumer(renderType: RenderType, pos: BlockPos, consumer: VertexConsumer): VertexConsumer {
        val pipeline = synchronized(terrainLayers) { terrainPipelines[renderType] } ?: return consumer
        return CooEffectUvVertexConsumer(consumer, pipeline.effectUvMode, pos, activationAt(renderType, pos))
    }

    /**
     * 查询当前地形批次中某位置的绝对生效 tick。
     *
     * @param renderType 已登记的地形 RenderType
     * @param pos 方块世界坐标
     * @return 匹配动态组的生效 tick；静态 Pipeline 或无客户端世界时返回 `0`
     */
    @JvmStatic
    fun activationAt(renderType: RenderType, pos: BlockPos): Long {
        val pipeline = synchronized(terrainLayers) { terrainPipelines[renderType] } ?: return 0L
        val level = Minecraft.getInstance().level
        return level?.let {
            CooTerrainEffectRegistry.activationAt(
                it.dimension().location(),
                pos,
                pipeline,
                it.gameTime
            )
        } ?: 0L
    }

    /**
     * 为地形覆盖 RenderType选择扩展方块顶点格式。
     *
     * @param renderType 待查询 RenderType
     * @param original 原顶点格式
     * @return 地形覆盖层使用 [CooTerrainVertexFormats.BLOCK_EFFECT]，其他层返回 [original]
     */
    @JvmStatic
    fun vertexFormat(renderType: RenderType, original: VertexFormat): VertexFormat {
        return if (isTerrainRenderType(renderType)) CooTerrainVertexFormats.BLOCK_EFFECT else original
    }

    /**
     * 判断 RenderType 是否由本 Manager 创建并登记为地形覆盖层。
     *
     * @param renderType 待查询 RenderType
     * @return 已登记时返回 `true`
     */
    @JvmStatic
    fun isTerrainRenderType(renderType: RenderType): Boolean {
        return synchronized(terrainLayers) { renderType in terrainLayers }
    }

    /**
     * 查询地形 RenderType 对应的 Pipeline。
     *
     * @param renderType 已登记的地形 RenderType
     * @return 对应 Pipeline；未登记时返回 `null`
     */
    @JvmStatic
    fun pipelineFor(renderType: RenderType): CooRenderPipeline<BlockState>? {
        return synchronized(terrainLayers) { terrainPipelines[renderType] }
    }

    /** @return 当前是否应由 Sodium 专用路径编译和绘制地形覆盖层。 */
    @JvmStatic
    fun usesSodiumTerrainOverlay(): Boolean {
        return sodiumLoaded
    }

    /** @return Sodium 已加载且地形覆盖层未因运行时错误降级时返回 `true`。 */
    @JvmStatic
    fun isSodiumTerrainOverlayEnabled(): Boolean {
        return sodiumLoaded && !terrainOverlayDisabled
    }

    /** @return 地形覆盖层未因运行时错误降级时返回 `true`。 */
    @JvmStatic
    fun isTerrainOverlayEnabled(): Boolean {
        return !terrainOverlayDisabled
    }

    /**
     * 判断区块编译是否必须保留原版 terrain 几何。
     *
     * @return 覆盖层已降级或 Iris shader pack 激活时返回 `true`
     */
    @JvmStatic
    fun shouldPreserveVanillaTerrainGeometry(): Boolean {
        return terrainOverlayDisabled || irisShaderPackActive
    }

    /**
     * 判断原版区块覆盖绘制是否应推迟到 Iris 最终合成之后。
     *
     * @return 非 Sodium 路径且 Iris shader pack 激活时返回 `true`
     */
    @JvmStatic
    fun shouldDeferVanillaTerrainOverlay(): Boolean {
        return !sodiumLoaded && irisShaderPackActive && !terrainOverlayDisabled
    }

    /**
     * 保存一次原版 section 覆盖绘制，等待 Iris 最终合成阶段重放。
     *
     * @param renderType 要重放的地形 RenderType
     * @param draw 提交对应 section 层的绘制回调
     */
    @JvmStatic
    fun deferVanillaTerrainOverlay(renderType: RenderType, draw: Runnable) {
        synchronized(deferredVanillaDraws) {
            deferredVanillaDraws += DeferredVanillaDraw(renderType, draw)
        }
    }

    /**
     * 在 Iris 最终合成后执行并清空所有延迟的原版地形覆盖绘制。
     *
     * 方法会保存和恢复调用方 GL 状态；不满足 Iris 非 Sodium 路径时仅丢弃本帧队列。
     */
    @JvmStatic
    fun flushDeferredVanillaTerrainOverlays() {
        val draws = synchronized(deferredVanillaDraws) {
            deferredVanillaDraws.toList().also { deferredVanillaDraws.clear() }
        }
        if (draws.isEmpty() || sodiumLoaded || !irisShaderPackActive || terrainOverlayDisabled) return
        beginFinalCompositeTerrainOverlay()
        try {
            OpenGlPostEffectExecutionBackend.withPreservedGlState {
                if (!beginOverlayBatch(draws.map(DeferredVanillaDraw::renderType).distinct())) {
                    return@withPreservedGlState
                }
                try {
                    if (terrainOverlayDisabled) return@withPreservedGlState
                    draws.forEach { deferred ->
                        deferred.draw.run()
                        recordPostDraw(deferred.renderType, deferred.draw)
                    }
                } catch (error: RuntimeException) {
                    handleSodiumOverlayFailure("deferred vanilla section draw", draws.first().renderType, error)
                } finally {
                    endOverlayBatch()
                }
            }
        } finally {
            endFinalCompositeTerrainOverlay()
        }
    }

    /**
     * 在 Iris 模式下保存原本的帧结束调用，等待地形覆盖绘制完成后执行。
     *
     * 输入矩阵会立即复制，调用方后续修改原对象不会影响延迟任务。
     *
     * @param tickDelta 当前帧部分 tick
     * @param viewMatrix 当前视图矩阵
     * @param projectionMatrix 当前投影矩阵
     * @return 已接管帧结束调用时返回 `true`；无需延迟时返回 `false`
     */
    @JvmStatic
    fun deferFrameFinish(tickDelta: Float, viewMatrix: Matrix4f, projectionMatrix: Matrix4f): Boolean {
        if (!irisShaderPackActive || terrainOverlayDisabled) return false
        deferredFrameFinish = DeferredFrameFinish(
            tickDelta,
            Matrix4f(viewMatrix),
            Matrix4f(projectionMatrix)
        )
        return true
    }

    /** 执行并清除先前由 [deferFrameFinish] 保存的帧结束调用。 */
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

    /**
     * 查询地形覆盖层是否需要按相机距离排序后上传。
     *
     * @param renderType 已登记的地形 RenderType
     * @return 其原版基础层启用上传排序时返回 `true`
     */
    @JvmStatic
    fun requiresTerrainSorting(renderType: RenderType): Boolean {
        return synchronized(terrainLayers) { terrainLayers[renderType]?.sortOnUpload() == true }
    }

    /**
     * 查询建立在指定原版 terrain layer 上的全部覆盖 RenderType。
     *
     * @param baseLayer 原版 terrain RenderType
     * @return 当前已登记且使用该基础层的覆盖 RenderType
     */
    @JvmStatic
    fun layersFor(baseLayer: RenderType): List<RenderType> {
        return synchronized(terrainLayers) {
            terrainLayers.filterValues { it === baseLayer }.keys.toList()
        }
    }

    /** @return 当前是否正在为地形 Pipeline 捕获世界节点 attachment。 */
    @JvmStatic
    fun isTerrainAttachmentCaptureActive(): Boolean {
        return terrainAttachmentCaptureActive
    }

    /** 标记后续地形覆盖绘制发生在 Iris 最终合成阶段。 */
    @JvmStatic
    fun beginFinalCompositeTerrainOverlay() {
        finalCompositeTerrainOverlayActive = true
    }

    /** 结束 Iris 最终合成地形覆盖阶段。 */
    @JvmStatic
    fun endFinalCompositeTerrainOverlay() {
        finalCompositeTerrainOverlayActive = false
    }

    /** @return 当前是否处于 Iris 最终合成地形覆盖阶段。 */
    @JvmStatic
    fun isFinalCompositeTerrainOverlayActive(): Boolean {
        return finalCompositeTerrainOverlayActive
    }

    /**
     * 为一批地形覆盖绘制解析场景纹理，并在需要时临时接入 Iris 地形深度。
     *
     * 返回 `true` 时必须与 [endOverlayBatch] 成对调用。Iris 目标仍在切换时返回 `false`，
     * 调用方只跳过当前批次，下一帧会重新解析。
     *
     * @param renderTypes 本批次将要绘制的地形 RenderType
     * @return 当前批次的场景目标和深度是否可用
     */
    @JvmStatic
    fun beginOverlayBatch(renderTypes: List<RenderType>): Boolean {
        if (irisTerrainUnavailableThisFrame) return false
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
                    markIrisTerrainUnavailable(
                        "Iris terrain depth attachment",
                        renderTypes.firstOrNull(),
                        IllegalStateException("Iris terrain depth texture is unavailable or incompatible")
                    )
                    endOverlayBatch()
                    return false
                }
                warnedIrisTerrainUnavailable = false
            }
        } catch (error: RuntimeException) {
            if (irisShaderPackActive) {
                markIrisTerrainUnavailable(
                    "Iris terrain target resolution",
                    renderTypes.firstOrNull(),
                    error
                )
                endOverlayBatch()
                return false
            }
            if (!warnedTargetResolutionFallback) {
                warnedTargetResolutionFallback = true
                CooParticlesConstants.logger.error(
                    "Terrain target resolution failed; explicit scene inputs use their configured fallback",
                    error
                )
            }
        }
        return true
    }

    /**
     * 结束当前地形覆盖批次，恢复 Iris 深度 attachment 并清除临时场景输入。
     */
    @JvmStatic
    fun endOverlayBatch() {
        restoreIrisDepthAttachment()
        terrainColorTextureId = null
        terrainDepthTextureId = null
        terrainSceneResources = RenderSceneResources.empty()
    }

    /**
     * 处理原版 section 覆盖路径中的绘制异常。
     *
     * @param renderType 失败的地形 RenderType
     * @param error 原始运行时异常
     * @return 始终返回 `true`，供 Mixin 回调直接取消自定义覆盖路径
     */
    @JvmStatic
    fun handleOverlayDrawFailure(renderType: RenderType, error: RuntimeException): Boolean {
        return handleSodiumOverlayFailure("vanilla section draw", renderType, error)
    }

    /**
     * 统一处理 Sodium、Iris 或原版覆盖路径的异常。
     *
     * 此入口只处理 shader 编译和实际绘制等确定性错误。Iris 目标尚未准备完成由
     * [markIrisTerrainUnavailable] 跳过当前帧，不会永久禁用覆盖层。
     *
     * @param phase 发生异常的渲染阶段名称，用于日志定位
     * @param renderType 相关地形 RenderType；无法确定时可为 `null`
     * @param error 原始运行时异常
     * @return 始终返回 `true`，表示异常已按降级策略处理
     */
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

    /** 记录 Iris 目标过渡状态；调用方会跳过当前批次。 */
    private fun markIrisTerrainUnavailable(
        phase: String,
        renderType: RenderType?,
        error: RuntimeException
    ) {
        restoreIrisDepthAttachment()
        irisTerrainUnavailableThisFrame = true
        if (!warnedIrisTerrainUnavailable) {
            warnedIrisTerrainUnavailable = true
            CooParticlesConstants.logger.warn(
                "Terrain overlay inputs are not ready during {} for {}; skipping this frame and retrying next frame",
                phase,
                renderType ?: "unknown render type",
                error
            )
        }
    }

    /** 创建或复用 Pipeline shader，并为当前方块状态绑定纹理和 uniform。 */
    private fun shaderFor(
        pipeline: CooRenderPipeline<BlockState>,
        baseLayer: RenderType,
        subject: BlockState
    ): ShaderInstance? {
        val shaderId = requireNotNull(pipeline.terrainShader)
        val descriptor = buildGeneratedDescriptor(shaderId, pipeline)
        val cacheKey = CooTerrainShaderCacheKey(shaderId, descriptor)
        val shader = synchronized(shaders) {
                shaders[cacheKey] ?: createShader(cacheKey)?.also { shaders[cacheKey] = it }
        } ?: return null
        bindInputs(shader, pipeline)
        bindUniforms(shader, pipeline, baseLayer, subject)
        return shader
    }

    /**
     * 获取并配置当前地形 RenderType 使用的 shader。
     *
     * 方法会复用按 shader ID 和生成描述缓存的 [ShaderInstance]，随后绑定本批次纹理及 uniform。
     * shader 创建失败时会触发地形覆盖降级。
     *
     * @param renderType 已登记的地形 RenderType
     * @param baseLayer 原版基础 terrain layer，用于计算 alpha cutoff
     * @return 已配置 shader；RenderType 未登记或创建失败时返回 `null`
     */
    @JvmStatic
    fun shaderFor(renderType: RenderType, baseLayer: RenderType): ShaderInstance? {
        val batch = synchronized(terrainLayers) {
            val pipeline = terrainPipelines[renderType] ?: return@synchronized null
            pipeline to (terrainSubjects[renderType] ?: Blocks.AIR.defaultBlockState())
        } ?: return null
        return shaderFor(batch.first, baseLayer, batch.second)
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
                    "Failed to load terrain shader {}; disabling the custom terrain overlay and rebuilding vanilla geometry",
                    cacheKey.shaderId,
                    error
                )
                handleSodiumOverlayFailure("terrain shader creation", null, RuntimeException(error))
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

    /**
     * 根据地形 Pipeline 的 WORLD 节点生成 Minecraft core shader 描述。
     *
     * @param shaderId Pipeline 声明的 fragment shader ID
     * @param pipeline 提供 sampler 和 uniform 声明的方块 Pipeline
     * @return 可由 [ShaderInstance] 读取的 JSON 描述文本
     */
    internal fun buildGeneratedDescriptor(
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
        return when (value) {
            is CooUniformValue.FloatValue -> floatUniform(name)
            is CooUniformValue.IntValue -> intUniform(name)
            is CooUniformValue.Vec2Value -> floatUniform(name, 2)
            is CooUniformValue.Vec3Value -> floatUniform(name, 3)
            is CooUniformValue.Vec4Value -> floatUniform(name, 4)
        }
    }

    private fun bindInputs(shader: ShaderInstance, pipeline: CooRenderPipeline<BlockState>) {
        val minecraft = Minecraft.getInstance()
        val atlas = minecraft.textureManager.getTexture(TextureAtlas.LOCATION_BLOCKS).id
        val worldNode = pipeline.nodes.firstOrNull { it.kind == CooPipelineNodeKind.WORLD } ?: return
        pipeline.lines.forEach { line ->
            val input = line.input as? CooPipelineInputPort
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
        baseLayer: RenderType,
        subject: BlockState
    ) {
        val camera = Minecraft.getInstance().gameRenderer.mainCamera.position
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

    /**
     * 按原版 terrain RenderType 查询片元 alpha 丢弃阈值。
     *
     * @param baseLayer 原版基础 terrain layer
     * @return cutout 类层为 `0.1F`，其他层为 `0.0F`
     */
    internal fun alphaCutoff(baseLayer: RenderType): Float {
        return when {
            baseLayer === RenderType.cutoutMipped() -> alphaCutoff(CooTerrainLayer.CUTOUT_MIPPED)
            baseLayer === RenderType.cutout() || baseLayer === RenderType.tripwire() -> {
                alphaCutoff(CooTerrainLayer.CUTOUT)
            }
            else -> alphaCutoff(CooTerrainLayer.SOLID)
        }
    }

    /**
     * 按公开地形层枚举查询片元 alpha 丢弃阈值。
     *
     * @param layer Pipeline 声明的地形层
     * @return [CooTerrainLayer.CUTOUT] 和 [CooTerrainLayer.CUTOUT_MIPPED] 为 `0.1F`，其他层为 `0.0F`
     */
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
        compiled: CooCompiledPipeline
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
        fullSectionRebuildPending = true
    }

    /** 方块绑定变化时解除旧的降级状态并重建所有 section。 */
    private fun onBlockBindingsChanged() {
        terrainOverlayDisabled = false
        irisTerrainUnavailableThisFrame = false
        warnedIrisTerrainUnavailable = false
        requestSectionRebuild()
    }

    /** 在客户端帧入口执行一次待处理的完整 section 重建。 */
    private fun flushPendingFullSectionRebuild() {
        if (!fullSectionRebuildPending) return
        fullSectionRebuildPending = false
        val minecraft = Minecraft.getInstance()
        if (minecraft.level != null) minecraft.levelRenderer.allChanged()
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

    /**
     * 释放地形 shader、RenderType 缓存、临时 attachment 和本帧绘制状态。
     *
     * 可从任意客户端线程调用；非渲染线程调用会通过 RenderSystem 排队执行实际释放。
     */
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
        synchronized(terrainLayers) {
            terrainLayers.clear()
            terrainPipelines.clear()
            terrainSubjects.clear()
        }
        CooParticlesServices.PLATFORM.getRenderTypesProvider().clearTerrainCache()
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
        warnedIrisTerrainUnavailable = false
        terrainOverlayDisabled = false
        irisTerrainUnavailableThisFrame = false
        deferredFrameFinish = null
        framePartialTick = 0F
        terrainAttachmentCaptureActive = false
        finalCompositeTerrainOverlayActive = false
        terrainEffectRevision = -1L
        synchronized(pendingPostDraws) {
            pendingPostDraws.clear()
        }
        synchronized(deferredVanillaDraws) {
            deferredVanillaDraws.clear()
        }
        OpenGlPostEffectExecutionBackend.releaseTerrainColorCapture()
    }

}
