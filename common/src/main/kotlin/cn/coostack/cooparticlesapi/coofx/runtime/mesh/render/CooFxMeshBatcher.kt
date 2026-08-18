package cn.coostack.cooparticlesapi.coofx.runtime.mesh.render

import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxCompiledRenderPackage
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.CooFxMeshEmitterTransform
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.CooFxMeshSimulationSpace
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.storage.CooFxMeshInstanceLayout
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.storage.CooFxMeshParticleStore
import org.joml.Vector3f
import java.util.TreeMap

/** 局部粒子构建 previous/current 实例数据时使用的发射器变换历史。 */
data class CooFxMeshEmitterTransformHistory(
    val previous: CooFxMeshEmitterTransform,
    val current: CooFxMeshEmitterTransform,
)

/** 一个已经按 stable particle id 排序并编码完成的连续实例批次。 */
data class CooFxMeshInstanceBatch(
    val key: CooFxMeshBatchKey,
    val instanceCount: Int,
    val instanceData: FloatArray,
    val stableParticleIds: LongArray,
    val nodeMatrixData: FloatArray = identityAffineMatrices(instanceCount),
) {
    init {
        require(nodeMatrixData.size == instanceCount * 12) { "Node matrix sidecar must contain one affine mat4 per instance" }
    }

    companion object {
        private fun identityAffineMatrices(instanceCount: Int): FloatArray = FloatArray(instanceCount * 12).also { values ->
            repeat(instanceCount) { index ->
                val offset = index * 12
                values[offset] = 1F
                values[offset + 5] = 1F
                values[offset + 10] = 1F
            }
        }
    }
}

/** 按稳定批次键和 stable particle id 构建连续 144-byte 实例区间。 */
class CooFxMeshBatcher {
    /**
     * 构建当前帧的稳定批次清单。
     *
     * [transformResolver] 的 key 是发射器运行实例 id；WORLD 粒子不会调用它。
     */
    fun build(
        store: CooFxMeshParticleStore,
        partialTick: Float = 1F,
        poseResolver: (CooFxMeshBatchKey) -> CooFxCompiledRenderPackage? = { null },
        packedLightResolver: ((Vector3f) -> Int)? = null,
        transformResolver: (Long) -> CooFxMeshEmitterTransformHistory?,
    ): List<CooFxMeshInstanceBatch> {
        require(partialTick.isFinite()) { "Partial tick must be finite" }
        val groupedIndices = TreeMap<CooFxMeshBatchKey, MutableList<Int>>()
        for (index in 0 until store.size) {
            groupedIndices.getOrPut(store.batchKey(index), ::mutableListOf).add(index)
        }
        return groupedIndices.map { (key, indices) ->
            indices.sortBy { store.stableParticleIds[it] }
            val instanceData = FloatArray(indices.size * CooFxMeshInstanceLayout.FLOAT_COUNT)
            val nodeMatrixData = FloatArray(indices.size * 12)
            val stableIds = LongArray(indices.size)
            val posePackage = poseResolver(key)
            indices.forEachIndexed { outputIndex, storeIndex ->
                val history = if (store.simulationSpace(storeIndex) == CooFxMeshSimulationSpace.LOCAL) {
                    checkNotNull(transformResolver(store.emitterRuntimeIds[storeIndex])) {
                        "Missing transform for local mesh particle emitter"
                    }
                } else {
                    null
                }
                CooFxMeshInstanceLayout.write(
                    store = store,
                    index = storeIndex,
                    target = instanceData,
                    targetFloatOffset = outputIndex * CooFxMeshInstanceLayout.FLOAT_COUNT,
                    currentEmitterTransform = history?.current,
                    previousEmitterTransform = history?.previous,
                    packedLightResolver = packedLightResolver,
                )
                val matrixOffset = outputIndex * 12
                if (posePackage == null) {
                    nodeMatrixData[matrixOffset] = 1F
                    nodeMatrixData[matrixOffset + 5] = 1F
                    nodeMatrixData[matrixOffset + 10] = 1F
                } else {
                    val progress = partialTick.coerceIn(0F, 1F)
                    val rawClipTime = store.previousClipTimes[storeIndex] +
                        (store.clipTimes[storeIndex] - store.previousClipTimes[storeIndex]) * progress
                    val matrix = posePackage.nodeWorldMatrix(
                        clipIndex = store.clipIndices[storeIndex],
                        nodeIndex = store.nodeIndices[storeIndex],
                        rawTimeSeconds = rawClipTime,
                    )
                    nodeMatrixData[matrixOffset] = matrix.m00()
                    nodeMatrixData[matrixOffset + 1] = matrix.m10()
                    nodeMatrixData[matrixOffset + 2] = matrix.m20()
                    nodeMatrixData[matrixOffset + 3] = matrix.m30()
                    nodeMatrixData[matrixOffset + 4] = matrix.m01()
                    nodeMatrixData[matrixOffset + 5] = matrix.m11()
                    nodeMatrixData[matrixOffset + 6] = matrix.m21()
                    nodeMatrixData[matrixOffset + 7] = matrix.m31()
                    nodeMatrixData[matrixOffset + 8] = matrix.m02()
                    nodeMatrixData[matrixOffset + 9] = matrix.m12()
                    nodeMatrixData[matrixOffset + 10] = matrix.m22()
                    nodeMatrixData[matrixOffset + 11] = matrix.m32()
                }
                stableIds[outputIndex] = store.stableParticleIds[storeIndex]
            }
            CooFxMeshInstanceBatch(key, indices.size, instanceData, stableIds, nodeMatrixData)
        }
    }
}
