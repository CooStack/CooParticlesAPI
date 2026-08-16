package cn.coostack.cooparticlesapi.coofx.render.compiled

import cn.coostack.cooparticlesapi.coofx.render.batch.CooFxBatchTemplate
import net.minecraft.resources.ResourceLocation

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

data class CooFxCompiledClipMetadata(
    val id: String,
    val durationSeconds: Float,
    val animatedNodeIndices: List<Int>
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
    }
}

data class CooFxCompiledEmitter(
    val id: String,
    val primitiveIndex: Int,
    val defaultClipIndex: Int?
) {
    init {
        require(id.isNotBlank()) { "Emitter id must not be blank" }
        require(primitiveIndex >= 0) { "Emitter primitive index must not be negative" }
        require(defaultClipIndex == null || defaultClipIndex >= 0) {
            "Emitter default clip index must not be negative"
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
    clips: List<CooFxCompiledClipMetadata>,
    primitives: List<CooFxCompiledPrimitive>,
    materials: List<CooFxCompiledMaterial>,
    emitters: List<CooFxCompiledEmitter>,
    batchTemplates: List<CooFxBatchTemplate>,
    warnings: List<String>
) {
    val nodeParents: List<Int> = nodeParents.toList()
    val topologicalNodeOrder: List<Int> = topologicalNodeOrder.toList()
    val clips: List<CooFxCompiledClipMetadata> = clips.map { it.copy(animatedNodeIndices = it.animatedNodeIndices.toList()) }
    val primitives: List<CooFxCompiledPrimitive> = primitives.toList()
    val materials: List<CooFxCompiledMaterial> = materials.toList()
    val emitters: List<CooFxCompiledEmitter> = emitters.toList()
    val batchTemplates: List<CooFxBatchTemplate> = batchTemplates.toList()
    val warnings: List<String> = warnings.toList()

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
        require(this.primitives.isNotEmpty()) { "Compiled package must contain primitives" }
        require(this.materials.isNotEmpty()) { "Compiled package must contain materials" }
        require(this.primitives.all { it.materialIndex in this.materials.indices }) {
            "Primitive references an unknown material"
        }
        require(this.emitters.all { emitter ->
            emitter.primitiveIndex in this.primitives.indices &&
                (emitter.defaultClipIndex == null || emitter.defaultClipIndex in this.clips.indices)
        }) { "Emitter references an unknown primitive or clip" }
        require(this.batchTemplates.all { template ->
            template.primitiveIndex in this.primitives.indices && template.materialIndex in this.materials.indices
        }) { "Batch template references an unknown primitive or material" }
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
