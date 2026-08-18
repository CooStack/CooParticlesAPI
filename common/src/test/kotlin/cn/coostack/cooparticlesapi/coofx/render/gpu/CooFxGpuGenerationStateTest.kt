package cn.coostack.cooparticlesapi.coofx.render.gpu

import cn.coostack.cooparticlesapi.coofx.playback.pose.CooFxLocalPose
import cn.coostack.cooparticlesapi.coofx.playback.pose.CooFxPoseNode
import cn.coostack.cooparticlesapi.coofx.render.batch.CooFxBatchTemplate
import cn.coostack.cooparticlesapi.coofx.render.batch.CooFxMeshBatchKey
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxAlphaMode
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxBlendMode
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxCompiledMaterial
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxCompiledPrimitive
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxCompiledRenderPackage
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxCullMode
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxDeformationMode
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxDeformationPlan
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxDepthTest
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxDrawRange
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxIndexType
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxLightMode
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxVertexAttribute
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxVertexComponentType
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxVertexLayout
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxVertexSemantic
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CooFxGpuGenerationStateTest {
    @Test
    fun `failed upload keeps the previous generation active`() {
        var failUpload = false
        val uploaded = mutableListOf<FakeGpuPackage>()
        val registry = CooFxGpuPackageRegistry(
            renderThreadGuard = CooFxRenderThreadGuard {},
            uploader = CooFxGpuPackageUploader { _, key, generation ->
                if (failUpload) {
                    error("upload failed")
                }
                FakeGpuPackage(key, generation).also(uploaded::add)
            }
        )

        val firstGeneration = registry.uploadAndReplace(compiledPackage('a'), "vanilla").getOrThrow()
        failUpload = true
        val failed = registry.uploadAndReplace(compiledPackage('b'), "vanilla")

        assertTrue(failed.isFailure)
        assertEquals(firstGeneration, registry.activeGeneration(resourceId()))
        assertFalse(uploaded.single().released)
    }

    @Test
    fun `retired generation waits for its frame lease`() {
        val uploaded = mutableListOf<FakeGpuPackage>()
        val registry = CooFxGpuPackageRegistry(
            renderThreadGuard = CooFxRenderThreadGuard {},
            uploader = CooFxGpuPackageUploader { _, key, generation ->
                FakeGpuPackage(key, generation).also(uploaded::add)
            }
        )
        val firstGeneration = registry.uploadAndReplace(compiledPackage('a'), "vanilla").getOrThrow()
        val lease = assertNotNull(registry.acquire(resourceId()))

        val secondGeneration = registry.uploadAndReplace(compiledPackage('b'), "vanilla").getOrThrow()

        assertEquals(secondGeneration, registry.activeGeneration(resourceId()))
        assertEquals(CooFxGpuGenerationState.RETIRED, registry.stateOf(firstGeneration))
        assertFalse(uploaded.first().released)

        lease.close()
        lease.close()

        assertTrue(uploaded.first().released)
        assertFalse(uploaded.last().released)
    }

    @Test
    fun `removed asset retires its active package`() {
        val uploaded = mutableListOf<FakeGpuPackage>()
        val registry = CooFxGpuPackageRegistry(
            renderThreadGuard = CooFxRenderThreadGuard {},
            uploader = CooFxGpuPackageUploader { _, key, generation ->
                FakeGpuPackage(key, generation).also(uploaded::add)
            }
        )
        registry.uploadAndReplace(compiledPackage('a'), "vanilla").getOrThrow()

        assertTrue(registry.retire(resourceId()))

        assertNull(registry.activeGeneration(resourceId()))
        assertTrue(uploaded.single().released)
    }

    @Test
    fun `dispose releases active packages and clears lookup`() {
        val registry = CooFxGpuPackageRegistry(
            renderThreadGuard = CooFxRenderThreadGuard {},
            uploader = CooFxGpuPackageUploader { _, key, generation -> FakeGpuPackage(key, generation) }
        )
        registry.uploadAndReplace(compiledPackage('a'), "vanilla").getOrThrow()
        val lease = registry.acquire(resourceId())
        assertNotNull(lease)
        lease.close()

        registry.disposeAll()

        assertNull(registry.activeGeneration(resourceId()))
        assertNull(registry.acquire(resourceId()))
    }

    private class FakeGpuPackage(
        override val key: CooFxGpuPackageKey,
        override val generation: Long
    ) : CooFxGpuPackage {
        var released = false

        override fun release() {
            released = true
        }
    }

    private fun compiledPackage(digestCharacter: Char): CooFxCompiledRenderPackage {
        val layout = CooFxVertexLayout(
            version = 1,
            strideBytes = 12,
            attributes = listOf(
                CooFxVertexAttribute(
                    semantic = CooFxVertexSemantic.POSITION,
                    componentType = CooFxVertexComponentType.FLOAT,
                    componentCount = 3,
                    byteOffset = 0
                )
            )
        )
        val material = CooFxCompiledMaterial(
            id = "material",
            baseColorTexture = null,
            alphaMode = CooFxAlphaMode.OPAQUE,
            alphaCutoff = 0.0F,
            cullMode = CooFxCullMode.BACK,
            depthTest = CooFxDepthTest.LESS_OR_EQUAL,
            depthWrite = true,
            blendMode = CooFxBlendMode.DISABLED,
            lightMode = CooFxLightMode.WORLD
        )
        val primitive = CooFxCompiledPrimitive(
            id = "primitive",
            vertexLayout = layout,
            vertexBytes = ByteArray(36),
            indexType = CooFxIndexType.UNSIGNED_SHORT,
            indexBytes = ByteArray(6),
            drawRange = CooFxDrawRange(firstIndex = 0, indexCount = 3),
            materialIndex = 0,
            deformationPlan = CooFxDeformationPlan(CooFxDeformationMode.RIGID, primitiveNodeIndex = 0)
        )
        return CooFxCompiledRenderPackage(
            id = resourceId(),
            sourceSchemaVersion = 1,
            compilerVersion = "test",
            contentDigest = digestCharacter.toString().repeat(64),
            nodeParents = listOf(-1),
            topologicalNodeOrder = listOf(0),
            poseNodes = listOf(CooFxPoseNode(-1, CooFxLocalPose())),
            clips = emptyList(),
            primitives = listOf(primitive),
            materials = listOf(material),
            emitters = emptyList(),
            batchTemplates = listOf(CooFxBatchTemplate(batchKey(), primitiveIndex = 0, materialIndex = 0)),
            warnings = emptyList()
        )
    }

    private fun batchKey(): CooFxMeshBatchKey = CooFxMeshBatchKey(
        generation = 1L,
        pipelineId = ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "coofx/world"),
        worldNodeId = "world",
        primitiveId = "primitive",
        vertexLayoutVersion = 1,
        indexType = CooFxIndexType.UNSIGNED_SHORT,
        materialId = "material",
        baseColorTexture = null,
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
        backendCapabilitySignature = "unbound",
        instanceLayoutVersion = 1
    )

    private fun resourceId(): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "coofx/test")
}
