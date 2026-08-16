package cn.coostack.cooparticlesapi.coofx.runtime.mesh.render

import cn.coostack.cooparticlesapi.coofx.runtime.mesh.CooFxMeshAlphaMode
import net.minecraft.resources.ResourceLocation

/**
 * 网格实例的不可变批次键。
 *
 * 字段只描述资源身份和光栅状态；粒子 transform、age、seed、color、clip time 与 stable id
 * 必须留在实例数据中。自然顺序逐字段固定，不能依赖 map 或对象 identity。
 */
data class CooFxMeshBatchKey(
    val packageGeneration: Long,
    val pipelineId: ResourceLocation,
    val worldNodeId: String,
    val meshPrimitiveId: Int,
    val vertexLayoutVersion: Int,
    val indexType: Int,
    val materialId: Int,
    val baseColorTexture: ResourceLocation,
    val samplerKey: String,
    val shaderVariant: String,
    val deformationMode: String,
    val alphaMode: CooFxMeshAlphaMode,
    val alphaCutoffBucket: Int,
    val cullMode: String,
    val depthTest: Boolean,
    val depthWrite: Boolean,
    val blendMode: String,
    val lightMode: String,
    val backendCapabilitySignature: String,
    val instanceLayoutVersion: Int,
) : Comparable<CooFxMeshBatchKey> {
    init {
        require(packageGeneration >= 0L) { "Package generation must be non-negative" }
        require(worldNodeId.isNotBlank()) { "World node id must not be blank" }
        require(meshPrimitiveId >= 0) { "Mesh primitive id must be non-negative" }
        require(vertexLayoutVersion > 0) { "Vertex layout version must be positive" }
        require(materialId >= 0) { "Material id must be non-negative" }
        require(samplerKey.isNotBlank()) { "Sampler key must not be blank" }
        require(shaderVariant.isNotBlank()) { "Shader variant must not be blank" }
        require(deformationMode.isNotBlank()) { "Deformation mode must not be blank" }
        require(alphaMode != CooFxMeshAlphaMode.BLEND) { "Mesh particle alpha BLEND is not supported" }
        require(alphaCutoffBucket in 0..0xFFFFFF) { "Alpha cutoff bucket must fit in 24 bits" }
        require(cullMode.isNotBlank()) { "Cull mode must not be blank" }
        require(blendMode.isNotBlank()) { "Blend mode must not be blank" }
        require(lightMode.isNotBlank()) { "Light mode must not be blank" }
        require(backendCapabilitySignature.isNotBlank()) { "Backend signature must not be blank" }
        require(instanceLayoutVersion > 0) { "Instance layout version must be positive" }
    }

    override fun compareTo(other: CooFxMeshBatchKey): Int = compareValuesBy(
        this,
        other,
        CooFxMeshBatchKey::packageGeneration,
        { it.pipelineId.toString() },
        CooFxMeshBatchKey::worldNodeId,
        CooFxMeshBatchKey::meshPrimitiveId,
        CooFxMeshBatchKey::vertexLayoutVersion,
        CooFxMeshBatchKey::indexType,
        CooFxMeshBatchKey::materialId,
        { it.baseColorTexture.toString() },
        CooFxMeshBatchKey::samplerKey,
        CooFxMeshBatchKey::shaderVariant,
        CooFxMeshBatchKey::deformationMode,
        { it.alphaMode.ordinal },
        CooFxMeshBatchKey::alphaCutoffBucket,
        CooFxMeshBatchKey::cullMode,
        CooFxMeshBatchKey::depthTest,
        CooFxMeshBatchKey::depthWrite,
        CooFxMeshBatchKey::blendMode,
        CooFxMeshBatchKey::lightMode,
        CooFxMeshBatchKey::backendCapabilitySignature,
        CooFxMeshBatchKey::instanceLayoutVersion,
    )
}
