package cn.coostack.cooparticlesapi.renderer.terrain.sodium

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.compat.IrisCompat
import cn.coostack.cooparticlesapi.compat.IrisShadowPassState
import cn.coostack.cooparticlesapi.renderer.terrain.CooEffectUvResolver
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainPipelineManager
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainVertexFormats
import cn.coostack.cooparticlesapi.renderer.pipeline.CooTerrainLayer
import cn.coostack.cooparticlesapi.renderer.backend.OpenGlRenderTargetBridge
import cn.coostack.cooparticlesapi.renderer.backend.bindWrite
import cn.coostack.cooparticlesapi.renderer.post.OpenGlPostEffectExecutionBackend
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import com.mojang.renderpearl.backend.opengl.GlStateManager
import com.mojang.blaze3d.vertex.VertexSorting
import net.caffeinemc.mods.sodium.api.util.ColorARGB
import net.caffeinemc.mods.sodium.api.texture.SpriteUtil
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion
import net.caffeinemc.mods.sodium.client.render.chunk.compile.BuilderTaskOutput
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionInfo
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl
import net.caffeinemc.mods.sodium.client.render.texture.SpriteFinderCache
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.oit.OitStage
import net.minecraft.client.renderer.chunk.ChunkSectionLayer
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.core.BlockPos
import org.joml.Matrix4f
import java.util.IdentityHashMap
import org.lwjgl.opengl.GL11.GL_DEPTH_FUNC
import org.lwjgl.opengl.GL11.GL_DEPTH_TEST
import org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK
import org.lwjgl.opengl.GL11.GL_GEQUAL
import org.lwjgl.opengl.GL11.GL_VIEWPORT
import org.lwjgl.opengl.GL11.glGetBoolean
import org.lwjgl.opengl.GL11.glGetIntegerv
import org.lwjgl.opengl.GL11.glIsEnabled
import org.lwjgl.opengl.GL11.glViewport
import org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER
import org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING
import org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER
import org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING
import org.lwjgl.opengl.GL30.glGetInteger
import org.lwjgl.opengl.GL33.glEnable
import org.lwjgl.opengl.GL33.glDisable
import org.lwjgl.opengl.GL33.glDepthMask
import org.lwjgl.opengl.GL33.glDepthFunc

/**
 * 管理 Sodium 0.9.2 的 section 级地形覆盖构建、GPU 上传和 OpenGL 3.3 绘制。
 *
 * 区块工作线程按 [beginBuild]、多次 [captureQuad]、[markRenderPasses]、[finishBuild] 的顺序生成
 * CPU 批次；渲染线程通过 [uploadResults] 将批次绑定到 RenderSection，再由 [renderOrDefer] 绘制。
 * Iris shader pack 激活时绘制会暂存到 [flushDeferred]，等最终合成结束后使用 Iris 深度重放。
 * 所有 CPU 和 GPU 资源分别跟随 ChunkBuildOutput 与 RenderSection 生命周期释放。
 */
internal object CooSodiumTerrainOverlay {
    private val activeBuild = ThreadLocal<BuildCollector?>()
    private val pending = CooIdentityResourceStore<ChunkBuildOutput, BuiltBatch>()
    private val uploaded = CooIdentityResourceStore<RenderSection, UploadedBatch>()
    /** 让重载与工作线程结果登记成为同一个所有权边界。 */
    private val buildLock = Any()
    /** 清理资源后拒绝接收先前工作线程的结果。 */
    @Volatile private var buildGeneration = 0L
    /** 26.3 RenderRegion 不再公开 getSection；记录已上传区段的原版 region 身份与局部索引。 */
    private val regionSections = IdentityHashMap<RenderRegion, MutableMap<Int, RenderSection>>()
    private val deferredDraws = ArrayList<DeferredDraw>()
    /** 世界绘制调用的递增帧标记，避免上帧异常退出后的延迟项跨帧重放。 */
    private var frameEpoch = 0L

    /** 世界绘制开始时清理上帧尚未消费的项，不影响已上传区段网格。 */
    @JvmStatic
    fun beginFrame() {
        synchronized(deferredDraws) {
            frameEpoch++
            deferredDraws.clear()
        }
    }

