package cn.coostack.cooparticlesapi.renderer.terrain

import cn.coostack.cooparticlesapi.display.LayeredVertexConsumer
import cn.coostack.cooparticlesapi.renderer.backend.bindWrite
import cn.coostack.cooparticlesapi.renderer.pipeline.CooTerrainLayer
import cn.coostack.cooparticlesapi.renderer.post.OpenGlPostEffectExecutionBackend
import cn.coostack.cooparticlesapi.renderer.terrain.sodium.OpenGlTerrainMesh
import cn.coostack.cooparticlesapi.renderer.terrain.sodium.CooIdentityResourceStore
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.VertexSorting
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import com.mojang.renderpearl.backend.opengl.GlStateManager
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SectionBufferBuilderPack
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.oit.OitStage
import net.minecraft.client.renderer.oit.OitRenderPassProvider
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.block.BlockQuadOutput
import net.minecraft.client.renderer.block.FluidRenderer
import net.minecraft.client.renderer.chunk.ChunkSectionLayer
import net.minecraft.client.renderer.chunk.CompiledSectionMesh
import net.minecraft.client.renderer.chunk.SectionCompiler
import net.minecraft.client.renderer.chunk.SectionMesh
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.lwjgl.opengl.GL33.glDepthMask

/**
 * 管理 26.3 原版区段的独立覆盖几何，不向有限的 ChunkSectionLayer 枚举塞入自定义层。
 *
 * 编译线程在原版完成光照和模型随机选择后复制 quad；Results 转交给 CompiledSectionMesh 时转移所有权。
 * 渲染线程按原版可见区段懒上传，区段关闭和编译取消分别释放 GPU 与 CPU 数据。
 * 示例：编译 hook 调用 [beginBuild]，原版世界渲染完成后调用 [render]。
 */
object VanillaTerrainOverlay {
    /** 当前编译线程唯一持有的临时构建器。 */
    private val activeBuild = ThreadLocal<Build?>()
    /** 已成功返回但尚未转交已编译区段的 CPU 资源，键为 Results 对象身份。 */
    private val pending = CooIdentityResourceStore<SectionCompiler.Results, Batch>()
    /** 区段网格拥有的覆盖资源，键为已编译网格对象身份，不引用世界或 RenderSection。 */
    private val sections = CooIdentityResourceStore<SectionMesh, Batch>()
    /** 串行化编译线程与渲染线程的所有权转移。 */
    private val lock = Any()
    /** 重载后不再接收旧工作线程的结果，避免资源清理完成后重新登记过期区段。 */
    @Volatile private var generation = 0L

    /**
     * 开始一次原版区段编译，异常退出的上次构建先释放。
     * 示例：`VanillaTerrainOverlay.beginBuild(pack)`。
     * @param pack 当前编译工作线程独占的缓冲池包，只用于取消归属判断
     */
    @JvmStatic
    fun beginBuild(pack: SectionBufferBuilderPack) {
        activeBuild.get()?.close()
        activeBuild.set(Build(pack, generation))
    }

    /**
     * 更新流体输出当前方块上下文，复制坐标后不引用原版可变迭代坐标。
     * 示例：`VanillaTerrainOverlay.currentBlock(pos, state)`。
     * @param pos 正在编译的世界方块坐标
     * @param state 此坐标从编译区域快照读取的状态
     */
    @JvmStatic
    fun currentBlock(pos: BlockPos, state: BlockState) {
        activeBuild.get()?.let {
            it.position = pos.immutable()
            it.state = state
        }
    }

