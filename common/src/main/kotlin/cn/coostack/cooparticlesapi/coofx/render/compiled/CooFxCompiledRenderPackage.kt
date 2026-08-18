package cn.coostack.cooparticlesapi.coofx.render.compiled

import cn.coostack.cooparticlesapi.coofx.playback.pose.CooFxPoseEvaluator
import cn.coostack.cooparticlesapi.coofx.playback.pose.CooFxPoseNode
import cn.coostack.cooparticlesapi.coofx.playback.pose.CooFxTransformTrack
import cn.coostack.cooparticlesapi.coofx.render.batch.CooFxBatchTemplate
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix4f
import kotlin.math.abs

class CooFxCompiledPrimitive(
    val id: String,
    val vertexLayout: CooFxVertexLayout,
    vertexBytes: ByteArray,
    val indexType: CooFxIndexType,
    indexBytes: ByteArray,
    val drawRange: CooFxDrawRange,
    val materialIndex: Int,
    val deformationPlan: CooFxDeformationPlan
) {
    val vertexBytes = CooFxImmutableBytes(vertexBytes)
    val indexBytes = CooFxImmutableBytes(indexBytes)

    init {
        require(id.isNotBlank()) { "Primitive id must not be blank" }
        require(vertexBytes.isNotEmpty()) { "Primitive vertex bytes must not be empty" }
        require(vertexBytes.size % vertexLayout.strideBytes == 0) {
            "Primitive vertex bytes must align to vertex stride"
        }
        require(indexBytes.isNotEmpty()) { "Primitive index bytes must not be empty" }
        require(indexBytes.size % indexType.byteSize == 0) { "Primitive index bytes must align to index type" }
        require(drawRange.firstIndex + drawRange.indexCount <= indexBytes.size / indexType.byteSize) {
            "Draw range exceeds primitive index data"
        }
        require(materialIndex >= 0) { "Primitive material index must not be negative" }
    }
}

/**
 * compiled package 中稳定保存的 clip 循环策略。
 *
 * [ONCE] 只播放一次；[LOOP] 从末尾回到开头；[PING_PONG] 在首尾之间往返。
 */
enum class CooFxCompiledLoopMode {
    /** 只播放一次并停留在末帧。 */
    ONCE,

    /** 按 duration 周期循环。 */
    LOOP,

    /** 按两倍 duration 周期往返。 */
    PING_PONG,
}

data class CooFxCompiledClipMetadata(
    val id: String,
    val durationSeconds: Float,
    val animatedNodeIndices: List<Int>,
    val loopMode: CooFxCompiledLoopMode = CooFxCompiledLoopMode.ONCE,
    val transformTracks: List<CooFxTransformTrack> = emptyList(),
) {
    init {
        require(id.isNotBlank()) { "Clip id must not be blank" }
        require(durationSeconds.isFinite() && durationSeconds > 0.0F) {
            "Clip duration must be finite and positive"
        }
        require(animatedNodeIndices.all { it >= 0 }) { "Animated node index must not be negative" }
        require(animatedNodeIndices.distinct().size == animatedNodeIndices.size) {
            "Animated node indices must not contain duplicates"
        }
        require(transformTracks.map(CooFxTransformTrack::nodeIndex).distinct().size == transformTracks.size) {
            "Clip transform tracks must contain at most one track per node"
        }
    }

    fun sampleTime(rawTimeSeconds: Float): Float {
        require(rawTimeSeconds.isFinite()) { "Raw clip time must be finite" }
        return when (loopMode) {
            CooFxCompiledLoopMode.ONCE -> rawTimeSeconds.coerceIn(0F, durationSeconds)
            CooFxCompiledLoopMode.LOOP -> positiveRemainder(rawTimeSeconds, durationSeconds)
            CooFxCompiledLoopMode.PING_PONG -> {
                val phase = positiveRemainder(rawTimeSeconds, durationSeconds * 2F)
                if (phase <= durationSeconds) phase else durationSeconds * 2F - phase
            }
        }
    }

    private fun positiveRemainder(value: Float, modulus: Float): Float {
        val remainder = value % modulus
        return if (remainder < 0F && abs(remainder) > 0F) remainder + modulus else remainder
    }
}

