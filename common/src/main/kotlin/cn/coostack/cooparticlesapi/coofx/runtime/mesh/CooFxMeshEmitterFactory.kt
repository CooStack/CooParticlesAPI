package cn.coostack.cooparticlesapi.coofx.runtime.mesh

import cn.coostack.cooparticlesapi.coofx.asset.CooFxFloat3
import cn.coostack.cooparticlesapi.coofx.asset.CooFxFloat3Range
import cn.coostack.cooparticlesapi.coofx.asset.CooFxSourceAsset
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxCompiledRenderPackage
import org.joml.Vector3f

/**
 * 把 importer 保留的发射语义与 compiler 生成的批次模板合并为可执行网格发射器。
 *
 * 该工厂只构造 CPU 定义，不访问 OpenGL。package generation 由 GPU registry 分配后传入，
 * 因此生成粒子的 batch key 不会携带 compiler 使用的占位 generation。
 */
object CooFxMeshEmitterFactory {
    fun create(
        source: CooFxSourceAsset,
        compiledPackage: CooFxCompiledRenderPackage,
        emitterId: String,
        generation: Long,
        backendCapabilitySignature: String,
    ): CooFxMeshEmitterDefinition {
        require(source.resource == compiledPackage.id) { "Source asset 与 compiled package 的资源 ID 不一致" }
        val sourceEmitter = source.emitters.singleOrNull { it.id == emitterId }
            ?: throw IllegalArgumentException("找不到 CooFX emitter：$emitterId")
        val compiledEmitter = compiledPackage.emitters.singleOrNull { it.id == emitterId }
            ?: throw IllegalArgumentException("Compiled package 找不到 emitter：$emitterId")
        val templatesByPrimitive = compiledPackage.batchTemplates.associateBy { it.primitiveIndex }
        val variants = compiledEmitter.primitiveNodeBindings.mapIndexed { meshVariant, binding ->
            val template = templatesByPrimitive[binding.primitiveIndex]
                ?: throw IllegalArgumentException("Emitter $emitterId 的 primitive[${binding.primitiveIndex}] 没有 batch template")
            CooFxMeshVariant(
                batchKey = template.bindGeneration(generation).copy(
                    backendCapabilitySignature = backendCapabilitySignature,
                ),
                meshVariant = meshVariant,
                materialVariant = template.materialIndex,
                nodeIndex = binding.nodeIndex,
            )
        }

        return CooFxMeshEmitterDefinition(
            emitterId = sourceEmitter.id,
            simulationSpace = CooFxMeshSimulationSpace.WORLD,
            emissionMode = CooFxMeshEmissionMode.BURST,
            selectionMode = if (variants.size == 1) CooFxMeshSelectionMode.OBJECT else CooFxMeshSelectionMode.ALL,
            variants = variants,
            delayTicks = sourceEmitter.delayTicks,
            durationTicks = 1,
            emissionCount = sourceEmitter.count,
            lifetimeTicks = sourceEmitter.lifetimeTicks..sourceEmitter.lifetimeTicks,
            velocity = sourceEmitter.velocity.toRuntimeRange(),
            rotationRadians = sourceEmitter.rotationRadians.toRuntimeRange(),
            scale = sourceEmitter.scale.toRuntimeRange(),
            clipIndex = compiledEmitter.defaultClipIndex ?: 0,
        )
    }

    private fun CooFxFloat3Range.toRuntimeRange(): CooFxMeshVectorRange = CooFxMeshVectorRange(
        minimum = minimum.toVector(),
        maximum = maximum.toVector(),
    )

    private fun CooFxFloat3.toVector(): Vector3f = Vector3f(x, y, z)
}