    /**
     * 包装已经具有原版光照与颜色的 quad 输出，不重新运行模型求值或随机选择。
     * 示例：`quadOutput(original, state, pos, false)`。
     * @param original 原版 quad 接收器
     * @param state 当前编译的方块状态
     * @param pos 世界坐标，调用期间由原版编译线程持有
     * @param forceOpaque 原版叶片设置是否把本方块强制放入不透明层
     * @return 在保持原版替换/保留策略后复制覆盖几何的接收器
     */
    @JvmStatic
    fun quadOutput(
        original: BlockQuadOutput, state: BlockState, pos: BlockPos, forceOpaque: Boolean,
    ): BlockQuadOutput {
        val build = activeBuild.get() ?: return original
        if (!CooTerrainPipelineManager.isTerrainOverlayEnabled() || CooTerrainPipelineManager.usesSodiumTerrainOverlay()) return original
        val worldPos = pos.immutable()
        return BlockQuadOutput { x, y, z, quad, instance ->
            val layer = if (forceOpaque) ChunkSectionLayer.SOLID else quad.materialInfo().layer()
            val overlays = CooTerrainPipelineManager.resolveOverlayRenderTypes(state, baseLayer(layer), worldPos)
            if (overlays.isEmpty() || CooTerrainPipelineManager.shouldPreserveVanillaTerrainGeometry(overlays)) {
                original.put(x, y, z, quad, instance)
            }
            for (overlay in overlays) {
                CooTerrainPipelineManager.decorateVertexConsumer(overlay, worldPos, build.buffer(overlay))
                    .putBlockBakedQuad(x, y, z, quad, instance)
            }
        }
    }

    /**
     * 装饰原版或 NeoForge 自定义流体输出，保持同一模型渲染器、邻接查询与光照路径。
     * 示例：`val output = VanillaTerrainOverlay.fluidOutput(original)`。
     * @param original 原版区段层对应的顶点接收器提供者
     * @return 根据当前方块上下文分发到原版及覆盖层的输出
     */
    @JvmStatic
    fun fluidOutput(original: FluidRenderer.Output): FluidRenderer.Output {
        return FluidRenderer.Output { layer ->
            val build = activeBuild.get()
            val state = build?.state
            val pos = build?.position
            if (build == null || state == null || pos == null ||
                !CooTerrainPipelineManager.isTerrainOverlayEnabled() || CooTerrainPipelineManager.usesSodiumTerrainOverlay()
            ) {
                original.getBuilder(layer)
            } else {
                val overlays = CooTerrainPipelineManager.resolveOverlayRenderTypes(state, baseLayer(layer), pos)
                if (overlays.isEmpty()) {
                    original.getBuilder(layer)
                } else {
                    val consumers = overlays.mapTo(mutableListOf()) {
                        CooTerrainPipelineManager.decorateVertexConsumer(it, pos, build.buffer(it))
                    }
                    if (CooTerrainPipelineManager.shouldPreserveVanillaTerrainGeometry(overlays)) {
                        consumers.add(0, original.getBuilder(layer))
                    }
                    if (consumers.size == 1) consumers.first() else LayeredVertexConsumer(consumers)
                }
            }
        }
    }

    /**
     * 成功编译后把独立 CPU 缓冲交给 Results，未完成的 builder 不再属于编译线程。
     * 示例：`VanillaTerrainOverlay.finishBuild(result)`。
     * @param result 当前线程刚刚成功返回的区段编译结果
     */
    @JvmStatic
    fun finishBuild(result: SectionCompiler.Results) {
        val build = activeBuild.get() ?: return
        activeBuild.remove()
        val batches = build.finish()
        if (batches.isNotEmpty()) {
            synchronized(lock) {
                if (build.generation == generation) {
                    pending.replace(result, batches, Batch::close)
                } else {
                    batches.forEach(Batch::close)
                }
            }
        }
    }

    /**
     * 缓冲池丢弃或清空时回收同线程尚未完成的构建，不影响已经转交 Results 的资源。
     * 示例：`VanillaTerrainOverlay.cancelBuild(pack)`。
     * @param pack 正在被清理的缓冲池包，只匹配同线程构建的所有者
     */
    @JvmStatic
    fun cancelBuild(pack: SectionBufferBuilderPack) {
        val build = activeBuild.get() ?: return
        if (build.pack !== pack) return
        activeBuild.remove()
        build.close()
    }