    /**
     * 把 Sodium terrain pass 还原为对应的原版 RenderType。
     *
     * @param original 方块面的原版区段层，用于未知 Sodium pass 的保守映射
     * @param material Sodium 为当前方块面选择的材质
     * @param pass 当前 Sodium terrain pass
     * @return 与 pass 和材质 mipmap 规则对应的原版 terrain layer
     */
    @JvmStatic
    fun resolveBaseLayer(original: ChunkSectionLayer, material: Material, pass: TerrainRenderPass): RenderType {
        val layer = when (pass) {
            DefaultTerrainRenderPasses.SOLID -> CooTerrainLayer.SOLID
            DefaultTerrainRenderPasses.CUTOUT -> {
                if (material.mipped) CooTerrainLayer.CUTOUT_MIPPED else CooTerrainLayer.CUTOUT
            }
            DefaultTerrainRenderPasses.TRANSLUCENT -> CooTerrainLayer.TRANSLUCENT
            else -> when (original) {
                ChunkSectionLayer.SOLID -> CooTerrainLayer.SOLID
                ChunkSectionLayer.CUTOUT -> if (material.mipped) CooTerrainLayer.CUTOUT_MIPPED else CooTerrainLayer.CUTOUT
                ChunkSectionLayer.TRANSLUCENT -> CooTerrainLayer.TRANSLUCENT
            }
        }
        return CooTerrainPipelineManager.baseLayer(layer)
    }

    /**
     * 开始当前区块构建线程的一次地形覆盖收集。
     *
     * 同一线程上未结束的旧收集器会先被关闭，避免异常构建遗留本地缓冲。
     */
    @JvmStatic
    fun beginBuild() {
        activeBuild.get()?.close()
        activeBuild.set(BuildCollector(buildGeneration))
    }

    /**
     * 把一个已解析为地形 Pipeline 的 Sodium 方块面复制到扩展顶点缓冲。
     *
     * 方法保留原 UV、颜色、AO、光照和法线，并额外写入 EffectUV 与绝对生效 tick。
     * 未处于构建阶段或 RenderType 未登记时直接忽略。
     *
     * @param renderType 当前方块面解析到的 Coo 地形 RenderType
     * @param pass 当前 Sodium terrain pass
     * @param blockPos 当前方块的世界坐标
     * @param quad Sodium 方块面及其逐顶点法线、sprite 数据
     * @param vertices Sodium 编码前的四个区块顶点
     */
    @JvmStatic
    fun captureQuad(
        renderType: RenderType,
        pass: TerrainRenderPass,
        blockPos: BlockPos,
        quad: MutableQuadViewImpl,
        vertices: Array<ChunkVertexEncoder.Vertex>
    ) {
        val pipeline = CooTerrainPipelineManager.pipelineFor(renderType) ?: return
        val collector = activeBuild.get() ?: return
        val faceNormal = quad.faceNormal()
        val sprite = quad.sprite(SpriteFinderCache.forBlockAtlas())
        val activatedAt = CooTerrainPipelineManager.activationAt(renderType, blockPos)
        collector.append(renderType, pass, sprite) { builder ->
            vertices.forEachIndexed { index, vertex ->
                val normalX = if (quad.hasNormal(index)) quad.normalX(index) else faceNormal.x()
                val normalY = if (quad.hasNormal(index)) quad.normalY(index) else faceNormal.y()
                val normalZ = if (quad.hasNormal(index)) quad.normalZ(index) else faceNormal.z()
                val effectUv = CooEffectUvResolver.resolve(
                    mode = pipeline.effectUvMode,
                    blockPos = blockPos,
                    x = vertex.x,
                    y = vertex.y,
                    z = vertex.z,
                    baseU = vertex.u,
                    baseV = vertex.v,
                    normalX = normalX,
                    normalY = normalY,
                    normalZ = normalZ
                )
                builder.addVertex(vertex.x, vertex.y, vertex.z)
                    // Sodium 的编码前颜色是 ABGR，原版消费者接受 ARGB，转换后再应用 AO。
                    .setColor(ColorARGB.mulRGB(ColorARGB.fromABGR(vertex.color), vertex.ao))
                    .setUv(vertex.u, vertex.v)
                    .setUv1(CooEffectUvResolver.pack(effectUv.u), CooEffectUvResolver.pack(effectUv.v))
                    .setLight(CooEffectUvResolver.packLightWithActivation(vertex.light, activatedAt))
                    .setNormal(normalX, normalY, normalZ)
            }
        }
    }

    /**
     * 把当前收集器使用的 terrain pass 标记到 Sodium 构建结果。
     *
     * @param renderData 当前 section 的构建元数据
     */
    @JvmStatic
    fun markRenderPasses(renderData: BuiltSectionInfo.Builder) {
        activeBuild.get()?.renderPasses()?.forEach { pass ->
            renderData.addRenderPass(pass)
        }
    }