data class CooFxPrimitiveNodeBinding(
    val primitiveIndex: Int,
    val nodeIndex: Int,
) {
    init {
        require(primitiveIndex >= 0) { "Primitive binding index must not be negative" }
        require(nodeIndex >= 0) { "Primitive binding node index must not be negative" }
    }
}

/** compiled package 中不依赖 glTF JSON 的摄像机投影参数。 */
sealed interface CooFxCompiledCameraProjection {
    data class Perspective(
        val yfovRadians: Float,
        val aspectRatio: Float?,
        val znear: Float,
        val zfar: Float?,
    ) : CooFxCompiledCameraProjection

    data class Orthographic(
        val xmag: Float,
        val ymag: Float,
        val znear: Float,
        val zfar: Float,
    ) : CooFxCompiledCameraProjection
}

/** 选定 scene 中一个 camera node 的稳定 compiled 绑定。 */
data class CooFxCompiledCamera(
    val id: String,
    val name: String?,
    val nodeIndex: Int,
    val projection: CooFxCompiledCameraProjection,
) {
    init {
        require(id.isNotBlank()) { "Compiled camera id must not be blank" }
        require(nodeIndex >= 0) { "Compiled camera node index must not be negative" }
    }
}

data class CooFxCompiledEmitter(
    val id: String,
    val primitiveIndex: Int,
    val defaultClipIndex: Int?,
    val primitiveIndices: List<Int> = listOf(primitiveIndex),
    val primitiveNodeBindings: List<CooFxPrimitiveNodeBinding> = primitiveIndices.map { primitive ->
        CooFxPrimitiveNodeBinding(primitive, 0)
    },
) {
    init {
        require(id.isNotBlank()) { "Emitter id must not be blank" }
        require(primitiveIndex >= 0) { "Emitter primitive index must not be negative" }
        require(primitiveIndices.isNotEmpty() && primitiveIndices.all { it >= 0 }) {
            "Emitter primitive indices must be non-empty and non-negative"
        }
        require(primitiveIndices.first() == primitiveIndex && primitiveIndices.distinct().size == primitiveIndices.size) {
            "Emitter primary primitive must be first and primitive indices must not contain duplicates"
        }
        require(defaultClipIndex == null || defaultClipIndex >= 0) {
            "Emitter default clip index must not be negative"
        }
        require(primitiveNodeBindings.map(CooFxPrimitiveNodeBinding::primitiveIndex) == primitiveIndices) {
            "Emitter primitive bindings must preserve primitive order"
        }
    }
}

