package cn.coostack.cooparticlesapi.coofx.runtime.mesh.render

import cn.coostack.cooparticlesapi.coofx.runtime.mesh.CooFxMeshEmitterTransform
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.CooFxMeshSimulationSpace
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.storage.CooFxMeshInstanceLayout
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.storage.CooFxMeshParticleStore
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
)

/** 按稳定批次键和 stable particle id 构建连续 144-byte 实例区间。 */
class CooFxMeshBatcher {
    /**
     * 构建当前帧的稳定批次清单。
     *
     * [transformResolver] 的 key 是发射器运行实例 id；WORLD 粒子不会调用它。
     */
    fun build(
        store: CooFxMeshParticleStore,
        transformResolver: (Long) -> CooFxMeshEmitterTransformHistory?,
    ): List<CooFxMeshInstanceBatch> {
        val groupedIndices = TreeMap<CooFxMeshBatchKey, MutableList<Int>>()
        for (index in 0 until store.size) {
            groupedIndices.getOrPut(store.batchKey(index), ::mutableListOf).add(index)
        }
        return groupedIndices.map { (key, indices) ->
            indices.sortBy { store.stableParticleIds[it] }
            val instanceData = FloatArray(indices.size * CooFxMeshInstanceLayout.FLOAT_COUNT)
            val stableIds = LongArray(indices.size)
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
                )
                stableIds[outputIndex] = store.stableParticleIds[storeIndex]
            }
            CooFxMeshInstanceBatch(key, indices.size, instanceData, stableIds)
        }
    }
}