    /**
     * 结束当前线程的覆盖收集，并把生成批次关联到 ChunkBuildOutput。
     *
     * @param output 成功的 Sodium 区块构建结果；为 `null` 时只释放收集器
     */
    @JvmStatic
    fun finishBuild(output: ChunkBuildOutput?) {
        val collector = activeBuild.get() ?: return
        activeBuild.remove()
        if (output == null) {
            collector.close()
            return
        }
        val batches = collector.build()
        synchronized(buildLock) {
            if (collector.generation == buildGeneration) {
                pending.replace(output, batches, BuiltBatch::close)
            } else {
                batches.forEach(BuiltBatch::close)
            }
        }
    }

    /**
     * 丢弃尚未上传的区块构建结果及其 CPU 顶点缓冲。
     *
     * @param output 被 Sodium 取消或替换的构建结果
     */
    @JvmStatic
    fun discardBuildOutput(output: ChunkBuildOutput) {
        pending.release(output, BuiltBatch::close)
    }

    /**
     * 在渲染线程把一批 Sodium 构建结果上传为 section 级 GPU 顶点缓冲。
     *
     * 同一 RenderSection 的旧覆盖批次会在替换时释放。
     *
     * @param outputs 本轮 Sodium 上传队列，非 ChunkBuildOutput 项会被忽略
     * @throws IllegalStateException 当前不在渲染线程时抛出
     */
    @JvmStatic
    fun uploadResults(outputs: Collection<BuilderTaskOutput>) {
        check(RenderSystem.isOnRenderThread()) { "Sodium terrain overlays must upload on the render thread" }
        outputs.forEach { output ->
            if (output !is ChunkBuildOutput) return@forEach
            val built = pending.take(output).orEmpty()
            val gpuBatches = built.mapNotNull { upload(output.section, it) }
            uploaded.replace(output.section, gpuBatches, ::closeUploaded)
            regionSections.getOrPut(output.section.region) { mutableMapOf() }[output.section.sectionIndex] = output.section
        }
    }

