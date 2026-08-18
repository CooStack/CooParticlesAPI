package cn.coostack.cooparticlesapi.coofx.runtime.model

import cn.coostack.cooparticlesapi.coofx.adapter.CooFxModelPlayRequest
import cn.coostack.cooparticlesapi.coofx.adapter.CooFxWorldTransform
import cn.coostack.cooparticlesapi.coofx.playback.pose.CooFxLocalPose
import cn.coostack.cooparticlesapi.coofx.playback.pose.CooFxPoseNode
import cn.coostack.cooparticlesapi.coofx.playback.pose.CooFxTransformTrack
import cn.coostack.cooparticlesapi.coofx.playback.track.CooFxTrack
import cn.coostack.cooparticlesapi.coofx.playback.track.CooFxTrackInterpolation
import cn.coostack.cooparticlesapi.coofx.render.batch.CooFxBatchTemplate
import cn.coostack.cooparticlesapi.coofx.render.batch.CooFxMeshBatchKey
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxAlphaMode
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxBlendMode
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxCompiledClipMetadata
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxCompiledLoopMode
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
import cn.coostack.cooparticlesapi.coofx.render.instance.CooFxMeshInstanceLayout
import net.minecraft.resources.ResourceLocation
import org.joml.Vector3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CooFxModelInstanceManagerTest {
    @Test
    fun `纯模型实例不需要 emitter 且可显式停止`() {
        val compiled = compiledPackage(
            poseNode = CooFxPoseNode(-1, CooFxLocalPose(translation = Vector3f(2F, 3F, 4F))),
        )
        val manager = CooFxModelInstanceManager()
        val instanceId = manager.start(
            request = request(),
            compiled = compiled,
            clipIndex = 0,
        )

        val batches = manager.buildBatches(1F) {
            CooFxResolvedModelAsset(compiled, 7L, "vanilla")
        }

        assertEquals(1, batches.size)
        val batch = batches.single()
        assertEquals(1, batch.instanceCount)
        assertEquals(7L, batch.key.generation)
        assertEquals(10F, batch.instanceData[CooFxMeshInstanceLayout.CURRENT_POSITION])
        assertEquals(20F, batch.instanceData[CooFxMeshInstanceLayout.CURRENT_POSITION + 1])
        assertEquals(30F, batch.instanceData[CooFxMeshInstanceLayout.CURRENT_POSITION + 2])
        assertEquals(2F, batch.nodeMatrixData[3])
        assertEquals(3F, batch.nodeMatrixData[7])
        assertEquals(4F, batch.nodeMatrixData[11])
        assertTrue(manager.isActive(instanceId))
        assertTrue(manager.stop(instanceId))
        assertFalse(manager.isActive(instanceId))
    }

    @Test
    fun `无动画模型使用 bind pose clip sentinel`() {
        val compiled = compiledPackage()
        val manager = CooFxModelInstanceManager()

        val instanceId = manager.start(request(), compiled, clipIndex = -1)

        val batch = manager.buildBatches(1F) {
            CooFxResolvedModelAsset(compiled, 7L, "vanilla")
        }.single()

        assertTrue(manager.isActive(instanceId))
        assertEquals(1, batch.instanceCount)
        assertEquals(0F, batch.nodeMatrixData[3])
    }

    @Test
    fun `服务端 patch 可原位更新模型变换而不重置 age`() {
        val compiled = compiledPackage()
        val manager = CooFxModelInstanceManager()
        val instanceId = manager.start(request(), compiled, clipIndex = 0)
        repeat(5) { manager.tick() }
        val updated = CooFxModelPlayRequest(
            resourceId = resourceId(),
            transform = CooFxWorldTransform(40.0, 50.0, 60.0),
            requestSeed = 42L,
            playbackSpeed = 2F,
        )

        assertTrue(manager.update(instanceId, updated, compiled, clipIndex = 0))
        val previousBatch = manager.buildBatches(0F) {
            CooFxResolvedModelAsset(compiled, 8L, "vanilla")
        }.single()
        val batch = manager.buildBatches(1F) {
            CooFxResolvedModelAsset(compiled, 8L, "vanilla")
        }.single()

        assertEquals(10F, previousBatch.instanceData[CooFxMeshInstanceLayout.PREVIOUS_POSITION])
        assertEquals(20F, previousBatch.instanceData[CooFxMeshInstanceLayout.PREVIOUS_POSITION + 1])
        assertEquals(30F, previousBatch.instanceData[CooFxMeshInstanceLayout.PREVIOUS_POSITION + 2])
        assertEquals(40F, batch.instanceData[CooFxMeshInstanceLayout.CURRENT_POSITION])
        assertEquals(50F, batch.instanceData[CooFxMeshInstanceLayout.CURRENT_POSITION + 1])
        assertEquals(60F, batch.instanceData[CooFxMeshInstanceLayout.CURRENT_POSITION + 2])
        assertEquals(5F, batch.instanceData[CooFxMeshInstanceLayout.CURRENT_POSITION + 3])
    }

    @Test
    fun `模型变换更新同时写入旋转和缩放`() {
        val compiled = compiledPackage()
        val manager = CooFxModelInstanceManager()
        val instanceId = manager.start(request(), compiled, clipIndex = 0)
        val updated = CooFxModelPlayRequest(
            resourceId = resourceId(),
            transform = CooFxWorldTransform(
                x = 40.0,
                y = 50.0,
                z = 60.0,
                rotationY = 1F,
                rotationW = 0F,
                scaleX = 2F,
                scaleY = 3F,
                scaleZ = 4F,
            ),
            requestSeed = 42L,
        )

        assertTrue(manager.update(instanceId, updated, compiled, clipIndex = 0))
        val batch = manager.buildBatches(1F) {
            CooFxResolvedModelAsset(compiled, 8L, "vanilla")
        }.single()

        assertEquals(1F, batch.instanceData[CooFxMeshInstanceLayout.CURRENT_ROTATION + 1])
        assertEquals(0F, batch.instanceData[CooFxMeshInstanceLayout.CURRENT_ROTATION + 3])
        assertEquals(2F, batch.instanceData[CooFxMeshInstanceLayout.CURRENT_SCALE])
        assertEquals(3F, batch.instanceData[CooFxMeshInstanceLayout.CURRENT_SCALE + 1])
        assertEquals(4F, batch.instanceData[CooFxMeshInstanceLayout.CURRENT_SCALE + 2])
    }

    @Test
    fun `模型 clip 按独立 tick 时间进入 node matrix`() {
        val translation = CooFxTrack(
            timesSeconds = floatArrayOf(0F, 1F),
            keyframeData = floatArrayOf(0F, 0F, 0F, 2F, 0F, 0F),
            componentCount = 3,
            interpolation = CooFxTrackInterpolation.LINEAR,
        )
        val clip = CooFxCompiledClipMetadata(
            id = "move",
            durationSeconds = 1F,
            animatedNodeIndices = listOf(0),
            loopMode = CooFxCompiledLoopMode.LOOP,
            transformTracks = listOf(CooFxTransformTrack(nodeIndex = 0, translation = translation)),
        )
        val compiled = compiledPackage(clips = listOf(clip))
        val manager = CooFxModelInstanceManager()
        manager.start(request(clipId = "move"), compiled, clipIndex = 0)
        repeat(10) { manager.tick() }

        val batch = manager.buildBatches(1F) {
            CooFxResolvedModelAsset(compiled, 3L, "vanilla")
        }.single()

        assertEquals(1F, batch.nodeMatrixData[3], absoluteTolerance = 0.0001F)
    }

    @Test
    fun `模型批处理调用次数随帧数和实例数线性增长`() {
        val compiled = compiledPackage()
        val manager = CooFxModelInstanceManager()
        val modelCount = 4
        val frameCount = 3
        repeat(modelCount) { index ->
            manager.start(
                request = request(requestSeed = index.toLong()),
                compiled = compiled,
                clipIndex = 0,
            )
        }
        var resolverCalls = 0

        repeat(frameCount) {
            val batches = manager.buildBatches(1F) {
                resolverCalls++
                CooFxResolvedModelAsset(compiled, 7L, "vanilla")
            }
            assertEquals(modelCount, batches.single().instanceCount)
        }

        assertEquals(modelCount * frameCount, resolverCalls)
    }

    @Test
    fun `模型批次写入当前位置的原版 packed light`() {
        val compiled = compiledPackage()
        val manager = CooFxModelInstanceManager()
        manager.start(request(), compiled, clipIndex = 0)

        val batch = manager.buildBatches(
            partialTick = 1F,
            lightResolver = { x, y, z ->
                assertEquals(10.0, x)
                assertEquals(20.0, y)
                assertEquals(30.0, z)
                0x00D00070
            },
        ) {
            CooFxResolvedModelAsset(compiled, 5L, "vanilla")
        }.single()

        assertEquals(0x00D00070.toFloat(), batch.instanceData[CooFxMeshInstanceLayout.CURRENT_SCALE + 3])
    }

    private fun request(
        clipId: String? = null,
        requestSeed: Long = 42L,
    ): CooFxModelPlayRequest = CooFxModelPlayRequest(
        resourceId = resourceId(),
        transform = CooFxWorldTransform(10.0, 20.0, 30.0),
        requestSeed = requestSeed,
        clipId = clipId,
    )

    private fun compiledPackage(
        poseNode: CooFxPoseNode = CooFxPoseNode(-1, CooFxLocalPose()),
        clips: List<CooFxCompiledClipMetadata> = emptyList(),
    ): CooFxCompiledRenderPackage {
        val layout = CooFxVertexLayout(
            version = 1,
            strideBytes = 12,
            attributes = listOf(
                CooFxVertexAttribute(
                    semantic = CooFxVertexSemantic.POSITION,
                    componentType = CooFxVertexComponentType.FLOAT,
                    componentCount = 3,
                    byteOffset = 0,
                )
            ),
        )
        val material = CooFxCompiledMaterial(
            id = "material",
            baseColorTexture = null,
            alphaMode = CooFxAlphaMode.OPAQUE,
            alphaCutoff = 0F,
            cullMode = CooFxCullMode.BACK,
            depthTest = CooFxDepthTest.LESS_OR_EQUAL,
            depthWrite = true,
            blendMode = CooFxBlendMode.DISABLED,
            lightMode = CooFxLightMode.FULL_BRIGHT,
        )
        val primitive = CooFxCompiledPrimitive(
            id = "primitive",
            vertexLayout = layout,
            vertexBytes = ByteArray(36),
            indexType = CooFxIndexType.UNSIGNED_SHORT,
            indexBytes = ByteArray(6),
            drawRange = CooFxDrawRange(firstIndex = 0, indexCount = 3),
            materialIndex = 0,
            deformationPlan = CooFxDeformationPlan(CooFxDeformationMode.RIGID, primitiveNodeIndex = 0),
        )
        return CooFxCompiledRenderPackage(
            id = resourceId(),
            sourceSchemaVersion = 1,
            compilerVersion = "test",
            contentDigest = "a".repeat(64),
            nodeParents = listOf(-1),
            topologicalNodeOrder = listOf(0),
            poseNodes = listOf(poseNode),
            clips = clips,
            primitives = listOf(primitive),
            materials = listOf(material),
            emitters = emptyList(),
            batchTemplates = listOf(CooFxBatchTemplate(batchKey(), primitiveIndex = 0, materialIndex = 0)),
            warnings = emptyList(),
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
        samplerKey = "default",
        shaderVariant = "coofx_mesh",
        deformationMode = CooFxDeformationMode.RIGID,
        alphaMode = CooFxAlphaMode.OPAQUE,
        alphaCutoffBucket = 0,
        cullMode = CooFxCullMode.BACK,
        depthTest = CooFxDepthTest.LESS_OR_EQUAL,
        depthWrite = true,
        blendMode = CooFxBlendMode.DISABLED,
        lightMode = CooFxLightMode.FULL_BRIGHT,
        backendCapabilitySignature = "unbound",
        instanceLayoutVersion = CooFxMeshInstanceLayout.VERSION,
    )

    private fun resourceId(): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath("test", "coofx/model-only.coofx.json")
}