    /**
     * 已编译区段构造时接管其 Results 的覆盖资源，不触碰原版 renderedLayers。
     * 示例：`VanillaTerrainOverlay.attach(results, compiled)`。
     * @param result 用于构造该区段的编译结果
     * @param mesh 成功构造并将承担资源释放的区段网格
     */
    @JvmStatic
    fun attach(result: SectionCompiler.Results, mesh: CompiledSectionMesh) {
        synchronized(lock) {
            pending.take(result)?.let { sections.replace(mesh, it, Batch::close) }
        }
    }

    /**
     * Results 在取消分支释放时关闭尚未转移的覆盖 CPU 数据。
     * 示例：`VanillaTerrainOverlay.releaseResult(result)`。
     * @param result 正在由原版释放的编译结果，成功转交后调用无副作用
     */
    @JvmStatic
    fun releaseResult(result: SectionCompiler.Results) {
        synchronized(lock) { pending.release(result, Batch::close) }
    }

    /**
     * 区段重建、回收或世界退出时释放其独立覆盖资源。
     * 示例：`VanillaTerrainOverlay.release(mesh)`。
     * @param mesh 正在被替换或关闭的区段网格；重复释放无副作用
     */
    @JvmStatic
    fun release(mesh: SectionMesh) {
        synchronized(lock) { sections.release(mesh, Batch::close) }
    }

    /**
     * 关闭资源重载或世界退出时仍登记的覆盖资源；区段后续关闭可重复调用而无副作用。
     * 示例：`VanillaTerrainOverlay.releaseAll()`。
     * 未完成的工作线程构建由对应 pack 的取消 hook 处理，不跨线程访问其写入器。
     */
    @JvmStatic
    fun releaseAll() {
        synchronized(lock) {
            generation++
            pending.clear(Batch::close)
            sections.clear(Batch::close)
        }
    }

    /**
     * 判断区段是否包含覆盖几何，用于保留原版几何全部被替换后的可见区段。
     * 示例：`VanillaTerrainOverlay.hasGeometry(compiled)`。
     * @param mesh 已构造的区段网格
     * @return 仍登记至少一个覆盖批次时为 true
     */
    @JvmStatic
    fun hasGeometry(mesh: SectionMesh): Boolean = synchronized(lock) { sections.get(mesh)?.isNotEmpty() == true }

    /**
     * 原版世界阶段入口，矩阵与相机来自同一帧提取快照。
     * 示例：`VanillaTerrainOverlay.render(levelRenderer, cameraState, partialTick)`。
     * @param renderer 提供原版可见区段列表
     * @param cameraState 当前帧已提取的相机与投影状态
     * @param partialTick 当前世界插值；覆盖几何为静态区段，不单独推进动画
     */
    @JvmStatic
    fun render(renderer: LevelRenderer, cameraState: CameraRenderState, partialTick: Float) {
        renderVisible(renderer.visibleSections(), cameraState.viewRotationMatrix, cameraState.projectionMatrix, cameraState.pos, false)
    }

    /**
     * 在不透明原版区段完成后、实体绘制前提交替换几何并写入真实场景深度。
     * Iris 保留原版几何并统一在最终合成后叠加，不走此早期替换入口。
     * 示例：`VanillaTerrainOverlay.renderOpaque(levelRenderer, cameraState, partialTick)`。
     * @param renderer 提供原版可见区段列表
     * @param cameraState 当前帧已提取的相机与投影状态
     * @param partialTick 当前世界插值，静态区段覆盖不独立推进动画
     */
    @JvmStatic
    fun renderOpaque(renderer: LevelRenderer, cameraState: CameraRenderState, partialTick: Float) {
        if (CooTerrainPipelineManager.shouldPreserveVanillaTerrainGeometry()) return
        renderVisible(renderer.visibleSections(), cameraState.viewRotationMatrix, cameraState.projectionMatrix, cameraState.pos, true)
    }

