package cn.coostack.cooparticlesapi.renderer.terrain.sodium

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.compat.IrisCompat
import cn.coostack.cooparticlesapi.compat.IrisShadowPassState
import cn.coostack.cooparticlesapi.renderer.terrain.CooEffectUvResolver
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainPipelineManager
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainVertexFormats
import cn.coostack.cooparticlesapi.renderer.post.OpenGlPostEffectExecutionBackend
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.VertexBuffer
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.vertex.VertexSorting
import net.caffeinemc.mods.sodium.api.util.ColorARGB
import net.caffeinemc.mods.sodium.api.texture.SpriteUtil
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection
import net.caffeinemc.mods.sodium.client.render.chunk.compile.BuilderTaskOutput
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionInfo
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder
import net.caffeinemc.mods.sodium.client.render.frapi.mesh.MutableQuadViewImpl
import net.caffeinemc.mods.sodium.client.render.texture.SpriteFinderCache
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.core.BlockPos
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11.GL_DEPTH_FUNC
import org.lwjgl.opengl.GL11.GL_DEPTH_TEST
import org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK
import org.lwjgl.opengl.GL11.GL_LEQUAL
import org.lwjgl.opengl.GL11.GL_VIEWPORT
import org.lwjgl.opengl.GL11.glDisable
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL11.glGetBoolean
import org.lwjgl.opengl.GL11.glGetIntegerv
import org.lwjgl.opengl.GL11.glIsEnabled
import org.lwjgl.opengl.GL11.glViewport
import org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER
import org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING
import org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER
import org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING
import org.lwjgl.opengl.GL30.glBindFramebuffer
import org.lwjgl.opengl.GL30.glGetInteger