    /**
     * 绘制当前 Sodium pass 的可见地形覆盖，或在 Iris 模式下保存为延迟绘制。
     *
     * 阴影 pass 不绘制覆盖层；无法可靠判断 Iris 阴影状态时会触发原版几何降级。
     *
     * @param renderLists Sodium 已排序的可见 section 列表
     * @param matrices 当前区块渲染矩阵
     * @param pass 当前 terrain pass
     * @param cameraX 相机世界 X 坐标
     * @param cameraY 相机世界 Y 坐标
     * @param cameraZ 相机世界 Z 坐标
     * @param deferToFrameEnd 兼容旧调用方的最终颜色延迟请求；明确提供 OIT 阶段时使用原生阶段
     * @param oitStage 当前已打开的原生 OIT 阶段；null 表示普通颜色绘制
     */
    @JvmStatic
    @JvmOverloads
    fun renderOrDefer(
        renderLists: SortedRenderLists,
        matrices: ChunkRenderMatrices,
        pass: TerrainRenderPass,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        deferToFrameEnd: Boolean = false,
        oitStage: OitStage? = null,
    ) {
        if (!CooTerrainPipelineManager.isSodiumTerrainOverlayEnabled()) return
        val irisComposite = CooTerrainPipelineManager.shouldPreserveVanillaTerrainGeometry()
        if (irisComposite && oitStage != null && oitStage != OitStage.ACCUMULATE) return
        if (irisComposite && IrisCompat.shadowPassState() != IrisShadowPassState.INACTIVE) {
            if (IrisCompat.shadowPassState() == IrisShadowPassState.UNKNOWN) {
                CooTerrainPipelineManager.handleSodiumOverlayFailure(
                    "Iris shadow-pass detection", null,
                    IllegalStateException("Iris 阴影状态不可用"),
                )
            }
            return
        }
        val collectedDrawGroups = collectDrawGroups(renderLists, pass)
        if (collectedDrawGroups.isEmpty()) return
        val drawGroups = LinkedHashMap<RenderType, MutableList<DrawEntry>>()
        collectedDrawGroups.forEach { (renderType, entries) ->
            val visibleEntries = if (!CooTerrainPipelineManager.isMappingRenderType(renderType)) {
                entries
            } else {
                val sectionMaxOffset = 15
                entries.filterTo(ArrayList<DrawEntry>()) { entry ->
                    CooTerrainPipelineManager.isTerrainMappingSectionVisible(
                        renderType,
                        entry.section.originX,
                        entry.section.originY,
                        entry.section.originZ,
                        entry.section.originX + sectionMaxOffset,
                        entry.section.originY + sectionMaxOffset,
                        entry.section.originZ + sectionMaxOffset
                    )
                }
            }
            if (visibleEntries.isNotEmpty()) {
                drawGroups[renderType] = visibleEntries
            }
        }
        if (drawGroups.isEmpty()) return
        val postDrawGroups = LinkedHashMap<RenderType, MutableList<DrawEntry>>()
        val immediateDrawGroups = LinkedHashMap<RenderType, MutableList<DrawEntry>>()
        drawGroups.forEach { (renderType, entries) ->
            if (CooTerrainPipelineManager.isTerrainPostRenderType(renderType)) {
                postDrawGroups[renderType] = entries
            } else {
                immediateDrawGroups[renderType] = entries
            }
        }

        val modelView = Matrix4f(matrices.modelView())
        val projection = Matrix4f(matrices.projection())
        val recordPost = oitStage == null || oitStage == OitStage.ACCUMULATE
        if (recordPost) {
            postDrawGroups.forEach { (renderType, entries) ->
                val snapshot = entries.toList()
                CooTerrainPipelineManager.recordPostDraw(renderType) {
                    drawSafely(
                        renderType,
                        snapshot,
                        modelView,
                        projection,
                        cameraX,
                        cameraY,
                        cameraZ,
                        false
                    )
                }
            }
        }
        if (immediateDrawGroups.isEmpty()) return

        if (!irisComposite && oitStage != null) {
            val nativeGroups = immediateDrawGroups.filterTo(LinkedHashMap()) { (type, _) ->
                CooTerrainPipelineManager.supportsNativeOit(type)
            }
            if (nativeGroups.isNotEmpty()) {
                CooTerrainPipelineManager.withOitStage(oitStage) {
                    renderDrawGroups(
                        nativeGroups, modelView, projection, cameraX, cameraY, cameraZ, false, recordPost,
                    )
                }
            }
            // 任意 MRT 输出等无法包装的用户 shader 仍保留最终颜色路线，避免迁移时丢失整个效果。
            if (oitStage != OitStage.ACCUMULATE) return
            immediateDrawGroups.keys.removeAll(nativeGroups.keys)
            if (immediateDrawGroups.isEmpty()) return
        }
        if (irisComposite || deferToFrameEnd || oitStage != null) {
            synchronized(deferredDraws) {
                deferredDraws += DeferredDraw(
                    immediateDrawGroups.mapValues { (_, entries) -> entries.toList() },
                    modelView,
                    projection,
                    cameraX,
                    cameraY,
                    cameraZ,
                    irisComposite,
                    frameEpoch,
                )
            }
            return
        }
        renderDrawGroups(
            immediateDrawGroups,
            modelView,
            projection,
            cameraX,
            cameraY,
            cameraZ,
            false
        )
    }

    /**
     * 在 OIT 完成后或 Iris 最终合成后绘制本帧延迟覆盖，并移除已经消费的队列项。
     *
     * 原版 OIT 必须在手部渲染清除主深度之前调用；Iris 沿用 GameRenderer 世界渲染返回入口。
     * @param includeIris 是否同时消费 Iris 队列；false 只消费原版 OIT 项
     */
    @JvmStatic
    @JvmOverloads
    fun flushDeferred(includeIris: Boolean = true) {
        val draws = synchronized(deferredDraws) {
            deferredDraws.filter { includeIris || !it.irisComposite }.also { consumed ->
                deferredDraws.removeAll(consumed.toSet())
            }
        }
        if (draws.isEmpty() || !CooTerrainPipelineManager.isSodiumTerrainOverlayEnabled()) return
        val irisActive = CooTerrainPipelineManager.shouldPreserveVanillaTerrainGeometry()
        val compatibleDraws = draws.filter { it.irisComposite == irisActive && it.frameEpoch == frameEpoch }
        if (compatibleDraws.isEmpty()) return
        OpenGlPostEffectExecutionBackend.withPreservedGlState {
            if (!irisActive) {
                // OIT 阶段的 FBO 已完成工作；在手部绘制前重新使用仍保留场景深度的主目标。
                Minecraft.getInstance().gameRenderer.mainRenderTarget().bindWrite(true)
            }
            val renderTypes = compatibleDraws.flatMap { it.drawGroups.keys }.distinct()
            CooTerrainPipelineManager.beginFinalCompositeTerrainOverlay()
            try {
                if (!CooTerrainPipelineManager.beginOverlayBatch(renderTypes)) return@withPreservedGlState
                try {
                    if (!CooTerrainPipelineManager.isSodiumTerrainOverlayEnabled()) return@withPreservedGlState
                    drawLoop@ for (draw in compatibleDraws) {
                        for ((renderType, entries) in draw.drawGroups) {
                            if (!drawSafely(
                                renderType,
                                entries,
                                draw.modelView,
                                draw.projection,
                                draw.cameraX,
                                draw.cameraY,
                                draw.cameraZ,
                                draw.irisComposite
                            )) break@drawLoop
                            CooTerrainPipelineManager.recordPostDraw(renderType) {
                                drawSafely(
                                    renderType,
                                    entries,
                                    draw.modelView,
                                    draw.projection,
                                    draw.cameraX,
                                    draw.cameraY,
                                    draw.cameraZ,
                                    false
                                )
                            }
                        }
                    }
                } finally {
                    CooTerrainPipelineManager.endOverlayBatch()
                }
            } finally {
                CooTerrainPipelineManager.endFinalCompositeTerrainOverlay()
            }
        }
    }

