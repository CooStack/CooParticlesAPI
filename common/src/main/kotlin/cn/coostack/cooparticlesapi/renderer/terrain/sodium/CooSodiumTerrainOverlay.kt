package cn.coostack.cooparticlesapi.renderer.terrain.sodium

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.compat.IrisCompat
import cn.coostack.cooparticlesapi.compat.IrisShadowPassState
import cn.coostack.cooparticlesapi.renderer.terrain.CooEffectUvResolver
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainPipelineManager
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainVertexFormats
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
import org.lwjgl.opengl.GL11.GL_POLYGON_OFFSET_FACTOR
import org.lwjgl.opengl.GL11.GL_POLYGON_OFFSET_FILL
import org.lwjgl.opengl.GL11.GL_POLYGON_OFFSET_UNITS
import org.lwjgl.opengl.GL11.GL_VIEWPORT
import org.lwjgl.opengl.GL11.glDisable
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL11.glGetBoolean
import org.lwjgl.opengl.GL11.glGetFloat
import org.lwjgl.opengl.GL11.glGetIntegerv
import org.lwjgl.opengl.GL11.glIsEnabled
import org.lwjgl.opengl.GL11.glPolygonOffset
import org.lwjgl.opengl.GL11.glViewport
import org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER
import org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING
import org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER
import org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING
import org.lwjgl.opengl.GL30.glBindFramebuffer
import org.lwjgl.opengl.GL30.glGetInteger

/** Sodium 0.6.x 的 section 级 terrain overlay 构建、上传和绘制协调器。 */
internal object CooSodiumTerrainOverlay {
    private val activeBuild = ThreadLocal<BuildCollector?>()
    private val pending = CooIdentityResourceStore<ChunkBuildOutput, BuiltBatch>()
    private val uploaded = CooIdentityResourceStore<RenderSection, UploadedBatch>()
    private val deferredDraws = ArrayList<DeferredDraw>()

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

    @JvmStatic
    fun beginBuild() {
        activeBuild.get()?.close()
        activeBuild.set(BuildCollector())
    }

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
                    .setLight(vertex.light)
                    .setNormal(normalX, normalY, normalZ)
            }
        }
    }

    @JvmStatic
    fun markRenderPasses(renderData: BuiltSectionInfo.Builder) {
        activeBuild.get()?.renderPasses()?.forEach { pass ->
            renderData.addRenderPass(pass)
        }
    }

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

    @JvmStatic
    fun discardBuildOutput(output: ChunkBuildOutput) {
        pending.release(output, BuiltBatch::close)
    }

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
        CooTerrainPipelineManager.beginOverlayBatch(renderTypes)
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
        CooTerrainPipelineManager.beginOverlayBatch(renderTypes)
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

    @JvmStatic
    fun releaseSection(section: RenderSection) {
        uploaded.release(section, ::closeUploaded)
    }

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
        val drawFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        val readFramebuffer = glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
        val viewport = IntArray(4)
        glGetIntegerv(GL_VIEWPORT, viewport)
        val previousDepthFunc = glGetInteger(GL_DEPTH_FUNC)
        val previousDepthMask = glGetBoolean(GL_DEPTH_WRITEMASK)
        val depthTestEnabled = glIsEnabled(GL_DEPTH_TEST)
        val polygonOffsetEnabled = glIsEnabled(GL_POLYGON_OFFSET_FILL)
        val previousPolygonOffsetFactor = glGetFloat(GL_POLYGON_OFFSET_FACTOR)
        val previousPolygonOffsetUnits = glGetFloat(GL_POLYGON_OFFSET_UNITS)
        var setupCompleted = false
        try {
            renderType.setupRenderState()
            setupCompleted = true
            restoreTarget(drawFramebuffer, readFramebuffer, viewport)
            if (irisComposite) {
                RenderSystem.enableDepthTest()
                RenderSystem.depthMask(false)
                RenderSystem.depthFunc(GL_LEQUAL)
                glEnable(GL_POLYGON_OFFSET_FILL)
                glPolygonOffset(-1F, -10F)
            }
            val shader = requireNotNull(RenderSystem.getShader()) {
                "Sodium terrain overlay has no active shader"
            }
            entries.forEach { entry ->
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
                glPolygonOffset(previousPolygonOffsetFactor, previousPolygonOffsetUnits)
                if (polygonOffsetEnabled) {
                    glEnable(GL_POLYGON_OFFSET_FILL)
                } else {
                    glDisable(GL_POLYGON_OFFSET_FILL)
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

        fun build(): BuiltBatch? {
            val meshData = builder.build() ?: run {
                close()
                return null
            }
            return BuiltBatch(key.renderType, key.pass, meshData, backing, sprites.toList())
        }

        override fun close() {
            backing.close()
        }
    }

    private class BuildCollector : AutoCloseable {
        private val buffers = LinkedHashMap<BatchKey, BuildBuffer>()

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

        fun renderPasses(): Set<TerrainRenderPass> {
            return buffers.keys.mapTo(LinkedHashSet()) { it.pass }
        }

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
