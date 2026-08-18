package cn.coostack.cooparticlesapi.coofx.runtime.mesh

import cn.coostack.cooparticlesapi.coofx.runtime.mesh.render.CooFxMeshBatchKey
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxAlphaMode
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxBlendMode
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxCullMode
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxDeformationMode
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxDepthTest
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxIndexType
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxLightMode
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.storage.CooFxMeshInstanceLayout
import net.minecraft.resources.ResourceLocation
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class CooFxMeshDeterminismTest {
    @Test
    fun `same seed and tick input produce identical simulation and instance bytes`() {
        val first = simulate(requestSeed = 9988L)
        val second = simulate(requestSeed = 9988L)

        assertEquals(first.store.stableSnapshot(), second.store.stableSnapshot())
        val firstBatches = first.buildBatches()
        val secondBatches = second.buildBatches()
        assertEquals(firstBatches.map { it.key }, secondBatches.map { it.key })
        firstBatches.zip(secondBatches).forEach { (left, right) ->
            assertContentEquals(left.stableParticleIds, right.stableParticleIds)
            assertContentEquals(left.instanceData, right.instanceData)
        }
    }

    @Test
    fun `different request seed changes deterministic particle channels`() {
        val first = simulate(requestSeed = 9988L).store.stableSnapshot()
        val second = simulate(requestSeed = 9989L).store.stableSnapshot()

        assertNotEquals(first, second)
    }

    @Test
    fun `local particles encode previous and current emitter transform without changing batch key`() {
        val manager = CooFxMeshParticleManager(8)
        val definition = definition(
            emissionMode = CooFxMeshEmissionMode.BURST,
            simulationSpace = CooFxMeshSimulationSpace.LOCAL,
            emissionCount = 1,
        )
        val runtimeId = manager.startEmitter(
            definition,
            CooFxMeshEmitterTransform(position = Vector3f(2F, 0F, 0F)),
            assetSeed = 1L,
            requestSeed = 2L,
        )
        manager.tick()
        manager.updateEmitterTransform(
            runtimeId,
            CooFxMeshEmitterTransform(position = Vector3f(5F, 0F, 0F)),
        )

        val batch = manager.buildBatches().single()

        val localX = manager.store.stableSnapshot().single().position.x
        assertEquals(5F + localX, batch.instanceData[CooFxMeshInstanceLayout.CURRENT_POSITION])
        assertEquals(2F + localX, batch.instanceData[CooFxMeshInstanceLayout.PREVIOUS_POSITION])
        assertEquals(true, batch.key in definition.variants.map { it.batchKey })
    }

    @Test
    fun `stopped emitter is reclaimed after its final particle expires`() {
        val manager = CooFxMeshParticleManager(4)
        val runtimeId = manager.startEmitter(
            definition(CooFxMeshEmissionMode.BURST, CooFxMeshSimulationSpace.WORLD, 1),
            CooFxMeshEmitterTransform(),
            assetSeed = 1L,
            requestSeed = 2L,
        )
        manager.tick()
        manager.stopEmitter(runtimeId)

        repeat(25) { manager.tick() }

        assertEquals(0, manager.particleCount)
        assertEquals(false, manager.stopEmitter(runtimeId))
    }

    @Test
    fun `capacity exhaustion is exposed through drop count`() {
        val manager = CooFxMeshParticleManager(1)
        manager.startEmitter(
            definition(CooFxMeshEmissionMode.BURST, CooFxMeshSimulationSpace.WORLD, 3),
            CooFxMeshEmitterTransform(),
            assetSeed = 1L,
            requestSeed = 2L,
        )

        manager.tick()

        assertEquals(1, manager.particleCount)
        assertEquals(2L, manager.droppedParticleCount)
    }

    private fun simulate(requestSeed: Long): CooFxMeshParticleManager {
        val manager = CooFxMeshParticleManager(64)
        manager.startEmitter(
            definition(
                emissionMode = CooFxMeshEmissionMode.CONTINUOUS,
                simulationSpace = CooFxMeshSimulationSpace.WORLD,
                emissionCount = 0,
            ),
            CooFxMeshEmitterTransform(
                position = Vector3f(3F, 4F, 5F),
                rotation = Quaternionf().rotationY(0.4F),
            ),
            assetSeed = 123456L,
            requestSeed = requestSeed,
        )
        repeat(8) { manager.tick() }
        return manager
    }

    private fun definition(
        emissionMode: CooFxMeshEmissionMode,
        simulationSpace: CooFxMeshSimulationSpace,
        emissionCount: Int,
    ) = CooFxMeshEmitterDefinition(
        emitterId = "deterministic_emitter",
        simulationSpace = simulationSpace,
        emissionMode = emissionMode,
        selectionMode = CooFxMeshSelectionMode.COLLECTION,
        variants = listOf(
            CooFxMeshVariant(batchKey(0), meshVariant = 0, materialVariant = 2),
            CooFxMeshVariant(batchKey(1), meshVariant = 1, materialVariant = 3),
        ),
        delayTicks = 0,
        durationTicks = 4,
        emissionCount = emissionCount,
        particlesPerTick = 1.5F,
        lifetimeTicks = 16..24,
        position = CooFxMeshVectorRange(Vector3f(-1F), Vector3f(1F)),
        velocity = CooFxMeshVectorRange(Vector3f(-0.2F, 0.1F, -0.2F), Vector3f(0.2F, 0.5F, 0.2F)),
        acceleration = CooFxMeshVectorRange(Vector3f(0F, -0.01F, 0F), Vector3f(0F, -0.01F, 0F)),
        rotationRadians = CooFxMeshVectorRange(Vector3f(), Vector3f(0F, 1F, 0F)),
        angularVelocityRadians = CooFxMeshVectorRange(Vector3f(-0.02F), Vector3f(0.02F)),
        scale = CooFxMeshVectorRange(Vector3f(0.5F), Vector3f(1.5F)),
        color = CooFxMeshColorRange(Vector4f(0.2F, 0.4F, 0.6F, 0.5F), Vector4f(1F)),
        packedLight = 15728880,
        clipIndex = 2,
        playbackSpeed = 1.25F,
        forces = CooFxMeshForces(
            gravity = Vector3f(0F, -0.04F, 0F),
            wind = Vector3f(0.01F, 0F, -0.02F),
            drag = 0.03F,
            noiseAmplitude = Vector3f(0.005F),
            noiseFrequencyTicks = 2,
        ),
    )

    private fun batchKey(meshPrimitiveId: Int) = CooFxMeshBatchKey(
        generation = 7L,
        pipelineId = ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "coofx/world"),
        worldNodeId = "world",
        primitiveId = "primitive_$meshPrimitiveId",
        vertexLayoutVersion = 1,
        indexType = CooFxIndexType.UNSIGNED_INT,
        materialId = "material_$meshPrimitiveId",
        baseColorTexture = ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "textures/coofx/test.png"),
        samplerKey = "linear_repeat",
        shaderVariant = "rigid",
        deformationMode = CooFxDeformationMode.RIGID,
        alphaMode = CooFxAlphaMode.MASK,
        alphaCutoffBucket = 128,
        cullMode = CooFxCullMode.BACK,
        depthTest = CooFxDepthTest.LESS_OR_EQUAL,
        depthWrite = true,
        blendMode = CooFxBlendMode.DISABLED,
        lightMode = CooFxLightMode.WORLD,
        backendCapabilitySignature = "gl33",
        instanceLayoutVersion = CooFxMeshInstanceLayout.VERSION,
    )
}