    /**
     * 在原版 OIT 的真实目标中重放透明覆盖，三个阶段共享原版深度范围和透射系数。
     * @param renderer 提供当前可见区段
     * @param cameraState 本帧相机与投影
     * @param stage 当前 OIT 阶段
     * @param parameters 当前调用范围有效的原版 OIT 目标
     */
    @JvmStatic
    fun renderOit(
        renderer: LevelRenderer, cameraState: CameraRenderState,
        stage: OitStage, parameters: OitRenderPassProvider.Parameters
    ) {
        if (CooTerrainPipelineManager.shouldPreserveVanillaTerrainGeometry()) return
        OpenGlPostEffectExecutionBackend.withPreservedGlState {
            OitRenderPassProvider.createRenderPass(stage, { "Coo terrain" }, parameters).use {
                renderVisible(
                    renderer.visibleSections(), cameraState.viewRotationMatrix, cameraState.projectionMatrix,
                    cameraState.pos, false, stage
                )
            }
        }
    }

    /**
     * 在 OIT 合成后、手部深度清除前重放所有当前可见区段覆盖。
     * Iris 调用方须保留最终合成后的既有时序；不得在 OIT 辅助颜色 FBO 中调用。
     * 示例：`render(sections, viewMatrix, projectionMatrix, cameraPosition)`。
     * @param visible 原版视锥与遮挡剔除后的区段列表
     * @param modelView 当前相机视图矩阵，不含区段平移
     * @param projection 与场景深度匹配的投影矩阵
     * @param camera 当前帧世界相机坐标
     */
    @JvmStatic
    fun render(
        visible: Iterable<SectionRenderDispatcher.RenderSection>,
        modelView: Matrix4f, projection: Matrix4f, camera: Vec3,
    ) {
        renderVisible(visible, modelView, projection, camera, false)
    }

    /** 早期只提交会替换场景的不透明层；晚期只提交透明/纯后处理，Iris 晚期提交全部。 */
    private fun renderVisible(
        visible: Iterable<SectionRenderDispatcher.RenderSection>,
        modelView: Matrix4f, projection: Matrix4f, camera: Vec3, opaquePhase: Boolean,
        oitStage: OitStage? = null,
    ) {
        if (!CooTerrainPipelineManager.isTerrainOverlayEnabled() || CooTerrainPipelineManager.usesSodiumTerrainOverlay()) return
        val irisComposite = CooTerrainPipelineManager.shouldPreserveVanillaTerrainGeometry()
        val entries = visible.flatMap { section ->
            val batches = synchronized(lock) { sections.get(section.sectionMesh.get()).orEmpty() }
            val origin = section.renderOrigin.immutable()
            batches.filter { batch ->
                val opaque = !CooTerrainPipelineManager.requiresTerrainSorting(batch.renderType) &&
                    !CooTerrainPipelineManager.isTerrainPostRenderType(batch.renderType)
                val phaseMatches = if (oitStage != null) {
                    !opaque && !CooTerrainPipelineManager.isTerrainPostRenderType(batch.renderType) &&
                        CooTerrainPipelineManager.supportsNativeOit(batch.renderType)
                } else {
                    (if (opaquePhase) opaque else irisComposite || !opaque) &&
                        !CooTerrainPipelineManager.wasNativeOitRendered(batch.renderType)
                }
                phaseMatches && (!CooTerrainPipelineManager.isMappingRenderType(batch.renderType) ||
                    CooTerrainPipelineManager.isTerrainMappingSectionVisible(
                        batch.renderType, origin.x, origin.y, origin.z, origin.x + 15, origin.y + 15, origin.z + 15
                    ))
            }.map { Draw(origin, it) }
        }
        if (entries.isEmpty()) return
        val plannedOrder = listOf(
            CooTerrainLayer.SOLID, CooTerrainLayer.CUTOUT_MIPPED,
            CooTerrainLayer.CUTOUT, CooTerrainLayer.TRANSLUCENT,
        ).flatMap { CooTerrainPipelineManager.layersFor(CooTerrainPipelineManager.baseLayer(it)) }
            .withIndex().associate { it.value to it.index }
        val ordered = entries.groupBy { it.batch.renderType }.entries.sortedWith(
            compareBy<Map.Entry<RenderType, List<Draw>>> { CooTerrainPipelineManager.requiresTerrainSorting(it.key) }
                .thenBy { plannedOrder[it.key] ?: Int.MAX_VALUE }
        )
        OpenGlPostEffectExecutionBackend.withPreservedGlState {
            if (!opaquePhase && !irisComposite && oitStage == null) {
                Minecraft.getInstance().gameRenderer.mainRenderTarget().bindWrite(true)
            }
            if (!CooTerrainPipelineManager.beginOverlayBatch(ordered.map { it.key })) return@withPreservedGlState
            try {
                for ((type, unsorted) in ordered) {
                    val draws = if (CooTerrainPipelineManager.requiresTerrainSorting(type)) {
                        unsorted.sortedByDescending { Vec3.atCenterOf(it.origin).distanceToSqr(camera) }
                    } else unsorted
                    val drawBatch = {
                        for (entry in draws) drawEntry(entry, modelView, projection, camera, opaquePhase)
                    }
                    if (CooTerrainPipelineManager.isTerrainPostRenderType(type)) {
                        CooTerrainPipelineManager.recordPostDraw(type) { drawBatch() }
                    } else {
                        try {
                            drawBatch()
                            if (oitStage == null || oitStage == OitStage.ACCUMULATE) {
                                CooTerrainPipelineManager.recordPostDraw(type) { drawBatch() }
                            }
                        } catch (failure: RuntimeException) {
                            CooTerrainPipelineManager.handleOverlayDrawFailure(type, failure)
                            break
                        }
                    }
                }
            } finally {
                CooTerrainPipelineManager.endOverlayBatch()
            }
        }
    }

