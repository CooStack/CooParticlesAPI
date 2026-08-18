package cn.coostack.cooparticlesapi.coofx.render.batch

import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxAlphaMode
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxBlendMode
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxCullMode
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxDeformationMode
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxDepthTest
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxIndexType
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxLightMode
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CooFxMeshBatchKeyTest {
    @Test
    fun `every shared resource state field splits a batch`() {
        val base = batchKey()

        assertNotEquals(base, base.copy(generation = 2L))
        assertNotEquals(base, base.copy(primitiveId = "other"))
        assertNotEquals(base, base.copy(materialId = "other"))
        assertNotEquals(base, base.copy(shaderVariant = "other"))
        assertNotEquals(base, base.copy(deformationMode = CooFxDeformationMode.VAT))
        assertNotEquals(base, base.copy(alphaMode = CooFxAlphaMode.MASK, alphaCutoffBucket = 128))
        assertNotEquals(base, base.copy(cullMode = CooFxCullMode.NONE))
        assertNotEquals(base, base.copy(depthWrite = false))
        assertNotEquals(base, base.copy(lightMode = CooFxLightMode.FULL_BRIGHT))
        assertNotEquals(base, base.copy(backendCapabilitySignature = "iris"))
        assertNotEquals(base, base.copy(instanceLayoutVersion = 2))
    }

    @Test
    fun `stable comparison uses the declared field order`() {
        val first = batchKey().copy(primitiveId = "a", materialId = "z")
        val second = batchKey().copy(primitiveId = "b", materialId = "a")

        assertTrue(first < second)
        assertEquals(listOf(first, second), listOf(second, first).sorted())
    }

    private fun batchKey(): CooFxMeshBatchKey = CooFxMeshBatchKey(
        generation = 1L,
        pipelineId = ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "coofx/world"),
        worldNodeId = "world",
        primitiveId = "primitive",
        vertexLayoutVersion = 1,
        indexType = CooFxIndexType.UNSIGNED_SHORT,
        materialId = "material",
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
        backendCapabilitySignature = "vanilla",
        instanceLayoutVersion = 1
    )
}
