package cn.coostack.cooparticlesapi.coofx.runtime.mesh

import cn.coostack.cooparticlesapi.coofx.runtime.mesh.render.CooFxMeshBatchKey
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
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CooFxMeshParticleStoreTest {
    @Test
    fun `swap remove preserves moved particle identity and sorted snapshot`() {
        val store = CooFxMeshParticleStore(4)
        store.spawn(spawn(11L, 101L))
        store.spawn(spawn(7L, 102L))
        store.spawn(spawn(23L, 103L))

        assertEquals(23L, store.removeAt(1))
        assertEquals(2, store.size)
        assertEquals(listOf(11L, 23L), store.stableSnapshot().map { it.stableParticleId })
        assertNull(store.removeAt(1))
        assertEquals(listOf(11L), store.stableSnapshot().map { it.stableParticleId })
    }

    private fun spawn(stableId: Long, seed: Long) = CooFxMeshParticleSpawn(
        stableParticleId = stableId,
        particleSeed = seed,
        emitterRuntimeId = 1L,
        simulationSpace = CooFxMeshSimulationSpace.WORLD,
        batchKey = batchKey(),
        position = Vector3f(stableId.toFloat(), 0F, 0F),
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

    private fun batchKey() = CooFxMeshBatchKey(
        generation = 1L,
        pipelineId = ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "coofx/world"),
        worldNodeId = "world",
        primitiveId = "primitive_0",
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
        instanceLayoutVersion = 1,
    )
}