    private fun renderDrawGroups(
        drawGroups: LinkedHashMap<RenderType, MutableList<DrawEntry>>,
        modelView: Matrix4f,
        projection: Matrix4f,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        irisComposite: Boolean,
        recordPost: Boolean = true,
    ) {
        val renderTypes = drawGroups.keys.toList()
        if (!CooTerrainPipelineManager.beginOverlayBatch(renderTypes)) return
        try {
            if (!CooTerrainPipelineManager.isSodiumTerrainOverlayEnabled()) return
            for ((renderType, entries) in drawGroups) {
                if (!drawSafely(
                    renderType,
                    entries,
                    modelView,
                    projection,
                    cameraX,
                    cameraY,
                    cameraZ,
                    irisComposite
                )) return
                if (recordPost) {
                    CooTerrainPipelineManager.recordPostDraw(renderType) {
                        drawSafely(
                            renderType,
                            entries,
                            modelView,
                            projection,
                            cameraX,
                            cameraY,
                            cameraZ,
                            false
                        )
                    }
                }
            }
        } finally {
            CooTerrainPipelineManager.endOverlayBatch()
        }
    }

    /**
     * 释放单个 Sodium RenderSection 持有的全部地形覆盖 GPU 资源。
     *
     * @param section 已删除、重建或离开缓存的 RenderSection
     */
    @JvmStatic
    fun releaseSection(section: RenderSection) {
        regionSections[section.region]?.let { sections ->
            if (sections[section.sectionIndex] === section) sections.remove(section.sectionIndex)
            if (sections.isEmpty()) regionSections.remove(section.region)
        }
        uploaded.release(section, ::closeUploaded)
    }

    /**
     * 释放所有线程本地构建器、待上传批次、GPU 批次和延迟绘制记录。
     *
     * 由客户端资源重载或关闭生命周期调用。
     */
    @JvmStatic
    fun releaseAll() {
        activeBuild.get()?.close()
        activeBuild.remove()
        synchronized(buildLock) {
            buildGeneration++
            pending.clear(BuiltBatch::close)
        }
        uploaded.clear(::closeUploaded)
        regionSections.clear()
        synchronized(deferredDraws) {
            deferredDraws.clear()
        }
    }

    private fun upload(section: RenderSection, batch: BuiltBatch): UploadedBatch? {
        var vertexBuffer: OpenGlTerrainMesh? = null
        var sortBuffer: ByteBufferBuilder? = null
        var sortState: MeshData.SortState? = null
        return try {
            if (batch.pass.isTranslucent) {
                val camera = Minecraft.getInstance().gameRenderer.mainCamera().position()
                sortBuffer = ByteBufferBuilder(RenderType.BIG_BUFFER_SIZE)
                sortState = batch.meshData.sortQuads(
                    sortBuffer,
                    VertexSorting.byDistance(
                        (camera.x - section.originX).toFloat(),
                        (camera.y - section.originY).toFloat(),
                        (camera.z - section.originZ).toFloat()
                    )
                )
            }
            vertexBuffer = OpenGlTerrainMesh(batch.meshData)
            UploadedBatch(batch.renderType, batch.pass, vertexBuffer, batch.sprites, sortState, sortBuffer)
        } catch (error: RuntimeException) {
            vertexBuffer?.close()
            sortBuffer?.close()
            CooParticlesConstants.logger.error(
                "Failed to upload Sodium terrain overlay for section [{}, {}, {}]",
                section.chunkX,
                section.chunkY,
                section.chunkZ,
                error
            )
            CooTerrainPipelineManager.handleSodiumOverlayFailure(
                "section upload",
                batch.renderType,
                error
            )
            null
        } finally {
            batch.close()
        }
    }