/**
 * 管理 Sodium 0.6.x 的 section 级地形覆盖构建、GPU 上传和绘制。
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
    private val deferredDraws = ArrayList<DeferredDraw>()

    /**
     * 把 Sodium terrain pass 还原为对应的原版 RenderType。
     *
     * @param original 方块原本使用的 RenderType，用于保留 tripwire 或未知 pass
     * @param material Sodium 为当前方块面选择的材质
     * @param pass 当前 Sodium terrain pass
     * @return 与 pass 和材质 mipmap 规则对应的原版 terrain layer
     */
    @JvmStatic
    fun resolveBaseLayer(original: RenderType, material: Material, pass: TerrainRenderPass): RenderType {
        return when (pass) {
            DefaultTerrainRenderPasses.SOLID -> RenderType.solid()
            DefaultTerrainRenderPasses.CUTOUT -> {
                if (material.mipped) RenderType.cutoutMipped() else RenderType.cutout()
            }
            DefaultTerrainRenderPasses.TRANSLUCENT -> {
                if (original === RenderType.tripwire()) RenderType.tripwire() else RenderType.translucent()
            }
            else -> original
        }
    }

    /**
     * 开始当前区块构建线程的一次地形覆盖收集。
     *
     * 同一线程上未结束的旧收集器会先被关闭，避免异常构建遗留本地缓冲。
     */
    @JvmStatic
    fun beginBuild() {
        activeBuild.get()?.close()
        activeBuild.set(BuildCollector())
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
                    .setColor(ColorARGB.mulRGB(vertex.color, vertex.ao))
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
        pending.replace(output, batches, BuiltBatch::close)
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
            val gpuBatches = built.mapNotNull { upload(output.render, it) }
            uploaded.replace(output.render, gpuBatches, ::closeUploaded)
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
     */
    @JvmStatic
    fun renderOrDefer(
        renderLists: SortedRenderLists,
        matrices: ChunkRenderMatrices,
        pass: TerrainRenderPass,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double
    ) {
        if (!CooTerrainPipelineManager.isSodiumTerrainOverlayEnabled()) return
        val drawGroups = collectDrawGroups(renderLists, pass)
        if (drawGroups.isEmpty()) return
        if (CooTerrainPipelineManager.shouldPreserveVanillaTerrainGeometry()) {
            when (IrisCompat.shadowPassState()) {
                IrisShadowPassState.ACTIVE -> return
                IrisShadowPassState.UNKNOWN -> {
                    CooTerrainPipelineManager.handleSodiumOverlayFailure(
                        "Iris shadow-pass detection",
                        drawGroups.keys.firstOrNull(),
                        IllegalStateException("Iris shadow-pass state is unavailable")
                    )
                    return
                }
                IrisShadowPassState.INACTIVE -> Unit
            }
            synchronized(deferredDraws) {
                deferredDraws += DeferredDraw(
                    drawGroups.mapValues { (_, entries) -> entries.toList() },
                    Matrix4f(matrices.modelView()),
                    Matrix4f(matrices.projection()),
                    cameraX,
                    cameraY,
                    cameraZ
                )
            }
            return
        }
        renderDrawGroups(
            drawGroups,
            Matrix4f(matrices.modelView()),
            Matrix4f(matrices.projection()),
            cameraX,
            cameraY,
            cameraZ,
            false
        )
    }

    /**
     * 在 Iris 最终合成后绘制并清空本帧延迟的 Sodium 地形覆盖。
     *
     * 该入口负责包围最终合成状态与 overlay batch；兼容条件失效时只清空队列。
     */
    @JvmStatic
    fun flushDeferred() {
        val draws = synchronized(deferredDraws) {
            deferredDraws.toList().also { deferredDraws.clear() }
        }
        if (draws.isEmpty() ||
            !CooTerrainPipelineManager.isSodiumTerrainOverlayEnabled() ||
            !CooTerrainPipelineManager.shouldPreserveVanillaTerrainGeometry()
        ) {
            return
        }
        val renderTypes = draws.flatMap { it.drawGroups.keys }.distinct()
        CooTerrainPipelineManager.beginFinalCompositeTerrainOverlay()
        try {
            if (!CooTerrainPipelineManager.beginOverlayBatch(renderTypes)) return
            try {
                if (!CooTerrainPipelineManager.isSodiumTerrainOverlayEnabled()) return
                drawLoop@ for (draw in draws) {
                    for ((renderType, entries) in draw.drawGroups) {
                        if (!drawSafely(
                            renderType,
                            entries,
                            draw.modelView,
                            draw.projection,
                            draw.cameraX,
                            draw.cameraY,
                            draw.cameraZ,
                            true
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

    private fun renderDrawGroups(
        drawGroups: LinkedHashMap<RenderType, MutableList<DrawEntry>>,
        modelView: Matrix4f,
        projection: Matrix4f,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        irisComposite: Boolean
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
        pending.clear(BuiltBatch::close)
        uploaded.clear(::closeUploaded)
        synchronized(deferredDraws) {
            deferredDraws.clear()
        }
    }

    private fun upload(section: RenderSection, batch: BuiltBatch): UploadedBatch? {
        val vertexBuffer = VertexBuffer(VertexBuffer.Usage.STATIC)
        var sortBuffer: ByteBufferBuilder? = null
        var sortState: MeshData.SortState? = null
        return try {
            if (batch.pass.isTranslucent) {
                val camera = Minecraft.getInstance().gameRenderer.mainCamera.position
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
            VertexBuffer.unbind()
            vertexBuffer.bind()
            vertexBuffer.upload(batch.meshData)
            UploadedBatch(batch.renderType, batch.pass, vertexBuffer, batch.sprites, sortState, sortBuffer)
        } catch (error: RuntimeException) {
            vertexBuffer.close()
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
            VertexBuffer.unbind()
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
                val section = region.getSection(sections.nextByteAsInt()) ?: continue
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
        var setupCompleted = false
        try {
            renderType.setupRenderState()
            setupCompleted = true
            restoreTarget(drawFramebuffer, readFramebuffer, viewport)
            if (irisComposite) {
                RenderSystem.enableDepthTest()
                RenderSystem.depthMask(false)
                RenderSystem.depthFunc(GL_LEQUAL)
            }
            val shader = RenderSystem.getShader() ?: return
            entries.filterNot { it.batch.vertexBuffer.isInvalid() }.forEach { entry ->
                entry.batch.sprites.forEach(SpriteUtil.INSTANCE::markSpriteActive)
                resortTranslucent(entry, cameraX, cameraY, cameraZ)
                shader.getUniform("ChunkOffset")?.set(
                    (entry.section.originX - cameraX).toFloat(),
                    (entry.section.originY - cameraY).toFloat(),
                    (entry.section.originZ - cameraZ).toFloat()
                )
                entry.batch.vertexBuffer.bind()
                entry.batch.vertexBuffer.drawWithShader(modelView, projection, shader)
            }
        } finally {
            try {
                VertexBuffer.unbind()
                if (setupCompleted) {
                    renderType.clearRenderState()
                }
            } finally {
                RenderSystem.depthFunc(previousDepthFunc)
                RenderSystem.depthMask(previousDepthMask)
                if (depthTestEnabled) {
                    RenderSystem.enableDepthTest()
                } else {
                    RenderSystem.disableDepthTest()
                }
                restoreTarget(drawFramebuffer, readFramebuffer, viewport)
            }
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
            entry.batch.vertexBuffer.bind()
            entry.batch.vertexBuffer.uploadIndexBuffer(it)
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
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFramebuffer)
        glBindFramebuffer(GL_READ_FRAMEBUFFER, readFramebuffer)
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
    }

    private fun closeUploaded(batch: UploadedBatch) {
        removeDeferredBatch(batch)
        if (RenderSystem.isOnRenderThread()) {
            batch.vertexBuffer.close()
            batch.sortBuffer?.close()
        } else {
            RenderSystem.recordRenderCall {
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
        val builder = BufferBuilder(backing, VertexFormat.Mode.QUADS, CooTerrainVertexFormats.BLOCK_EFFECT)
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
            return BuiltBatch(key.renderType, key.pass, meshData, backing, sprites.toList())
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

    private class BuildCollector : AutoCloseable {
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
                buffers.values.mapNotNullTo(built, BuildBuffer::build)
                buffers.clear()
                built
            } catch (error: RuntimeException) {
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
        val vertexBuffer: VertexBuffer,
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
        val cameraZ: Double
    )
}
