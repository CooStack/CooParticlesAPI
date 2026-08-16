package cn.coostack.cooparticlesapi.coofx.playback.pose

import org.joml.Matrix4f
import org.joml.Matrix4fc

/** 一个稳定 node index 对应的父节点关系和绑定局部姿态。 */
data class CooFxPoseNode(
    val parentIndex: Int,
    val bindPose: CooFxLocalPose
)

/** 节点姿态采样结果，列表索引始终等于稳定 node index。 */
class CooFxPoseSnapshot(
    localPoses: List<CooFxLocalPose>,
    worldMatrices: List<Matrix4fc>
) {
    val localPoses = localPoses.toList()
    val worldMatrices = worldMatrices.map(::Matrix4f)
}

/** 按父节点先于子节点的稳定拓扑顺序计算 local TRS 和 world matrix。 */
class CooFxPoseEvaluator(nodes: List<CooFxPoseNode>) {
    private val nodes = nodes.toList()

    init {
        this.nodes.forEachIndexed { index, node ->
            require(node.parentIndex in -1 until index) { "节点必须按父节点先于子节点的拓扑顺序排列" }
        }
    }

    fun evaluate(tracks: List<CooFxTransformTrack>, timeSeconds: Float): CooFxPoseSnapshot {
        val tracksByNode = arrayOfNulls<CooFxTransformTrack>(nodes.size)
        tracks.forEach { track ->
            require(track.nodeIndex in nodes.indices) { "变换轨道引用了不存在的节点" }
            require(tracksByNode[track.nodeIndex] == null) { "同一节点只能提供一条变换轨道" }
            tracksByNode[track.nodeIndex] = track
        }
        val localPoses = nodes.mapIndexed { index, node ->
            tracksByNode[index]?.sample(timeSeconds, node.bindPose) ?: node.bindPose
        }
        val worldMatrices = ArrayList<Matrix4f>(nodes.size)
        nodes.forEachIndexed { index, node ->
            val pose = localPoses[index]
            val local = Matrix4f().translationRotateScale(pose.translation, pose.rotation, pose.scale)
            worldMatrices += if (node.parentIndex < 0) local else Matrix4f(worldMatrices[node.parentIndex]).mul(local)
        }
        return CooFxPoseSnapshot(localPoses, worldMatrices)
    }
}

/** 骨骼调色板的数据计划；每个骨骼引用稳定节点索引和对应逆绑定矩阵。 */
class CooFxBonePosePlan(
    nodeIndices: IntArray,
    inverseBindMatrices: List<Matrix4fc>
) {
    private val nodeIndices = nodeIndices.copyOf()
    private val inverseBindMatrices = inverseBindMatrices.map(::Matrix4f)

    init {
        require(this.nodeIndices.size == this.inverseBindMatrices.size) { "骨骼节点与逆绑定矩阵数量必须一致" }
        require(this.nodeIndices.all { index -> index >= 0 }) { "骨骼节点索引不能为负数" }
    }

    fun palette(snapshot: CooFxPoseSnapshot): List<Matrix4f> {
        return nodeIndices.indices.map { boneIndex ->
            val nodeIndex = nodeIndices[boneIndex]
            require(nodeIndex in snapshot.worldMatrices.indices) { "骨骼引用了不存在的姿态节点" }
            Matrix4f(snapshot.worldMatrices[nodeIndex]).mul(inverseBindMatrices[boneIndex])
        }
    }
}