    private fun collectDrawGroups(
        renderLists: SortedRenderLists,
        pass: TerrainRenderPass
    ): LinkedHashMap<RenderType, MutableList<DrawEntry>> {
        val groups = LinkedHashMap<RenderType, MutableList<DrawEntry>>()
        val reverse = pass.isTranslucent
        val lists = renderLists.iterator(reverse)
        while (lists.hasNext()) {
            val renderList = lists.next()
            val sections = renderList.sectionsWithGeometryIterator(reverse) ?: continue
            val region = renderList.region
            while (sections.hasNext()) {
                val section = regionSections[region]?.get(sections.nextByteAsInt()) ?: continue
                if (section.isDisposed) continue
                uploaded.get(section).orEmpty()
                    .asSequence()
                    .filter { it.pass === pass }
                    .forEach { batch ->
                        groups.getOrPut(batch.renderType) { ArrayList() }
                            .add(DrawEntry(section, batch))
                    }
            }
        }
        return groups
    }

    private fun drawRenderType(
        renderType: RenderType,
        entries: List<DrawEntry>,
        modelView: Matrix4f,
        projection: Matrix4f,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        irisComposite: Boolean
    ) {
        OpenGlPostEffectExecutionBackend.withPreservedGlState {
            drawRenderTypePreserved(
                renderType,
                entries,
                modelView,
                projection,
                cameraX,
                cameraY,
                cameraZ,
                irisComposite
            )
        }
    }

    /**
     * 在调用方 GL 状态保护范围内绘制一个 Sodium 地形覆盖批次。
     *
     * @param renderType 当前覆盖 RenderType
     * @param entries 可见 section 及其 GPU 批次
     * @param modelView 当前模型视图矩阵
     * @param projection 当前投影矩阵
     * @param cameraX 相机世界 X 坐标
     * @param cameraY 相机世界 Y 坐标
     * @param cameraZ 相机世界 Z 坐标
     * @param irisComposite 是否在 Iris 最终合成结果上绘制
     */
    private fun drawRenderTypePreserved(
        renderType: RenderType,
        entries: List<DrawEntry>,
        modelView: Matrix4f,
        projection: Matrix4f,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        irisComposite: Boolean
    ) {
        val drawFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        val readFramebuffer = glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
        val viewport = IntArray(4)
        glGetIntegerv(GL_VIEWPORT, viewport)
        val previousDepthFunc = glGetInteger(GL_DEPTH_FUNC)
        val previousDepthMask = glGetBoolean(GL_DEPTH_WRITEMASK)
        val depthTestEnabled = glIsEnabled(GL_DEPTH_TEST)
        try {
            if (irisComposite) {
                GlStateManager._enableDepthTest()
                glEnable(GL_DEPTH_TEST)
                GlStateManager._depthMask(false)
                glDepthMask(false)
                GlStateManager._depthFunc(GL_GEQUAL)
                glDepthFunc(GL_GEQUAL)
            }
            entries.filterNot { it.batch.vertexBuffer.isClosed }.forEach { entry ->
                entry.batch.sprites.forEach(SpriteUtil.INSTANCE::markSpriteActive)
                resortTranslucent(entry, cameraX, cameraY, cameraZ)
                check(CooTerrainPipelineManager.drawSodiumOverlay(
                    renderType,
                    modelView,
                    projection,
                    (entry.section.originX - cameraX).toFloat(),
                    (entry.section.originY - cameraY).toFloat(),
                    (entry.section.originZ - cameraZ).toFloat(),
                ) {
                    restoreTarget(drawFramebuffer, readFramebuffer, viewport)
                    if (irisComposite) {
                        GlStateManager._enableDepthTest()
                        glEnable(GL_DEPTH_TEST)
                        GlStateManager._depthMask(false)
                        glDepthMask(false)
                        GlStateManager._depthFunc(GL_GEQUAL)
                        glDepthFunc(GL_GEQUAL)
                    } else if (!entry.batch.pass.isTranslucent &&
                        !CooTerrainPipelineManager.isTerrainAttachmentCaptureActive() &&
                        !CooTerrainPipelineManager.isTerrainPostRenderType(renderType)
                    ) {
                        // 替换原版不透明几何时必须建立真实遮挡，不能只在最终颜色上补画。
                        GlStateManager._depthMask(true)
                        glDepthMask(true)
                    }
                    entry.batch.vertexBuffer.draw()
                }) { "Sodium 地形覆盖 shader 尚不可用" }
            }
        } finally {
            GlStateManager._depthFunc(previousDepthFunc)
            glDepthFunc(previousDepthFunc)
            GlStateManager._depthMask(previousDepthMask)
            glDepthMask(previousDepthMask)
            if (depthTestEnabled) {
                GlStateManager._enableDepthTest()
                glEnable(GL_DEPTH_TEST)
            } else {
                GlStateManager._disableDepthTest()
                glDisable(GL_DEPTH_TEST)
            }
            restoreTarget(drawFramebuffer, readFramebuffer, viewport)
        }
    }