    /** 在已有目标与光栅保护范围内懒上传和重排一次区段绘制。 */
    private fun drawEntry(entry: Draw, view: Matrix4f, projection: Matrix4f, camera: Vec3, writeDepth: Boolean) {
        val batch = entry.batch
        synchronized(batch) {
            if (batch.closed) return
            if (batch.gpu == null) {
                batch.gpu = OpenGlTerrainMesh(batch.mesh)
                batch.releaseCpu()
            }
            val gpu = batch.gpu ?: return
            val offset = Vec3(entry.origin.x.toDouble(), entry.origin.y.toDouble(), entry.origin.z.toDouble()).subtract(camera)
            val state = batch.sortState
            val sortBuffer = batch.sortBuffer
            if (state != null && sortBuffer != null) {
                sortBuffer.clear()
                state.buildSortedIndexBuffer(
                    sortBuffer, VertexSorting.byDistance(-offset.x.toFloat(), -offset.y.toFloat(), -offset.z.toFloat())
                )?.use { gpu.uploadIndexBuffer(it.byteBuffer()) }
            }
            check(CooTerrainPipelineManager.drawSodiumOverlay(
                batch.renderType, view, projection, offset.x.toFloat(), offset.y.toFloat(), offset.z.toFloat(),
            ) {
                if (writeDepth && !CooTerrainPipelineManager.isTerrainAttachmentCaptureActive()) {
                    GlStateManager._depthMask(true)
                    glDepthMask(true)
                }
                gpu.draw()
            }) { "原版地形覆盖 shader 不可用" }
        }
    }

    /** 把有限原版层映射到管理器唯一基础身份，不区分已被原版合并的 cutout mip 状态。 */
    private fun baseLayer(layer: ChunkSectionLayer): RenderType = CooTerrainPipelineManager.baseLayer(
        when (layer) {
            ChunkSectionLayer.SOLID -> CooTerrainLayer.SOLID
            ChunkSectionLayer.CUTOUT -> CooTerrainLayer.CUTOUT_MIPPED
            ChunkSectionLayer.TRANSLUCENT -> CooTerrainLayer.TRANSLUCENT
        }
    )

