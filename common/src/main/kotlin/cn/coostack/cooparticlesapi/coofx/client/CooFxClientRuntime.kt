package cn.coostack.cooparticlesapi.coofx.client

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.CooParticlesAPIClient
import cn.coostack.cooparticlesapi.compat.IrisCompat
import cn.coostack.cooparticlesapi.compat.IrisEntityShaderKind
import cn.coostack.cooparticlesapi.coofx.adapter.CooFxConsumerRuntime
import cn.coostack.cooparticlesapi.coofx.adapter.CooFxEmitterParameterNames
import cn.coostack.cooparticlesapi.coofx.adapter.CooFxFrameRequest
import cn.coostack.cooparticlesapi.coofx.adapter.CooFxParameterValue
import cn.coostack.cooparticlesapi.coofx.adapter.CooFxModelPlayRequest
import cn.coostack.cooparticlesapi.coofx.adapter.CooFxModelPlayResult
import cn.coostack.cooparticlesapi.coofx.adapter.CooFxPlayRequest
import cn.coostack.cooparticlesapi.coofx.adapter.CooFxPlaybackFailure
import cn.coostack.cooparticlesapi.coofx.adapter.CooFxWorldTransform
import cn.coostack.cooparticlesapi.coofx.adapter.CooFxPlaybackHandle
import cn.coostack.cooparticlesapi.coofx.adapter.CooFxPlayResult
import cn.coostack.cooparticlesapi.coofx.asset.CooFxAssetImporter
import cn.coostack.cooparticlesapi.coofx.asset.CooFxSourceAsset
import cn.coostack.cooparticlesapi.coofx.asset.MinecraftCooFxResourceProvider
import cn.coostack.cooparticlesapi.coofx.client.render.CooFxOpenGlGpuPackage
import cn.coostack.cooparticlesapi.coofx.client.render.CooFxOpenGlGpuPackageUploader
import cn.coostack.cooparticlesapi.coofx.client.render.CooFxOpenGlPrimitive
import cn.coostack.cooparticlesapi.coofx.client.render.CooFxShaderProgramFactory
import cn.coostack.cooparticlesapi.coofx.client.render.CooFxShaderSourceBundle
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxAlphaMode
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxAssetCompiler
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxCompiledRenderPackage
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxCullMode
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxDepthTest
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxLightMode
import cn.coostack.cooparticlesapi.coofx.render.gpu.CooFxGpuPackageRegistry
import cn.coostack.cooparticlesapi.coofx.render.gpu.CooFxRenderThreadGuard
import cn.coostack.cooparticlesapi.coofx.runtime.model.CooFxModelInstanceManager
import cn.coostack.cooparticlesapi.coofx.runtime.model.CooFxResolvedModelAsset
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.CooFxMeshEmitterFactory
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.CooFxMeshParticleManager
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.CooFxMeshEmitterTransform
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.render.CooFxMeshInstanceBatch
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.render.CooFxMeshParticleRenderer
import cn.coostack.cooparticlesapi.cparticle.CParticleIndexedBlendState
import cn.coostack.cooparticlesapi.extend.ofVanillaID
import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram
import cn.coostack.cooparticlesapi.renderer.state.CooGLSLStateManager
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.LightTexture
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.GL11.GL_ALWAYS
import org.lwjgl.opengl.GL11.GL_BACK
import org.lwjgl.opengl.GL11.GL_BLEND
import org.lwjgl.opengl.GL11.GL_COLOR_WRITEMASK
import org.lwjgl.opengl.GL11.GL_CULL_FACE
import org.lwjgl.opengl.GL11.GL_CULL_FACE_MODE
import org.lwjgl.opengl.GL11.GL_CCW
import org.lwjgl.opengl.GL11.GL_DEPTH_TEST
import org.lwjgl.opengl.GL11.GL_FRONT_FACE
import org.lwjgl.opengl.GL11.GL_LEQUAL
import org.lwjgl.opengl.GL11.GL_ONE
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.GL_ZERO
import org.lwjgl.opengl.GL13.glActiveTexture
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL14.glBlendFuncSeparate
import org.lwjgl.opengl.GL11.glCullFace
import org.lwjgl.opengl.GL11.glDepthFunc
import org.lwjgl.opengl.GL11.glDepthMask
import org.lwjgl.opengl.GL11.glDisable
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL11.glFrontFace
import org.lwjgl.opengl.GL11.glGetInteger
import org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.GL_TEXTURE1
import org.lwjgl.opengl.GL13.GL_TEXTURE2
import org.lwjgl.opengl.GL13.GL_TEXTURE_BINDING_2D
import org.lwjgl.opengl.GL20.GL_MAX_DRAW_BUFFERS
import org.lwjgl.opengl.GL33.GL_BLEND_DST_ALPHA
import org.lwjgl.opengl.GL33.GL_BLEND_DST_RGB
import org.lwjgl.opengl.GL33.GL_BLEND_EQUATION_ALPHA
import org.lwjgl.opengl.GL33.GL_BLEND_EQUATION_RGB
import org.lwjgl.opengl.GL33.GL_BLEND_SRC_ALPHA
import org.lwjgl.opengl.GL33.GL_BLEND_SRC_RGB
import org.lwjgl.opengl.GL33.GL_FUNC_ADD
import org.lwjgl.opengl.GL30.glColorMaski
import org.lwjgl.opengl.GL30.glDisablei
import org.lwjgl.opengl.GL30.glEnablei
import org.lwjgl.opengl.GL30.glGetIntegeri
import org.lwjgl.opengl.GL30.glGetIntegeri_v
import org.lwjgl.opengl.GL30.glIsEnabledi
import java.util.ArrayDeque