    private fun resortTranslucent(entry: DrawEntry, cameraX: Double, cameraY: Double, cameraZ: Double) {
        val sortState = entry.batch.sortState ?: return
        val sortBuffer = entry.batch.sortBuffer ?: return
        sortBuffer.clear()
        val result = requireNotNull(
            sortState.buildSortedIndexBuffer(
                sortBuffer,
                VertexSorting.byDistance(
                    (cameraX - entry.section.originX).toFloat(),
                    (cameraY - entry.section.originY).toFloat(),
                    (cameraZ - entry.section.originZ).toFloat()
                )
            )
        ) { "Sodium translucent terrain overlay index sorting returned no data" }
        result.use {
            entry.batch.vertexBuffer.uploadIndexBuffer(it.byteBuffer())
        }
    }

    private fun drawSafely(
        renderType: RenderType,
        entries: List<DrawEntry>,
        modelView: Matrix4f,
        projection: Matrix4f,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        irisComposite: Boolean
    ): Boolean {
        return try {
            drawRenderType(
                renderType,
                entries,
                modelView,
                projection,
                cameraX,
                cameraY,
                cameraZ,
                irisComposite
            )
            true
        } catch (error: RuntimeException) {
            CooTerrainPipelineManager.handleSodiumOverlayFailure("section draw", renderType, error)
            false
        }
    }

