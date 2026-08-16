package cn.coostack.cooparticlesapi.coofx.render.batch

import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxAlphaMode
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxBlendMode
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxCullMode
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxDeformationMode
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxDepthTest
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxIndexType
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxLightMode
import net.minecraft.resources.ResourceLocation

/**
 * CooFX 网格实例的稳定批次键。
 *
 * 字段只描述一次绘制共享的资源与光栅状态，按声明顺序参与稳定排序。实例位置、年龄、seed、颜色、
 * clip time、粒子 ID 和相机距离不得进入该值对象。首版只接受 OPAQUE 与 MASK，因此不承担透明排序。
 */
data class CooFxMeshBatchKey(
    val generation: Long,
    val pipelineId: ResourceLocation,
    val worldNodeId: String,
    val primitiveId: String,
    val vertexLayoutVersion: Int,
    val indexType: CooFxIndexType,
    val materialId: String,
    val baseColorTexture: ResourceLocation?,
    val samplerKey: String,
    val shaderVariant: String,
    val deformationMode: CooFxDeformationMode,
    val alphaMode: CooFxAlphaMode,
    val alphaCutoffBucket: Int,
    val cullMode: CooFxCullMode,
    val depthTest: CooFxDepthTest,
    val depthWrite: Boolean,
    val blendMode: CooFxBlendMode,
    val lightMode: CooFxLightMode,
    val backendCapabilitySignature: String,
    val instanceLayoutVersion: Int
) : Comparable<CooFxMeshBatchKey> {
    init {
        require(generation > 0L) { "Generation must be positive" }
        require(worldNodeId.isNotBlank()) { "World node id must not be blank" }
        require(primitiveId.isNotBlank()) { "Primitive id must not be blank" }
        require(vertexLayoutVersion > 0) { "Vertex layout version must be positive" }
        require(materialId.isNotBlank()) { "Material id must not be blank" }
        require(samplerKey.isNotBlank()) { "Sampler key must not be blank" }
        require(shaderVariant.isNotBlank()) { "Shader variant must not be blank" }
        require(alphaCutoffBucket in 0..255) { "Alpha cutoff bucket must be between 0 and 255" }
        require(alphaMode == CooFxAlphaMode.MASK || alphaCutoffBucket == 0) {
            "Opaque batch must use zero alpha cutoff bucket"
        }
        require(backendCapabilitySignature.isNotBlank()) { "Backend capability signature must not be blank" }
        require(instanceLayoutVersion > 0) { "Instance layout version must be positive" }
    }

    override fun compareTo(other: CooFxMeshBatchKey): Int = compareValuesBy(
        this,
        other,
        CooFxMeshBatchKey::generation,
        { it.pipelineId.toString() },
        CooFxMeshBatchKey::worldNodeId,
        CooFxMeshBatchKey::primitiveId,
        CooFxMeshBatchKey::vertexLayoutVersion,
        CooFxMeshBatchKey::indexType,
        CooFxMeshBatchKey::materialId,
        { it.baseColorTexture?.toString().orEmpty() },
        CooFxMeshBatchKey::samplerKey,
        CooFxMeshBatchKey::shaderVariant,
        CooFxMeshBatchKey::deformationMode,
        CooFxMeshBatchKey::alphaMode,
        CooFxMeshBatchKey::alphaCutoffBucket,
        CooFxMeshBatchKey::cullMode,
        CooFxMeshBatchKey::depthTest,
        CooFxMeshBatchKey::depthWrite,
        CooFxMeshBatchKey::blendMode,
        CooFxMeshBatchKey::lightMode,
        CooFxMeshBatchKey::backendCapabilitySignature,
        CooFxMeshBatchKey::instanceLayoutVersion
    )
}

data class CooFxBatchTemplate(
    val keyWithoutGeneration: CooFxMeshBatchKey,
    val primitiveIndex: Int,
    val materialIndex: Int
) {
    init {
        require(keyWithoutGeneration.generation == 1L) {
            "Batch template uses generation 1 as an unbound placeholder"
        }
        require(primitiveIndex >= 0) { "Primitive index must not be negative" }
        require(materialIndex >= 0) { "Material index must not be negative" }
    }

    fun bindGeneration(generation: Long): CooFxMeshBatchKey = keyWithoutGeneration.copy(generation = generation)
}

data class CooFxInstanceDrawRange(
    val key: CooFxMeshBatchKey,
    val firstInstance: Int,
    val instanceCount: Int
) {
    init {
        require(firstInstance >= 0) { "First instance must not be negative" }
        require(instanceCount > 0) { "Instance count must be positive" }
    }
}