/**
 * CooFX 的客户端 CPU owner 和渲染线程边界。
 *
 * 资源解析、编译、播放请求和整数 tick 不创建 OpenGL 资源；WORLD_PASS 才执行 shader、静态
 * package、实例缓冲和 draw。所有 GPU 对象都由本类按 generation lease 生命周期释放。
 */
internal class CooFxClientRuntime(
    private val maxPendingRequests: Int = 4096,
) : CooFxConsumerRuntime {
    private val lock = Any()
    private val compiler = CooFxAssetCompiler()
    private val particleManager = CooFxMeshParticleManager(16384)
    private val modelManager = CooFxModelInstanceManager()
    private val renderThreadGuard = CooFxRenderThreadGuard { RenderSystem.assertOnRenderThread() }
    private val particleRenderer = CooFxMeshParticleRenderer()
    private val gpuPackages = CooFxGpuPackageRegistry(
        renderThreadGuard,
        CooFxOpenGlGpuPackageUploader(),
    )
    private val pending = ArrayDeque<PendingPlay>()
    private val pendingModels = ArrayDeque<PendingModelPlay>()
    private val uploaded = LinkedHashMap<ResourceLocation, UploadedAsset>()

    @Volatile
    private var snapshot = PreparedSnapshot(0L, emptyMap(), null)
    private var appliedRevision = -1L
    private var nextRequestId = 1L
    private var currentBackendSignature = ""
    private var shaderProgram: CooShaderProgram? = null
    private var shaderRevision = -1L
    private var irisShaderPackWarningEmitted = false
    private var irisEmissiveWarningEmitted = false

    init {
        require(maxPendingRequests > 0) { "Pending request limit must be positive" }
    }

    override fun play(request: CooFxPlayRequest): CooFxPlayResult {
        val prepared = snapshot.assets[request.resourceId]
            ?: return failed(request.resourceId, "asset", "CooFX 资源尚未加载：${request.resourceId}")
        if (request.parameterOverrides.keys.any { key ->
                key !in setOf(
                    CooFxEmitterParameterNames.COUNT,
                    CooFxEmitterParameterNames.DELAY_TICKS,
                    CooFxEmitterParameterNames.LIFETIME_TICKS,
                    CooFxEmitterParameterNames.PLAYBACK_SPEED,
                )
            }) {
            return failed(request.resourceId, "validation", "存在未知 CooFX emitter 参数覆盖")
        }
        val emitterId = resolveEmitter(prepared.source, request.emitterId)
            ?: return failed(request.resourceId, "validation", "emitterId 为空且资源不是唯一 emitter")
        if (prepared.source.emitters.none { it.id == emitterId }) {
            return failed(request.resourceId, "validation", "找不到 CooFX emitter：$emitterId")
        }
        val clipIndex = resolveClip(prepared, request.clipId)
            ?: return failed(request.resourceId, "validation", "当前资产找不到 clip：${request.clipId}")
        val generation = synchronized(lock) {
            if (appliedRevision == snapshot.revision) {
                uploaded[request.resourceId]
                    ?.takeIf { it.digest == prepared.compiled.contentDigest && it.backendSignature == currentBackendSignature }
                    ?.generation
            } else {
                null
            }
        }
        if (generation == null) {
            val requestId = synchronized(lock) {
                if (pending.size >= maxPendingRequests) {
                    null
                } else {
                    val id = nextRequestId++
                    pending += PendingPlay(id, request.withEmitter(emitterId))
                    id
                }
            } ?: return failed(request.resourceId, "queue", "CooFX 待播放队列已满")
            return CooFxPlayResult.Queued(requestId)
        }
        val handle = start(
            prepared = prepared,
            request = request.withEmitter(emitterId),
            generation = generation,
            clipIndex = clipIndex,
            backendCapabilitySignature = currentBackendSignature,
        )
        return CooFxPlayResult.Started(handle)
    }

    fun playModel(request: CooFxModelPlayRequest): CooFxModelPlayResult {
        val prepared = snapshot.assets[request.resourceId]
            ?: return modelFailed(request.resourceId, "asset", "CooFX 资源尚未加载：${request.resourceId}")
        val clipIndex = resolveClip(prepared, request.clipId)
            ?: return modelFailed(request.resourceId, "validation", "当前资产找不到 clip：${request.clipId}")
        val generation = synchronized(lock) {
            if (appliedRevision == snapshot.revision) {
                uploaded[request.resourceId]
                    ?.takeIf { asset ->
                        asset.digest == prepared.compiled.contentDigest &&
                            asset.backendSignature == currentBackendSignature
                    }
                    ?.generation
            } else {
                null
            }
        }
        if (generation == null) {
            val requestId = synchronized(lock) {
                if (pendingModels.size >= maxPendingRequests) {
                    null
                } else {
                    val id = nextRequestId++
                    pendingModels += PendingModelPlay(id, request)
                    id
                }
            } ?: return modelFailed(request.resourceId, "queue", "CooFX 模型待播放队列已满")
            return CooFxModelPlayResult.Queued(requestId)
        }
        return CooFxModelPlayResult.Started(startModel(prepared, request, clipIndex))
    }

    fun updateParticle(instanceId: Long, request: CooFxPlayRequest): Boolean {
        if (request.emitterId == null || !particleManager.isEmitterActive(instanceId)) return false
        val transform = CooFxMeshEmitterTransform(
            position = Vector3f(
                request.transform.x.toFloat(),
                request.transform.y.toFloat(),
                request.transform.z.toFloat(),
            ),
            rotation = Quaternionf(
                request.transform.rotationX,
                request.transform.rotationY,
                request.transform.rotationZ,
                request.transform.rotationW,
            ),
            scale = Vector3f(
                request.transform.scaleX,
                request.transform.scaleY,
                request.transform.scaleZ,
            ),
        )
        particleManager.updateEmitterTransform(instanceId, transform)
        return true
    }

    fun updateModel(instanceId: Long, request: CooFxModelPlayRequest): Boolean {
        val prepared = snapshot.assets[request.resourceId] ?: return false
        val clipIndex = resolveClip(prepared, request.clipId) ?: return false
        return synchronized(lock) {
            modelManager.update(instanceId, request, prepared.compiled, clipIndex)
        }
    }

    fun sampleCamera(
        resourceId: ResourceLocation,
        cameraSelector: String?,
        clipId: String?,
        rawTimeSeconds: Float,
        transform: CooFxWorldTransform,
    ): CooFxCameraPose? {
        require(rawTimeSeconds.isFinite()) { "Camera sample time must be finite" }
        val prepared = snapshot.assets[resourceId] ?: return null
        val clipIndex = resolveClip(prepared, clipId) ?: return null
        val camera = prepared.compiled.resolveCamera(cameraSelector) ?: return null
        val sceneMatrix = Matrix4f().translationRotateScale(
            Vector3f(transform.x.toFloat(), transform.y.toFloat(), transform.z.toFloat()),
            Quaternionf(transform.rotationX, transform.rotationY, transform.rotationZ, transform.rotationW),
            Vector3f(transform.scaleX, transform.scaleY, transform.scaleZ),
        )
        val worldMatrix = sceneMatrix.mul(
            prepared.compiled.nodeWorldMatrix(clipIndex, camera.nodeIndex, rawTimeSeconds)
        )
        return CooFxCameraPose(camera, worldMatrix)
    }

    fun cancelQueued(requestId: Long): Boolean {
        return synchronized(lock) {
            val iterator = pending.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().requestId == requestId) {
                    iterator.remove()
                    return@synchronized true
                }
            }
            false
        }
    }

    fun cancelQueuedModel(requestId: Long): Boolean {
        return synchronized(lock) {
            val iterator = pendingModels.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().requestId == requestId) {
                    iterator.remove()
                    return@synchronized true
                }
            }
            false
        }
    }

    override fun tickClient() {
        synchronized(lock) {
            particleManager.tick()
            modelManager.tick()
        }
    }

    override fun renderWorldFrame(request: CooFxFrameRequest) {
        RenderSystem.assertOnRenderThread()
        if (request.backendCapabilitySignature != currentBackendSignature) {
            synchronized(lock) {
                currentBackendSignature = request.backendCapabilitySignature
                releaseRenderResourcesOnThread()
            }
        }
        val activeSnapshot = snapshot
        val sources = activeSnapshot.shaderSources ?: return
        ensureShaderProgram(sources, activeSnapshot.revision)
        particleRenderer.initialize()
        if (uploadPackages(activeSnapshot, request.backendCapabilitySignature)) {
            commitPreparedSnapshotOnThread(activeSnapshot)
        }
        startPending(activeSnapshot, request.backendCapabilitySignature)
        startPendingModels(activeSnapshot, request.backendCapabilitySignature)

        val compiledByGeneration = uploaded.values.associate { uploadedAsset ->
            uploadedAsset.generation to uploadedAsset.compiled
        }
        val batches = synchronized(lock) {
            val particleBatches = particleManager.buildBatches(
                partialTick = request.partialTick,
                poseResolver = { key -> compiledByGeneration[key.generation] },
                packedLightResolver = { position ->
                    packedLightAt(position.x.toDouble(), position.y.toDouble(), position.z.toDouble())
                },
            )
            val modelBatches = modelManager.buildBatches(request.partialTick, ::packedLightAt) { assetId ->
                val uploadedAsset = uploaded[assetId] ?: return@buildBatches null
                CooFxResolvedModelAsset(
                    compiled = uploadedAsset.compiled,
                    generation = uploadedAsset.generation,
                    backendCapabilitySignature = uploadedAsset.backendSignature,
                )
            }
            (particleBatches + modelBatches).sortedBy(CooFxMeshInstanceBatch::key)
        }
        if (batches.isEmpty()) return
        val leases = activeSnapshot.assets.keys.mapNotNull { assetId ->
            gpuPackages.acquire(assetId)
        }
        try {
            val packagesByGeneration = leases.associateBy { it.generation }
            batches.forEach { batch ->
                val packageForBatch = packagesByGeneration[batch.key.generation]?.gpuPackage as? CooFxOpenGlGpuPackage
                    ?: return@forEach
                val primitive = packageForBatch.primitive(batch.key.primitiveId)
                renderBatch(request, batch, primitive)
            }
        } finally {
            leases.asReversed().forEach { it.close() }
        }
    }

    override fun clearTransientWorldState() {
        synchronized(lock) {
            particleManager.clear()
            modelManager.clear()
            pending.clear()
            pendingModels.clear()
        }
    }

    override fun reloadResources(resourceManager: ResourceManager) {
        val importer = CooFxAssetImporter(MinecraftCooFxResourceProvider(resourceManager))
        val assets = LinkedHashMap<ResourceLocation, PreparedAsset>()
        resourceManager.listResources("coofx") { location ->
            location.path.endsWith(".coofx.json")
        }.keys.sortedBy(ResourceLocation::toString).forEach { assetId ->
            val imported = runCatching { importer.import(assetId) }
                .getOrElse { failure ->
                    CooParticlesConstants.logger.error("[CooFX-ASSET] 导入 CooFX 资源失败：$assetId", failure)
                    return@forEach
                }
            imported.diagnostics.forEach { diagnostic ->
                CooParticlesConstants.logger.warn("[CooFX-ASSET] $assetId ${diagnostic.severity}: ${diagnostic.message}")
            }
            val source = imported.asset ?: run {
                CooParticlesConstants.logger.error(
                    "[CooFX-ASSET] 资源未进入 prepared snapshot：importer 没有产出 asset，resource=$assetId",
                )
                return@forEach
            }
            val compiled = runCatching { compiler.compile(source) }
                .getOrElse { failure ->
                    CooParticlesConstants.logger.error("[CooFX-ASSET] 编译 CooFX 资源失败：$assetId", failure)
                    return@forEach
                }
            assets[assetId] = PreparedAsset(source, compiled)
        }
        val sources = runCatching { CooFxShaderProgramFactory.loadSources(resourceManager) }
            .getOrElse { failure ->
                CooParticlesConstants.logger.error("加载 CooFX shader 源码失败", failure)
                null
            }
        synchronized(lock) {
            particleManager.clear()
            modelManager.clear()
            pending.clear()
            pendingModels.clear()
            snapshot = PreparedSnapshot(snapshot.revision + 1L, assets, sources)
        }
    }

    override fun releaseRenderResources() {
        if (RenderSystem.isOnRenderThread()) {
            synchronized(lock) { releaseRenderResourcesOnThread() }
        } else {
            RenderSystem.recordRenderCall {
                synchronized(lock) { releaseRenderResourcesOnThread() }
            }
        }
    }

    override fun stopClient() {
        synchronized(lock) {
            particleManager.clear()
            modelManager.clear()
            pending.clear()
            pendingModels.clear()
        }
        releaseRenderResources()
    }

    private fun commitPreparedSnapshotOnThread(prepared: PreparedSnapshot) {
        if (appliedRevision == prepared.revision) return
        val removedAssetIds = uploaded.keys - prepared.assets.keys
        removedAssetIds.forEach { assetId ->
            gpuPackages.retire(assetId)
            uploaded.remove(assetId)
        }
        appliedRevision = prepared.revision
    }

    private fun releaseRenderResourcesOnThread() {
        check(RenderSystem.isOnRenderThread()) { "CooFX GPU 资源必须在渲染线程释放" }
        gpuPackages.disposeAll()
        uploaded.clear()
        particleRenderer.release()
        CooFxShaderProgramFactory.releaseProgram(shaderProgram)
        shaderProgram = null
        shaderRevision = -1L
        appliedRevision = -1L
    }

    private fun ensureShaderProgram(sources: CooFxShaderSourceBundle, revision: Long) {
        if (shaderProgram != null && shaderRevision == revision) return
        val candidate = CooFxShaderProgramFactory.createProgram(sources)
        runCatching { candidate.init() }
            .onFailure { CooFxShaderProgramFactory.releaseProgram(candidate) }
            .getOrThrow()
        val previous = shaderProgram
        shaderProgram = candidate
        shaderRevision = revision
        CooFxShaderProgramFactory.releaseProgram(previous)
    }

    private fun uploadPackages(prepared: PreparedSnapshot, backendSignature: String): Boolean {
        var succeeded = true
        prepared.assets.forEach { (assetId, asset) ->
            val cached = uploaded[assetId]
            if (cached?.digest == asset.compiled.contentDigest && cached.backendSignature == backendSignature) return@forEach
            gpuPackages.uploadAndReplace(
                compiledPackage = asset.compiled,
                backendCapabilitySignature = backendSignature,
            ).onSuccess { generation ->
                uploaded[assetId] = UploadedAsset(asset.compiled.contentDigest, backendSignature, generation, asset.compiled)
            }.onFailure { failure ->
                succeeded = false
                CooParticlesConstants.logger.error("上传 CooFX GPU package 失败：$assetId", failure)
            }
        }
        return succeeded
    }

    private fun startPending(prepared: PreparedSnapshot, backendSignature: String) {
        val requests = synchronized(lock) {
            val values = pending.toList()
            pending.clear()
            values
        }
        val unresolved = mutableListOf<PendingPlay>()
        requests.forEach { pendingPlay ->
            val asset = prepared.assets[pendingPlay.request.resourceId]
            val generation = asset?.let {
                uploaded[pendingPlay.request.resourceId]
                    ?.takeIf { uploadedAsset ->
                        uploadedAsset.digest == asset.compiled.contentDigest &&
                            uploadedAsset.backendSignature == backendSignature
                    }
                    ?.generation
            }
            if (asset == null) {
                CooParticlesConstants.logger.warn("丢弃已移除 CooFX 资源的待播放请求：${pendingPlay.request.resourceId}")
                return@forEach
            }
            if (generation == null) {
                unresolved += pendingPlay
                return@forEach
            }
            val emitterId = resolveEmitter(asset.source, pendingPlay.request.emitterId) ?: return@forEach
            val clipIndex = resolveClip(asset, pendingPlay.request.clipId) ?: return@forEach
            start(
                prepared = asset,
                request = pendingPlay.request.withEmitter(emitterId),
                generation = generation,
                clipIndex = clipIndex,
                backendCapabilitySignature = backendSignature,
            )
        }
        if (unresolved.isNotEmpty()) {
            synchronized(lock) {
                unresolved.asReversed().forEach(pending::addFirst)
            }
        }
    }

    private fun startPendingModels(prepared: PreparedSnapshot, backendSignature: String) {
        val requests = synchronized(lock) {
            val values = pendingModels.toList()
            pendingModels.clear()
            values
        }
        val unresolved = mutableListOf<PendingModelPlay>()
        requests.forEach { pendingPlay ->
            val asset = prepared.assets[pendingPlay.request.resourceId]
            val generation = asset?.let {
                uploaded[pendingPlay.request.resourceId]
                    ?.takeIf { uploadedAsset ->
                        uploadedAsset.digest == asset.compiled.contentDigest &&
                            uploadedAsset.backendSignature == backendSignature
                    }
                    ?.generation
            }
            if (asset == null) {
                CooParticlesConstants.logger.warn(
                    "丢弃已移除 CooFX 资源的模型待播放请求：${pendingPlay.request.resourceId}"
                )
                return@forEach
            }
            if (generation == null) {
                unresolved += pendingPlay
                return@forEach
            }
            val clipIndex = resolveClip(asset, pendingPlay.request.clipId)
            if (clipIndex == null) {
                CooParticlesConstants.logger.warn(
                    "丢弃 clip 不存在的 CooFX 模型请求：${pendingPlay.request.clipId}"
                )
                return@forEach
            }
            startModel(asset, pendingPlay.request, clipIndex)
        }
        if (unresolved.isNotEmpty()) {
            synchronized(lock) {
                unresolved.asReversed().forEach(pendingModels::addFirst)
            }
        }
    }

    private fun start(
        prepared: PreparedAsset,
        request: CooFxPlayRequest,
        generation: Long,
        clipIndex: Int,
        backendCapabilitySignature: String,
    ): CooFxPlaybackHandle {
        val emitterId = requireNotNull(request.emitterId)
        val baseDefinition = CooFxMeshEmitterFactory.create(
            source = prepared.source,
            compiledPackage = prepared.compiled,
            emitterId = emitterId,
            generation = generation,
            backendCapabilitySignature = backendCapabilitySignature,
        ).copy(
            clipIndex = clipIndex,
            packedLight = packedLightAt(request.transform.x, request.transform.y, request.transform.z),
        )
        val definition = baseDefinition.copy(
            delayTicks = request.intOverride(CooFxEmitterParameterNames.DELAY_TICKS) ?: baseDefinition.delayTicks,
            emissionCount = request.intOverride(CooFxEmitterParameterNames.COUNT) ?: baseDefinition.emissionCount,
            lifetimeTicks = request.intOverride(CooFxEmitterParameterNames.LIFETIME_TICKS)
                ?.let { value -> value..value }
                ?: baseDefinition.lifetimeTicks,
            playbackSpeed = request.floatOverride(CooFxEmitterParameterNames.PLAYBACK_SPEED)
                ?: baseDefinition.playbackSpeed,
        )
        val runtimeId = synchronized(lock) {
            particleManager.startEmitter(
                definition = definition,
                assetSeed = prepared.source.assetSeed.toLong(),
                requestSeed = request.requestSeed,
                transform = CooFxMeshEmitterTransform(
                    position = Vector3f(
                        request.transform.x.toFloat(),
                        request.transform.y.toFloat(),
                        request.transform.z.toFloat(),
                    ),
                    rotation = Quaternionf(
                        request.transform.rotationX,
                        request.transform.rotationY,
                        request.transform.rotationZ,
                        request.transform.rotationW,
                    ),
                    scale = Vector3f(
                        request.transform.scaleX,
                        request.transform.scaleY,
                        request.transform.scaleZ,
                    ),
                ),
            )
        }
        return RuntimePlaybackHandle(lock, particleManager, runtimeId)
    }

    private fun startModel(
        prepared: PreparedAsset,
        request: CooFxModelPlayRequest,
        clipIndex: Int,
    ): CooFxPlaybackHandle {
        val runtimeId = synchronized(lock) {
            modelManager.start(request, prepared.compiled, clipIndex)
        }
        return RuntimeModelPlaybackHandle(lock, modelManager, runtimeId)
    }

    private fun warnAboutIrisShaderPackCompatibilityIfNeeded(active: Boolean) {
        if (!active || irisShaderPackWarningEmitted) return
        irisShaderPackWarningEmitted = true
        CooParticlesConstants.logger.warn(
            "CooFX 检测到活动 Iris shaderpack：当前资产的着色结果可能与无光影不同；entity color modulation、UV/overlay、PBR、雾、deferred/G-buffer、顶点位移和 emissive second pass 均不能无条件保证。CooFX 阴影暂不支持；未通过目标光影验证的 shaderpack 视为 shader 部分不兼容。"
        )
    }

    private fun renderBatch(
        request: CooFxFrameRequest,
        batch: CooFxMeshInstanceBatch,
        primitive: CooFxOpenGlPrimitive,
    ) {
        val irisShaderPackActive = CooParticlesAPIClient.checkIrisShaderPackUsed()
        warnAboutIrisShaderPackCompatibilityIfNeeded(irisShaderPackActive)
        val previousCullFace = glGetInteger(GL_CULL_FACE_MODE)
        val previousFrontFace = glGetInteger(GL_FRONT_FACE)
        try {
            withRenderBatchStatePreserved(irisShaderPackActive) {
                glEnable(GL_DEPTH_TEST)
                glDepthFunc(
                    when (primitive.material.depthTest) {
                        CooFxDepthTest.LESS_OR_EQUAL -> GL_LEQUAL
                        CooFxDepthTest.ALWAYS -> GL_ALWAYS
                    }
                )
                glDepthMask(primitive.material.depthWrite)
                when (primitive.material.cullMode) {
                    CooFxCullMode.NONE -> glDisable(GL_CULL_FACE)
                    CooFxCullMode.BACK -> {
                        glEnable(GL_CULL_FACE)
                        glCullFace(GL_BACK)
                        glFrontFace(GL_CCW)
                    }
                }
                if (irisShaderPackActive && CParticleIndexedBlendState.isAvailable()) {
                    glDisablei(GL_BLEND, 0)
                } else {
                    glDisable(GL_BLEND)
                }
                val previousActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE)
                val previousTextures = IntArray(3) { index ->
                    glActiveTexture(GL_TEXTURE0 + index)
                    glGetInteger(GL_TEXTURE_BINDING_2D)
                }
                val minecraft = Minecraft.getInstance()
                val lightTexture = minecraft.gameRenderer.lightTexture()
                val lightmapWasEnabled = RenderSystem.getShaderTexture(2) != 0
                try {
                    if (!lightmapWasEnabled) lightTexture.turnOnLightLayer()
                    val baseColorTexture = primitive.material.baseColorTexture
                    val emissiveTexture = primitive.material.emissiveTexture
                    val baseColorTextureId = baseColorTexture?.let { minecraft.textureManager.getTexture(it).id } ?: 0
                    val emissiveTextureId = emissiveTexture?.let { minecraft.textureManager.getTexture(it).id } ?: 0
                    glActiveTexture(GL_TEXTURE0)
                    glBindTexture(GL_TEXTURE_2D, baseColorTextureId)
                    glActiveTexture(GL_TEXTURE1)
                    glBindTexture(GL_TEXTURE_2D, emissiveTextureId)
                    glActiveTexture(GL_TEXTURE2)
                    glBindTexture(GL_TEXTURE_2D, RenderSystem.getShaderTexture(2))
                    val program = requireNotNull(shaderProgram)
                    var expandedEntityVertexCount = 0
                    program.useOnContext {
                        setMatrix4("uView", request.viewMatrix)
                        setMatrix4("uProjection", request.projectionMatrix)
                        setFloat3(
                            "uCameraPosition",
                            Vector3f(request.cameraX.toFloat(), request.cameraY.toFloat(), request.cameraZ.toFloat()),
                        )
                        setFloat("uPartialTick", request.partialTick)
                        setInt("uBaseColor", 0)
                        setInt("uEmissiveTexture", 1)
                        setInt("uLightmap", 2)
                        setFloat4(
                            "uBaseColorFactor",
                            Vector4f(
                                primitive.material.baseColorFactor.red,
                                primitive.material.baseColorFactor.green,
                                primitive.material.baseColorFactor.blue,
                                primitive.material.baseColorFactor.alpha,
                            ),
                        )
                        setFloat3(
                            "uEmissiveFactor",
                            Vector3f(
                                primitive.material.emissiveFactor.red,
                                primitive.material.emissiveFactor.green,
                                primitive.material.emissiveFactor.blue,
                            ),
                        )
                        setFloat("uEmissiveStrength", primitive.material.emissiveStrength)
                        setBoolean("uHasBaseColorTexture", baseColorTexture != null)
                        setBoolean("uHasEmissiveTexture", emissiveTexture != null)
                        setBoolean("uUseAlphaCutoff", primitive.material.alphaMode == CooFxAlphaMode.MASK)
                        setBoolean("uEmissiveOnly", false)
                        setBoolean("uFullBright", primitive.material.lightMode == CooFxLightMode.FULL_BRIGHT)
                        setFloat("uAlphaCutoff", primitive.material.alphaCutoff)
                         setBoolean("uIrisEntitySpace", irisShaderPackActive)
                        if (irisShaderPackActive) {
                            expandedEntityVertexCount = particleRenderer.expandForIrisEntity(
                                batch,
                                primitive.drawBinding,
                            )
                        } else {
                            particleRenderer.render(listOf(batch)) { primitive.drawBinding }
                        }
                    }
                    if (irisShaderPackActive && expandedEntityVertexCount > 0) {
                        val previousShaderTexture0 = RenderSystem.getShaderTexture(0)
                        val previousShaderTexture1 = RenderSystem.getShaderTexture(1)
                        val entityBaseTextureId = if (baseColorTextureId != 0) {
                            baseColorTextureId
                        } else {
                            minecraft.textureManager.getTexture(ofVanillaID("textures/misc/white.png")).id
                        }
                        val overlayTexture = minecraft.gameRenderer.overlayTexture()
                        try {
                            RenderSystem.setShaderTexture(0, entityBaseTextureId)
                            overlayTexture.setupOverlayColor()
                            if (primitive.material.emissiveStrength > 0F) {
                                warnIrisEmissiveDeferred()
                            }
                            IrisCompat.runWithRenderEntityShader(
                                request.viewMatrix,
                                request.projectionMatrix,
                                shaderKind = if (primitive.material.alphaMode == CooFxAlphaMode.MASK) {
                                    IrisEntityShaderKind.CUTOUT
                                } else {
                                    IrisEntityShaderKind.SOLID
                                },
                            ) {
                                particleRenderer.drawExpandedIrisEntity(expandedEntityVertexCount)
                                // emissive 第二次提交待 NEW_ENTITY 专用实现与状态测试后恢复。
                            }
                        } finally {
                            overlayTexture.teardownOverlayColor()
                            RenderSystem.setShaderTexture(0, previousShaderTexture0)
                            RenderSystem.setShaderTexture(1, previousShaderTexture1)
                        }
                    }
                } finally {
                    if (!lightmapWasEnabled) lightTexture.turnOffLightLayer()
                    previousTextures.forEachIndexed { index, texture ->
                        glActiveTexture(GL_TEXTURE0 + index)
                        glBindTexture(GL_TEXTURE_2D, texture)
                    }
                    glActiveTexture(previousActiveTexture)
                }
            }
        } finally {
            glCullFace(previousCullFace)
            glFrontFace(previousFrontFace)
        }
    }

    private fun CooFxPlayRequest.intOverride(name: String): Int? {
        return when (val value = parameterOverrides[name]) {
            null -> null
            is CooFxParameterValue.IntValue -> value.value
            else -> throw IllegalArgumentException("CooFX emitter 参数 $name 必须是整数")
        }
    }

    private fun packedLightAt(x: Double, y: Double, z: Double): Int {
        val level = Minecraft.getInstance().level ?: return LightTexture.FULL_BRIGHT
        return LevelRenderer.getLightColor(level, BlockPos.containing(x, y, z))
    }

    private fun warnIrisEmissiveDeferred() {
        if (irisEmissiveWarningEmitted) return
        irisEmissiveWarningEmitted = true
        CooParticlesConstants.logger.warn(
            "Iris emissive second pass deferred: expanded NEW_ENTITY emissive 提交尚未验证，已安全跳过",
        )
    }

    private fun <T> withRenderBatchStatePreserved(active: Boolean, block: () -> T): T {
        return withIrisDrawBufferStatePreserved(active) {
            CooGLSLStateManager.useState(block)
        }
    }

    private fun <T> withIrisDrawBufferStatePreserved(active: Boolean, block: () -> T): T {
        if (!active) return block()
        val indexedBlend = CParticleIndexedBlendState.isAvailable()
        val states = Array(glGetInteger(GL_MAX_DRAW_BUFFERS)) { drawBuffer ->
            val colorMask = IntArray(4)
            glGetIntegeri_v(
                GL_COLOR_WRITEMASK,
                drawBuffer,
                colorMask,
            )
            CooFxDrawBufferState(
                blendEnabled = glIsEnabledi(GL_BLEND, drawBuffer),
                sourceRgb = if (indexedBlend) glGetIntegeri(GL_BLEND_SRC_RGB, drawBuffer) else 0,
                destinationRgb = if (indexedBlend) glGetIntegeri(GL_BLEND_DST_RGB, drawBuffer) else 0,
                sourceAlpha = if (indexedBlend) glGetIntegeri(GL_BLEND_SRC_ALPHA, drawBuffer) else 0,
                destinationAlpha = if (indexedBlend) glGetIntegeri(GL_BLEND_DST_ALPHA, drawBuffer) else 0,
                equationRgb = if (indexedBlend) glGetIntegeri(GL_BLEND_EQUATION_RGB, drawBuffer) else 0,
                equationAlpha = if (indexedBlend) glGetIntegeri(GL_BLEND_EQUATION_ALPHA, drawBuffer) else 0,
                colorMask = colorMask,
            )
        }
        try {
            return block()
        } finally {
            states.forEachIndexed { drawBuffer, state ->
                if (indexedBlend) {
                    CParticleIndexedBlendState.setEquation(drawBuffer, state.equationRgb, state.equationAlpha)
                    CParticleIndexedBlendState.setFactors(
                        drawBuffer,
                        state.sourceRgb,
                        state.destinationRgb,
                        state.sourceAlpha,
                        state.destinationAlpha,
                    )
                }
                if (state.blendEnabled) {
                    glEnablei(GL_BLEND, drawBuffer)
                } else {
                    glDisablei(GL_BLEND, drawBuffer)
                }
                glColorMaski(
                    drawBuffer,
                    state.colorMask[0] != 0,
                    state.colorMask[1] != 0,
                    state.colorMask[2] != 0,
                    state.colorMask[3] != 0,
                )
            }
        }
    }

    private fun <T> withPrimaryColorWriteOnly(block: () -> T): T = withColorMasks(
        enabled = { drawBuffer -> drawBuffer == 0 },
        block = block,
    )

    private fun <T> withColorMasks(enabled: (Int) -> Boolean, block: () -> T): T {
        val colorMasks = Array(glGetInteger(GL_MAX_DRAW_BUFFERS)) { drawBuffer ->
            IntArray(4).also { mask ->
                glGetIntegeri_v(
                    GL_COLOR_WRITEMASK,
                    drawBuffer,
                    mask,
                )
            }
        }
        try {
            colorMasks.indices.forEach { drawBuffer ->
                if (!enabled(drawBuffer)) {
                    glColorMaski(drawBuffer, false, false, false, false)
                }
            }
            return block()
        } finally {
            colorMasks.forEachIndexed { drawBuffer, mask ->
                glColorMaski(
                    drawBuffer,
                    mask[0] != 0,
                    mask[1] != 0,
                    mask[2] != 0,
                    mask[3] != 0,
                )
            }
        }
    }

    private fun CooFxPlayRequest.floatOverride(name: String): Float? {
        return when (val value = parameterOverrides[name]) {
            null -> null
            is CooFxParameterValue.FloatValue -> value.value
            else -> throw IllegalArgumentException("CooFX emitter 参数 $name 必须是浮点数")
        }
    }

    private fun CooFxPlayRequest.withEmitter(emitterId: String): CooFxPlayRequest = CooFxPlayRequest(
        resourceId = resourceId,
        transform = transform,
        requestSeed = requestSeed,
        clipId = clipId,
        emitterId = emitterId,
        parameterOverrides = parameterOverrides,
    )

    private fun failed(
        resourceId: ResourceLocation,
        stage: String,
        message: String,
    ): CooFxPlayResult = CooFxPlayResult.Failed(
        CooFxPlaybackFailure(resourceId, stage, message)
    )

    private fun modelFailed(
        resourceId: ResourceLocation,
        stage: String,
        message: String,
    ): CooFxModelPlayResult = CooFxModelPlayResult.Failed(
        CooFxPlaybackFailure(resourceId, stage, message)
    )

    private fun resolveEmitter(source: CooFxSourceAsset, emitterId: String?): String? {
        if (emitterId != null) return emitterId
        return source.emitters.singleOrNull()?.id
    }

    private fun resolveClip(asset: PreparedAsset, clipId: String?): Int? {
        // 没有任何动画时，所有 selector 都使用节点 bind/world matrix；-1 明确表示无 clip。
        if (asset.compiled.clips.isEmpty()) return -1
        if (clipId == null) return 0
        val index = asset.compiled.clips.indexOfFirst { it.id == clipId }
        return index.takeIf { it >= 0 }
    }

    private data class PreparedSnapshot(
        val revision: Long,
        val assets: Map<ResourceLocation, PreparedAsset>,
        val shaderSources: CooFxShaderSourceBundle?,
    )

    private data class PreparedAsset(
        val source: CooFxSourceAsset,
        val compiled: CooFxCompiledRenderPackage,
    )

    private data class PendingPlay(
        val requestId: Long,
        val request: CooFxPlayRequest,
    )

    private data class PendingModelPlay(
        val requestId: Long,
        val request: CooFxModelPlayRequest,
    )

    private data class CooFxDrawBufferState(
        val blendEnabled: Boolean,
        val sourceRgb: Int,
        val destinationRgb: Int,
        val sourceAlpha: Int,
        val destinationAlpha: Int,
        val equationRgb: Int,
        val equationAlpha: Int,
        val colorMask: IntArray,
    )

    private data class UploadedAsset(
        val digest: String,
        val backendSignature: String,
        val generation: Long,
        val compiled: CooFxCompiledRenderPackage,
    )

    private class RuntimeModelPlaybackHandle(
        private val lock: Any,
        private val manager: CooFxModelInstanceManager,
        private val runtimeId: Long,
    ) : CooFxPlaybackHandle {
        override val instanceId: Long
            get() = runtimeId

        override val isAlive: Boolean
            get() = synchronized(lock) { manager.isActive(runtimeId) }

        override fun stop() {
            synchronized(lock) { manager.stop(runtimeId) }
        }
    }

    private class RuntimePlaybackHandle(
        private val lock: Any,
        private val manager: CooFxMeshParticleManager,
        private val runtimeId: Long,
    ) : CooFxPlaybackHandle {
        override val instanceId: Long
            get() = runtimeId

        override val isAlive: Boolean
            get() = synchronized(lock) { manager.isEmitterActive(runtimeId) }

        override fun stop() {
            synchronized(lock) { manager.stopEmitter(runtimeId) }
        }
    }
}
