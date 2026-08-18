package cn.coostack.cooparticlesapi.coofx.runtime.mesh

import cn.coostack.cooparticlesapi.coofx.runtime.mesh.render.CooFxMeshBatchKey
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.render.CooFxMeshBatcher
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.render.CooFxMeshEmitterTransformHistory
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.storage.CooFxMeshInstanceLayout
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.storage.CooFxMeshParticleStore
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxAlphaMode
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxBlendMode
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxCullMode
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxDeformationMode
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxDepthTest
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxIndexType
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxLightMode
import net.minecraft.resources.ResourceLocation
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class CooFxMeshBatcherTest {
    @Test
    fun `layout is nine vec4 and one hundred forty four bytes`() {
        assertEquals(9, CooFxMeshInstanceLayout.VECTOR_COUNT)
        assertEquals(36, CooFxMeshInstanceLayout.FLOAT_COUNT)
        assertEquals(144, CooFxMeshInstanceLayout.BYTE_STRIDE)
        val attributes = CooFxMeshInstanceLayout.attributes(5)
        assertEquals((5..13).toList(), attributes.map { it.shaderLocation })
        assertEquals((0 until 144 step 16).toList(), attributes.map { it.byteOffset })
        assertEquals(setOf(CooFxMeshInstanceLayout.BYTE_STRIDE), attributes.map { CooFxMeshInstanceLayout.BYTE_STRIDE }.toSet())
        assertEquals(setOf(1), attributes.map { it.divisor }.toSet())
    }

    @Test
    fun `batcher sorts keys and stable ids while instance fields stay outside key`() {
        val store = CooFxMeshParticleStore(4)
        val secondKey = batchKey(meshPrimitiveId = 2)
        val firstKey = batchKey(meshPrimitiveId = 1)
        store.spawn(spawn(stableId = 20L, key = firstKey, positionX = 2F))
        store.spawn(spawn(stableId = 30L, key = secondKey, positionX = 3F))
        store.spawn(spawn(stableId = 10L, key = firstKey, positionX = 1F))

        val batches = CooFxMeshBatcher().build(store) { null }

        assertEquals(listOf(firstKey, secondKey), batches.map { it.key })
        assertContentEquals(longArrayOf(10L, 20L), batches.first().stableParticleIds)
        assertEquals(1F, batches.first().instanceData[CooFxMeshInstanceLayout.CURRENT_POSITION])
        assertEquals(2F, batches.first().instanceData[CooFxMeshInstanceLayout.FLOAT_COUNT])
    }

    @Test
    fun `batcher resolves packed light from current particle world position`() {
        val store = CooFxMeshParticleStore(1)
        store.spawn(spawn(stableId = 1L, key = batchKey(meshPrimitiveId = 1), positionX = 7F))

        val batch = CooFxMeshBatcher().build(
            store = store,
            packedLightResolver = { position ->
                assertEquals(Vector3f(7F, 0F, 0F), position)
                0x123456
            },
            transformResolver = { null },
        ).single()

        assertEquals(
            0x123456.toFloat(),
            batch.instanceData[CooFxMeshInstanceLayout.CURRENT_SCALE + 3],
        )
    }

    @Test
    fun `batcher resolves local particle light after emitter transform`() {
        val store = CooFxMeshParticleStore(1)
        store.spawn(
            spawn(stableId = 1L, key = batchKey(meshPrimitiveId = 1), positionX = 2F)
                .copy(simulationSpace = CooFxMeshSimulationSpace.LOCAL)
        )
        val transform = CooFxMeshEmitterTransform(position = Vector3f(5F, 0F, 0F))

        val batch = CooFxMeshBatcher().build(
            store = store,
            packedLightResolver = { position ->
                assertEquals(Vector3f(7F, 0F, 0F), position)
                0x654321
            },
            transformResolver = { CooFxMeshEmitterTransformHistory(transform, transform) },
        ).single()

        assertEquals(
            0x654321.toFloat(),
            batch.instanceData[CooFxMeshInstanceLayout.CURRENT_SCALE + 3],
        )
    }

    private fun spawn(stableId: Long, key: CooFxMeshBatchKey, positionX: Float) = CooFxMeshParticleSpawn(
        stableParticleId = stableId,
        particleSeed = stableId * 17L,
        emitterRuntimeId = 0L,
        simulationSpace = CooFxMeshSimulationSpace.WORLD,
        batchKey = key,
        position = Vector3f(positionX, 0F, 0F),
        velocity = Vector3f(),
        acceleration = Vector3f(),
        rotation = Quaternionf(),
        angularVelocityRadians = Vector3f(),
        scale = Vector3f(1F),
        lifetimeTicks = 20,
        color = Vector4f(1F),
        packedLight = 0,
        clipIndex = 0,
        playbackSpeed = 1F,
        meshVariant = 0,
        materialVariant = 0,
        forces = CooFxMeshForces(),
    )

    private fun batchKey(meshPrimitiveId: Int) = CooFxMeshBatchKey(
        generation = 1L,
        pipelineId = ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "coofx/world"),
        worldNodeId = "world",
        primitiveId = "primitive_$meshPrimitiveId",
        vertexLayoutVersion = 1,
        indexType = CooFxIndexType.UNSIGNED_INT,
        materialId = "material_0",
        baseColorTexture = ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "textures/coofx/test.png"),
        samplerKey = "linear_repeat",
        shaderVariant = "rigid",
        deformationMode = CooFxDeformationMode.RIGID,
        alphaMode = CooFxAlphaMode.OPAQUE,
        alphaCutoffBucket = 0,
        cullMode = CooFxCullMode.BACK,
        depthTest = CooFxDepthTest.LESS_OR_EQUAL,
        depthWrite = true,
        blendMode = CooFxBlendMode.DISABLED,
        lightMode = CooFxLightMode.WORLD,
        backendCapabilitySignature = "gl33",
        instanceLayoutVersion = CooFxMeshInstanceLayout.VERSION,
    )
}