class CooFxCompiledRenderPackage(
    val id: ResourceLocation,
    val sourceSchemaVersion: Int,
    val compilerVersion: String,
    val contentDigest: String,
    nodeParents: List<Int>,
    topologicalNodeOrder: List<Int>,
    poseNodes: List<CooFxPoseNode>,
    clips: List<CooFxCompiledClipMetadata>,
    primitives: List<CooFxCompiledPrimitive>,
    materials: List<CooFxCompiledMaterial>,
    emitters: List<CooFxCompiledEmitter>,
    batchTemplates: List<CooFxBatchTemplate>,
    warnings: List<String>,
    modelPrimitiveNodeBindings: List<CooFxPrimitiveNodeBinding> = primitives.mapIndexed { primitiveIndex, primitive ->
        CooFxPrimitiveNodeBinding(primitiveIndex, primitive.deformationPlan.primitiveNodeIndex)
    },
    cameras: List<CooFxCompiledCamera> = emptyList(),
) {
    val nodeParents: List<Int> = nodeParents.toList()
    val topologicalNodeOrder: List<Int> = topologicalNodeOrder.toList()
    val poseNodes: List<CooFxPoseNode> = poseNodes.toList()
    val clips: List<CooFxCompiledClipMetadata> = clips.map { clip ->
        clip.copy(
            animatedNodeIndices = clip.animatedNodeIndices.toList(),
            transformTracks = clip.transformTracks.toList(),
        )
    }
    private val poseEvaluator = CooFxPoseEvaluator(this.poseNodes)
    val primitives: List<CooFxCompiledPrimitive> = primitives.toList()
    val materials: List<CooFxCompiledMaterial> = materials.toList()
    val emitters: List<CooFxCompiledEmitter> = emitters.map { emitter ->
        emitter.copy(primitiveIndices = emitter.primitiveIndices.toList())
    }
    val batchTemplates: List<CooFxBatchTemplate> = batchTemplates.toList()
    val batchTemplatesByPrimitive: Map<Int, CooFxBatchTemplate> = this.batchTemplates.associateBy { template ->
        template.primitiveIndex
    }
    val warnings: List<String> = warnings.toList()
    val modelPrimitiveNodeBindings: List<CooFxPrimitiveNodeBinding> = modelPrimitiveNodeBindings.toList()
    val cameras: List<CooFxCompiledCamera> = cameras.toList()

    init {
        require(sourceSchemaVersion > 0) { "Source schema version must be positive" }
        require(compilerVersion.isNotBlank()) { "Compiler version must not be blank" }
        require(contentDigest.matches(Regex("[0-9a-f]{64}"))) {
            "Content digest must be a lowercase SHA-256 value"
        }
        require(this.nodeParents.isNotEmpty()) { "Compiled package must contain nodes" }
        require(this.nodeParents.allIndexed { index, parent -> parent == -1 || parent in 0 until index }) {
            "Node parents must reference an earlier node or -1"
        }
        require(this.topologicalNodeOrder.size == this.nodeParents.size &&
            this.topologicalNodeOrder.toSet() == this.nodeParents.indices.toSet()) {
            "Topological node order must contain every node exactly once"
        }
        require(this.poseNodes.size == this.nodeParents.size) { "Pose node count must match topology" }
        require(this.primitives.isNotEmpty()) { "Compiled package must contain primitives" }
        require(this.materials.isNotEmpty()) { "Compiled package must contain materials" }
        require(this.primitives.all { it.materialIndex in this.materials.indices }) {
            "Primitive references an unknown material"
        }
        require(this.modelPrimitiveNodeBindings.isNotEmpty() && this.modelPrimitiveNodeBindings.all { binding ->
            binding.primitiveIndex in this.primitives.indices && binding.nodeIndex in this.poseNodes.indices
        }) { "Model scene references an unknown primitive or node" }
        require(this.cameras.map { camera -> camera.id }.distinct().size == this.cameras.size) {
            "Compiled camera ids must be unique"
        }
        require(this.cameras.all { camera -> camera.nodeIndex in this.poseNodes.indices }) {
            "Compiled camera references an unknown node"
        }
        require(this.emitters.all { emitter ->
            emitter.primitiveIndices.all { it in this.primitives.indices } &&
                emitter.primitiveNodeBindings.all { binding -> binding.nodeIndex in this.poseNodes.indices } &&
                (emitter.defaultClipIndex == null || emitter.defaultClipIndex in this.clips.indices)
        }) { "Emitter references an unknown primitive, node, or clip" }
        require(this.batchTemplates.all { template ->
            template.primitiveIndex in this.primitives.indices && template.materialIndex in this.materials.indices
        }) { "Batch template references an unknown primitive or material" }
    }

    /** 按稳定 ID 或唯一可读名称解析 scene camera；选择器为空时使用第一个。 */
    fun resolveCamera(selector: String?): CooFxCompiledCamera? {
        if (selector == null) return cameras.firstOrNull()
        cameras.singleOrNull { camera -> camera.id == selector }?.let { camera -> return camera }
        return cameras.singleOrNull { camera -> camera.name == selector }
    }

    fun nodeWorldMatrix(clipIndex: Int, nodeIndex: Int, rawTimeSeconds: Float): Matrix4f {
        require(nodeIndex in poseNodes.indices) { "Pose node index is out of range" }
        val clip = clips.getOrNull(clipIndex)
        val snapshot = if (clip == null) {
            poseEvaluator.evaluate(emptyList(), 0F)
        } else {
            poseEvaluator.evaluate(clip.transformTracks, clip.sampleTime(rawTimeSeconds))
        }
        return Matrix4f(snapshot.worldMatrices[nodeIndex])
    }

    private inline fun <T> List<T>.allIndexed(predicate: (Int, T) -> Boolean): Boolean {
        forEachIndexed { index, value ->
            if (!predicate(index, value)) {
                return false
            }
        }
        return true
    }
}