    /**
     * 一次编译拥有的独立 builder，避免缓冲池 clear 导致尚未上传的 Results 数据失效。
     * @property pack 当前编译工作线程独占的缓冲池包，用于判断取消归属
     * @property generation 创建时的资源代数，用于丢弃重载前启动的工作
     */
    private class Build(val pack: SectionBufferBuilderPack, val generation: Long) : AutoCloseable {
        /** 当前循环块的独立世界坐标，用于流体回调。 */
        var position: BlockPos? = null
        /** 当前循环块状态，不跨编译使用。 */
        var state: BlockState? = null
        /** 每个覆盖身份对应的 CPU 内存所有者与写入器。 */
        private val builders = linkedMapOf<RenderType, Pair<ByteBufferBuilder, BufferBuilder>>()

        /** 返回本次编译唯一拥有的覆盖层写入器。 */
        fun buffer(type: RenderType): BufferBuilder = builders.getOrPut(type) {
            val backing = ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE)
            try {
                backing to BufferBuilder(backing, PrimitiveTopology.QUADS, CooTerrainVertexFormats.BLOCK_EFFECT)
            } catch (failure: Throwable) {
                backing.close()
                throw failure
            }
        }.second

        /** 逐项转移内存所有权；任一批次失败时同时释放已完成及尚未完成的项。 */
        fun finish(): List<Batch> {
            val batches = mutableListOf<Batch>()
            try {
                val remaining = builders.iterator()
                while (remaining.hasNext()) {
                    val (type, value) = remaining.next()
                    remaining.remove()
                    var mesh: MeshData? = null
                    try {
                        mesh = value.second.build()
                        if (mesh == null) value.first.close() else batches += Batch(type, mesh, value.first)
                    } catch (failure: Throwable) {
                        mesh?.close()
                        value.first.close()
                        throw failure
                    }
                }
                return batches
            } catch (failure: Throwable) {
                batches.forEach(Batch::close)
                close()
                throw failure
            }
        }

        override fun close() {
            builders.values.forEach { it.first.close() }
            builders.clear()
        }
    }

    /**
     * 编译、懒上传与关闭共同拥有的单个覆盖批次；关闭后延迟回调只会跳过。
     * @property renderType 稳定的覆盖层身份，由地形管理器拥有
     * @property mesh 首次上传前有效的顶点与初次排序索引，上传后立即释放
     * @property backing 本批次独占的原生顶点内存，与 mesh 一并释放
     */
    private class Batch(val renderType: RenderType, val mesh: MeshData, private val backing: ByteBufferBuilder) : AutoCloseable {
        /** 仅透明层分配可重复使用的索引排序内存。 */
        val sortBuffer = if (CooTerrainPipelineManager.requiresTerrainSorting(renderType)) {
            ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE)
        } else null
        /** 透明层的独立四边形中心点，不依赖上传后释放的顶点字节。 */
        val sortState = try {
            sortBuffer?.let { mesh.sortQuads(it, VertexSorting.DISTANCE_TO_ORIGIN) }
        } catch (failure: Throwable) {
            sortBuffer?.close()
            throw failure
        }
        /** 渲染线程首次使用时创建，区段关闭后由相同线程删除。 */
        var gpu: OpenGlTerrainMesh? = null
        /** GPU 上传后顶点 CPU 内存立即回收，避免每个可见区段长期占用双份几何。 */
        private var cpuMeshClosed = false
        /** 关闭状态同时保护延迟后处理回调。 */
        @Volatile var closed = false

        /** 上传成功或取消时只释放一次 CPU 顶点与初次索引。 */
        fun releaseCpu() {
            if (cpuMeshClosed) return
            mesh.close()
            backing.close()
            cpuMeshClosed = true
        }

        @Synchronized
        override fun close() {
            if (closed) return
            closed = true
            releaseCpu()
            sortBuffer?.close()
            val buffer = gpu
            gpu = null
            if (buffer != null) {
                if (RenderSystem.isOnRenderThread()) buffer.close() else Minecraft.getInstance().execute { buffer.close() }
            }
        }
    }

    /**
     * 当前帧区段坐标与批次，不跨帧缓存可变 RenderSection。
     * @property origin 当前帧复制的区段世界原点
     * @property batch 本次绘制引用的资源所有者，执行前须检查关闭状态
     */
    private data class Draw(val origin: BlockPos, val batch: Batch)
}