    private fun restoreTarget(drawFramebuffer: Int, readFramebuffer: Int, viewport: IntArray) {
        OpenGlRenderTargetBridge.bindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFramebuffer)
        OpenGlRenderTargetBridge.bindFramebuffer(GL_READ_FRAMEBUFFER, readFramebuffer)
        GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3])
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
    }

    private fun closeUploaded(batch: UploadedBatch) {
        removeDeferredBatch(batch)
        if (RenderSystem.isOnRenderThread()) {
            batch.vertexBuffer.close()
            batch.sortBuffer?.close()
        } else {
            Minecraft.getInstance().execute {
                batch.vertexBuffer.close()
                batch.sortBuffer?.close()
            }
        }
    }

    /** 资源被替换或释放时，从 Iris 延迟队列移除仍引用旧 GPU buffer 的绘制项。 */
    private fun removeDeferredBatch(batch: UploadedBatch) {
        synchronized(deferredDraws) {
            val retainedDraws = ArrayList<DeferredDraw>(deferredDraws.size)
            deferredDraws.forEach { draw ->
                val retainedGroups = LinkedHashMap<RenderType, List<DrawEntry>>()
                draw.drawGroups.forEach { (renderType, entries) ->
                    val retainedEntries = entries.filterNot { it.batch === batch }
                    if (retainedEntries.isNotEmpty()) {
                        retainedGroups[renderType] = retainedEntries
                    }
                }
                if (retainedGroups.isNotEmpty()) {
                    retainedDraws += draw.copy(drawGroups = retainedGroups)
                }
            }
            deferredDraws.clear()
            deferredDraws.addAll(retainedDraws)
        }
    }

    private data class BatchKey(
        val renderType: RenderType,
        val pass: TerrainRenderPass
    )

    private class BuildBuffer(
        val key: BatchKey
    ) : AutoCloseable {
        val backing = ByteBufferBuilder(RenderType.BIG_BUFFER_SIZE)
        val builder = BufferBuilder(backing, PrimitiveTopology.QUADS, CooTerrainVertexFormats.BLOCK_EFFECT)
        val sprites = LinkedHashSet<TextureAtlasSprite>()

        /**
         * 根据输入和 `BuildBuffer` 当前配置创建 `build` 结果；返回对象保留本次配置的语义。
         *
         * 示例：`build()`。
         *
         * @return 根据当前输入生成的新对象或数据结果
         */
        fun build(): BuiltBatch? {
            val meshData = builder.build() ?: run {
                close()
                return null
            }
            try {
                return BuiltBatch(key.renderType, key.pass, meshData, backing, sprites.toList())
            } catch (failure: Throwable) {
                meshData.close()
                throw failure
            }
        }

        /**
         * 释放 `BuildBuffer` 在 `close` 中管理的资源；再次使用前必须重新初始化。
         *
         * 示例：`close()`。
         */
        override fun close() {
            backing.close()
        }
    }

    /**
     * 一个工作线程独占的区段覆盖构建器。
     * @property generation 启动时资源代数，用于拒绝重载前的结果
     */
    private class BuildCollector(val generation: Long) : AutoCloseable {
        private val buffers = LinkedHashMap<BatchKey, BuildBuffer>()

        /**
         * 执行 `BuildCollector` 定义的 `append` 操作；输入和返回值用于该组件当前的渲染职责。
         *
         * 示例：`append(renderType = renderType, pass = pass, sprite = sprite, append = append)`。
         *
         * @param renderType 当前操作需要的输入值；其语义由方法名和所属组件共同限定
         *
         * @param pass 当前操作需要的输入值；其语义由方法名和所属组件共同限定
         *
         * @param sprite 当前操作需要的输入值；其语义由方法名和所属组件共同限定
         *
         * @param append 当前操作需要的输入值；其语义由方法名和所属组件共同限定
         */
        fun append(
            renderType: RenderType,
            pass: TerrainRenderPass,
            sprite: TextureAtlasSprite?,
            append: (BufferBuilder) -> Unit
        ) {
            val key = BatchKey(renderType, pass)
            val buffer = buffers.getOrPut(key) { BuildBuffer(key) }
            sprite?.let(buffer.sprites::add)
            append(buffer.builder)
        }

        /**
         * 根据输入和 `BuildCollector` 当前配置创建 `build` 结果；返回对象保留本次配置的语义。
         *
         * 示例：`build()`。
         *
         * @return 根据当前输入生成的新对象或数据结果
         */
        fun build(): List<BuiltBatch> {
            val built = ArrayList<BuiltBatch>(buffers.size)
            return try {
                val remaining = buffers.values.iterator()
                while (remaining.hasNext()) {
                    val buffer = remaining.next()
                    remaining.remove()
                    try {
                        buffer.build()?.let(built::add)
                    } catch (error: Throwable) {
                        buffer.close()
                        throw error
                    }
                }
                built
            } catch (error: Throwable) {
                built.forEach(BuiltBatch::close)
                close()
                throw error
            }
        }

        /**
         * 执行 `BuildCollector` 的 `renderPasses` 渲染操作，处理传入数据并更新当前帧或 GPU 状态。
         *
         * 示例：`renderPasses()`。
         *
         * @return 当前操作计算、更新或查询得到的结果
         */
        fun renderPasses(): Set<TerrainRenderPass> {
            return buffers.keys.mapTo(LinkedHashSet()) { it.pass }
        }

        /**
         * 释放 `BuildCollector` 在 `close` 中管理的资源；再次使用前必须重新初始化。
         *
         * 示例：`close()`。
         */
        override fun close() {
            buffers.values.forEach(BuildBuffer::close)
            buffers.clear()
        }
    }

    private class BuiltBatch(
        val renderType: RenderType,
        val pass: TerrainRenderPass,
        val meshData: MeshData,
        private val backing: ByteBufferBuilder,
        val sprites: List<TextureAtlasSprite>
    ) : AutoCloseable {
        /**
         * 释放 `BuiltBatch` 在 `close` 中管理的资源；再次使用前必须重新初始化。
         *
         * 示例：`close()`。
         */
        override fun close() {
            meshData.close()
            backing.close()
        }
    }

    private data class UploadedBatch(
        val renderType: RenderType,
        val pass: TerrainRenderPass,
        val vertexBuffer: OpenGlTerrainMesh,
        val sprites: List<TextureAtlasSprite>,
        val sortState: MeshData.SortState?,
        val sortBuffer: ByteBufferBuilder?
    )

    private data class DrawEntry(
        val section: RenderSection,
        val batch: UploadedBatch
    )

    private data class DeferredDraw(
        val drawGroups: Map<RenderType, List<DrawEntry>>,
        val modelView: Matrix4f,
        val projection: Matrix4f,
        val cameraX: Double,
        val cameraY: Double,
        val cameraZ: Double,
        val irisComposite: Boolean,
        val frameEpoch: Long,
    )
}
