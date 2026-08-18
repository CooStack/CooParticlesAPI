package cn.coostack.cooparticlesapi.coofx.runtime.model

import cn.coostack.cooparticlesapi.coofx.adapter.CooFxModelPlayRequest
import cn.coostack.cooparticlesapi.coofx.adapter.CooFxWorldTransform
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxCompiledRenderPackage
import cn.coostack.cooparticlesapi.coofx.render.instance.CooFxColor
import cn.coostack.cooparticlesapi.coofx.render.instance.CooFxFloat3
import cn.coostack.cooparticlesapi.coofx.render.instance.CooFxMeshInstanceData
import cn.coostack.cooparticlesapi.coofx.render.instance.CooFxMeshInstanceLayout
import cn.coostack.cooparticlesapi.coofx.render.instance.CooFxQuaternion
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.render.CooFxMeshBatchKey
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.render.CooFxMeshInstanceBatch
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix4fc
import java.util.TreeMap

/** 当前 render generation 中可用于模型实例批处理的 compiled package。 */
internal data class CooFxResolvedModelAsset(
    val compiled: CooFxCompiledRenderPackage,
    val generation: Long,
    val backendCapabilitySignature: String,
) {
    init {
        require(generation > 0L) { "Model render generation must be positive" }
        require(backendCapabilitySignature.isNotBlank()) { "Model backend signature must not be blank" }
    }
}

/**
 * 管理不依赖 emitter 的持久模型实例。
 *
 * 一个逻辑模型实例会按 compiled scene 中的 primitive-node 绑定展开为多个 draw instance；模型只在显式
 * stop、世界清理或资源重载时移除。动画时间按客户端 tick 独立推进，不读取系统时钟。
 */
internal class CooFxModelInstanceManager {
    private val instances = LinkedHashMap<Long, ModelState>()
    private var nextInstanceId = 0L
    private var nextStableDrawId = 0L

    val instanceCount: Int
        get() = instances.size

    fun isActive(instanceId: Long): Boolean = instanceId in instances

    fun start(request: CooFxModelPlayRequest, compiled: CooFxCompiledRenderPackage, clipIndex: Int): Long {
        val normalizedClipIndex = normalizeClipIndex(clipIndex, compiled)
        val instanceId = nextInstanceId++
        val stableDrawIds = LongArray(compiled.modelPrimitiveNodeBindings.size) { nextStableDrawId++ }
        instances[instanceId] = ModelState(
            resourceId = request.resourceId,
            contentDigest = compiled.contentDigest,
            transform = request.transform,
            requestSeed = request.requestSeed,
            clipIndex = normalizedClipIndex,
            playbackSpeed = request.playbackSpeed,
            stableDrawIds = stableDrawIds,
        )
        return instanceId
    }

    fun update(
        instanceId: Long,
        request: CooFxModelPlayRequest,
        compiled: CooFxCompiledRenderPackage,
        clipIndex: Int,
    ): Boolean {
        val state = instances[instanceId] ?: return false
        if (state.resourceId != request.resourceId || state.contentDigest != compiled.contentDigest) return false
        val normalizedClipIndex = normalizeClipIndex(clipIndex, compiled)
        if (state.clipIndex != normalizedClipIndex) state.ageTicks = 0L
        state.previousTransform = state.transform
        state.transform = request.transform
        state.clipIndex = normalizedClipIndex
        state.playbackSpeed = request.playbackSpeed
        return true
    }

    private fun normalizeClipIndex(
        clipIndex: Int,
        compiled: CooFxCompiledRenderPackage,
    ): Int {
        val normalized = if (clipIndex == -1) 0 else clipIndex
        require(normalized == 0 || normalized in compiled.clips.indices) {
            "Model clip index is out of range"
        }
        return normalized
    }

    fun stop(instanceId: Long): Boolean = instances.remove(instanceId) != null

    fun tick() {
        instances.values.forEach { state -> state.ageTicks++ }
    }

    fun buildBatches(
        partialTick: Float,
        lightResolver: (Double, Double, Double) -> Int = { _, _, _ -> 0 },
        assetResolver: (ResourceLocation) -> CooFxResolvedModelAsset?,
    ): List<CooFxMeshInstanceBatch> {
        require(partialTick.isFinite()) { "Partial tick must be finite" }
        val progress = partialTick.coerceIn(0F, 1F)
        val secondsPerTick = 0.05F
        val grouped = TreeMap<CooFxMeshBatchKey, MutableList<DrawInstance>>()
        instances.values.forEach { state ->
            val resolved = assetResolver(state.resourceId) ?: return@forEach
            val compiled = resolved.compiled
            if (compiled.contentDigest != state.contentDigest) return@forEach
            val packedLight = lightResolver(state.transform.x, state.transform.y, state.transform.z)
            require(packedLight in 0..0xFFFFFF) { "Resolved model packed light must fit in 24 bits" }
            val templatesByPrimitive = compiled.batchTemplatesByPrimitive
            val clipTimeSeconds = state.ageTicks.toFloat() * secondsPerTick * state.playbackSpeed
            val previousClipTimeSeconds = (state.ageTicks - 1L).coerceAtLeast(0L).toFloat() *
                secondsPerTick * state.playbackSpeed
            val sampledClipTime = previousClipTimeSeconds +
                (clipTimeSeconds - previousClipTimeSeconds) * progress
            compiled.modelPrimitiveNodeBindings.forEachIndexed { bindingIndex, binding ->
                val template = requireNotNull(templatesByPrimitive[binding.primitiveIndex]) {
                    "Model primitive binding has no batch template"
                }
                val key = template.bindGeneration(resolved.generation).copy(
                    backendCapabilitySignature = resolved.backendCapabilitySignature,
                )
                val stableDrawId = state.stableDrawIds[bindingIndex]
                val encoded = CooFxMeshInstanceLayout.encode(
                    CooFxMeshInstanceData(
                        currentPosition = CooFxFloat3(
                            state.transform.x.toFloat(),
                            state.transform.y.toFloat(),
                            state.transform.z.toFloat(),
                        ),
                        ageTicks = state.ageTicks.toFloat(),
                        previousPosition = CooFxFloat3(
                            state.previousTransform.x.toFloat(),
                            state.previousTransform.y.toFloat(),
                            state.previousTransform.z.toFloat(),
                        ),
                        lifetimeTicks = 0F,
                        currentRotation = CooFxQuaternion(
                            state.transform.rotationX,
                            state.transform.rotationY,
                            state.transform.rotationZ,
                            state.transform.rotationW,
                        ),
                        previousRotation = CooFxQuaternion(
                            state.previousTransform.rotationX,
                            state.previousTransform.rotationY,
                            state.previousTransform.rotationZ,
                            state.previousTransform.rotationW,
                        ),
                        currentScale = CooFxFloat3(
                            state.transform.scaleX,
                            state.transform.scaleY,
                            state.transform.scaleZ,
                        ),
                        packedLight = packedLight,
                        previousScale = CooFxFloat3(
                            state.previousTransform.scaleX,
                            state.previousTransform.scaleY,
                            state.previousTransform.scaleZ,
                        ),
                        materialVariant = template.materialIndex,
                        color = CooFxColor(1F, 1F, 1F, 1F),
                        clipTimeSeconds = clipTimeSeconds,
                        previousClipTimeSeconds = previousClipTimeSeconds,
                        playbackSpeed = state.playbackSpeed,
                        clipIndex = state.clipIndex,
                        visibleSeed = state.requestSeed.toUInt(),
                        flags = 0,
                        stableParticleIdLow24 = (stableDrawId and 0xFFFFFFL).toInt(),
                    )
                )
                val nodeMatrix = compiled.nodeWorldMatrix(
                    clipIndex = state.clipIndex,
                    nodeIndex = binding.nodeIndex,
                    rawTimeSeconds = sampledClipTime,
                )
                grouped.getOrPut(key, ::mutableListOf) += DrawInstance(
                    stableDrawId = stableDrawId,
                    instanceData = encoded,
                    nodeMatrixData = encodeAffineMatrix(nodeMatrix),
                )
            }
        }
        return grouped.map { (key, drawInstances) ->
            drawInstances.sortBy(DrawInstance::stableDrawId)
            val nodeMatrixFloatCount = 12
            val instanceData = FloatArray(drawInstances.size * CooFxMeshInstanceLayout.FLOAT_COUNT)
            val nodeMatrixData = FloatArray(drawInstances.size * nodeMatrixFloatCount)
            val stableDrawIds = LongArray(drawInstances.size)
            drawInstances.forEachIndexed { index, drawInstance ->
                drawInstance.instanceData.copyInto(
                    destination = instanceData,
                    destinationOffset = index * CooFxMeshInstanceLayout.FLOAT_COUNT,
                )
                drawInstance.nodeMatrixData.copyInto(
                    destination = nodeMatrixData,
                    destinationOffset = index * nodeMatrixFloatCount,
                )
                stableDrawIds[index] = drawInstance.stableDrawId
            }
            CooFxMeshInstanceBatch(
                key = key,
                instanceCount = drawInstances.size,
                instanceData = instanceData,
                stableParticleIds = stableDrawIds,
                nodeMatrixData = nodeMatrixData,
            )
        }
    }

    fun clear() {
        instances.clear()
    }

    private fun encodeAffineMatrix(matrix: Matrix4fc): FloatArray = floatArrayOf(
        matrix.m00(), matrix.m10(), matrix.m20(), matrix.m30(),
        matrix.m01(), matrix.m11(), matrix.m21(), matrix.m31(),
        matrix.m02(), matrix.m12(), matrix.m22(), matrix.m32(),
    )

    private data class ModelState(
        val resourceId: ResourceLocation,
        var contentDigest: String,
        var transform: CooFxWorldTransform,
        var previousTransform: CooFxWorldTransform = transform,
        var requestSeed: Long,
        var clipIndex: Int,
        var playbackSpeed: Float,
        val stableDrawIds: LongArray,
        var ageTicks: Long = 0L,
    )

    private data class DrawInstance(
        val stableDrawId: Long,
        val instanceData: FloatArray,
        val nodeMatrixData: FloatArray,
    )
}
