package cn.coostack.cooparticlesapi.coofx.runtime.mesh

import cn.coostack.cooparticlesapi.coofx.asset.CooFxAlphaMode
import cn.coostack.cooparticlesapi.coofx.asset.CooFxAnimation
import cn.coostack.cooparticlesapi.coofx.asset.CooFxAnimationChannel
import cn.coostack.cooparticlesapi.coofx.asset.CooFxAnimationPath
import cn.coostack.cooparticlesapi.coofx.asset.CooFxClip
import cn.coostack.cooparticlesapi.coofx.asset.CooFxClipLoopMode
import cn.coostack.cooparticlesapi.coofx.asset.CooFxDeformationMetadata
import cn.coostack.cooparticlesapi.coofx.asset.CooFxEmitter
import cn.coostack.cooparticlesapi.coofx.asset.CooFxFloat3
import cn.coostack.cooparticlesapi.coofx.asset.CooFxFloat3Range
import cn.coostack.cooparticlesapi.coofx.asset.CooFxInterpolation
import cn.coostack.cooparticlesapi.coofx.asset.CooFxMaterial
import cn.coostack.cooparticlesapi.coofx.asset.CooFxMesh
import cn.coostack.cooparticlesapi.coofx.asset.CooFxMeshPrimitive
import cn.coostack.cooparticlesapi.coofx.asset.CooFxNode
import cn.coostack.cooparticlesapi.coofx.asset.CooFxSourceAsset
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxAssetCompiler
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class CooFxMeshEmitterFactoryTest {
    @Test
    fun `source emitter 与真实 generation 合并为可执行 burst`() {
        val source = sourceAsset()
        val compiled = CooFxAssetCompiler().compile(source)

        val definition = CooFxMeshEmitterFactory.create(
            source = source,
            compiledPackage = compiled,
            emitterId = "burst",
            generation = 7L,
            backendCapabilitySignature = "vanilla:gl33",
        )

        assertEquals(CooFxMeshEmissionMode.BURST, definition.emissionMode)
        assertEquals(CooFxMeshSimulationSpace.WORLD, definition.simulationSpace)
        assertEquals(12, definition.emissionCount)
        assertEquals(40..40, definition.lifetimeTicks)
        assertEquals(-0.1F, definition.velocity.minimum.x)
        assertEquals(1.5F, definition.scale.maximum.z)
        assertEquals(7L, definition.variants.single().batchKey.generation)
    }

    @Test
    fun `自然完成的 burst 在最后粒子死亡后回收发射器`() {
        val source = sourceAsset()
        val compiled = CooFxAssetCompiler().compile(source)
        val definition = CooFxMeshEmitterFactory.create(
            source = source,
            compiledPackage = compiled,
            emitterId = "burst",
            generation = 7L,
            backendCapabilitySignature = "vanilla:gl33",
        ).copy(
            delayTicks = 0,
            emissionCount = 1,
            lifetimeTicks = 1..1,
        )
        val manager = CooFxMeshParticleManager(4)
        val runtimeId = manager.startEmitter(definition, CooFxMeshEmitterTransform(), 1L, 2L)

        manager.tick()
        manager.tick()

        assertEquals(0, manager.particleCount)
        assertEquals(false, manager.isEmitterActive(runtimeId))
    }

    @Test
    fun `glTF baseColorFactor 会进入 compiled material`() {
        val source = sourceAsset().let { asset ->
            asset.copy(
                materials = listOf(
                    asset.materials.single().copy(
                        baseColorFactor = listOf(0.25F, 0.5F, 0.75F, 0.8F),
                    )
                )
            )
        }

        val factor = CooFxAssetCompiler().compile(source).materials.single().baseColorFactor

        assertEquals(0.25F, factor.red)
        assertEquals(0.5F, factor.green)
        assertEquals(0.75F, factor.blue)
        assertEquals(0.8F, factor.alpha)
    }

    @Test
    fun `多 primitive mesh 的一次逻辑 burst 会生成全部渲染实例`() {
        val first = sourceAsset()
        val secondPrimitive = first.meshes.single().primitives.single().copy(
            positions = listOf(0F, 0F, 1F, 1F, 0F, 1F, 0F, 1F, 1F),
        )
        val source = first.copy(
            meshes = listOf(
                first.meshes.single().copy(
                    primitives = listOf(first.meshes.single().primitives.single(), secondPrimitive),
                )
            ),
        )
        val compiled = CooFxAssetCompiler().compile(source)
        val definition = CooFxMeshEmitterFactory.create(
            source = source,
            compiledPackage = compiled,
            emitterId = "burst",
            generation = 9L,
            backendCapabilitySignature = "vanilla:gl33",
        )
        val manager = CooFxMeshParticleManager(64)
        manager.startEmitter(definition, CooFxMeshEmitterTransform(), 1L, 2L)

        repeat(3) { manager.tick() }

        assertEquals(CooFxMeshSelectionMode.ALL, definition.selectionMode)
        assertEquals(2, definition.variants.size)
        assertEquals(24, manager.particleCount)
        assertEquals(listOf(12, 12), manager.buildBatches().map { it.instanceCount }.sorted())
    }

    @Test
    fun `ALL selection does not spawn a partial logical particle at capacity`() {
        val first = sourceAsset()
        val secondPrimitive = first.meshes.single().primitives.single().copy(
            positions = listOf(0F, 0F, 1F, 1F, 0F, 1F, 0F, 1F, 1F),
        )
        val source = first.copy(
            meshes = listOf(first.meshes.single().copy(primitives = listOf(first.meshes.single().primitives.single(), secondPrimitive))),
        )
        val compiled = CooFxAssetCompiler().compile(source)
        val definition = CooFxMeshEmitterFactory.create(source, compiled, "burst", 9L, "vanilla").copy(
            delayTicks = 0,
            emissionCount = 1,
        )
        val manager = CooFxMeshParticleManager(1)
        manager.startEmitter(definition, CooFxMeshEmitterTransform(), 1L, 2L)

        manager.tick()

        assertEquals(0, manager.particleCount)
        assertEquals(2L, manager.droppedParticleCount)
    }

    @Test
    fun `compiler 保留父子绑定姿态和独立 clip 轨道`() {
        val base = sourceAsset()
        val source = base.copy(
            nodes = listOf(
                CooFxNode(
                    name = "Root",
                    children = listOf(1),
                    mesh = null,
                    skin = null,
                    matrix = null,
                    translation = listOf(10F, 0F, 0F),
                    rotation = listOf(0F, 0F, 0F, 1F),
                    scale = listOf(1F, 1F, 1F),
                ),
                base.nodes.single().copy(
                    name = "AnimatedMesh",
                    translation = listOf(1F, 0F, 0F),
                ),
            ),
            animations = listOf(
                CooFxAnimation(
                    name = "move",
                    channels = listOf(
                        CooFxAnimationChannel(
                            node = 1,
                            path = CooFxAnimationPath.TRANSLATION,
                            interpolation = CooFxInterpolation.LINEAR,
                            inputSeconds = listOf(0F, 1F),
                            outputValues = listOf(1F, 0F, 0F, 3F, 0F, 0F),
                            outputComponentCount = 3,
                        )
                    ),
                )
            ),
            clips = listOf(CooFxClip("loop", 0, CooFxClipLoopMode.LOOP)),
            emitters = listOf(base.emitters.single().copy(node = 1)),
        )

        val compiled = CooFxAssetCompiler().compile(source)

        assertEquals(1, compiled.emitters.single().primitiveNodeBindings.single().nodeIndex)
        assertEquals(12F, compiled.nodeWorldMatrix(0, 1, 0.5F).m30(), 0.0001F)
        assertEquals(12F, compiled.nodeWorldMatrix(0, 1, 1.5F).m30(), 0.0001F)

        val definition = CooFxMeshEmitterFactory.create(source, compiled, "burst", 4L, "gl33").copy(
            delayTicks = 0,
            emissionCount = 2,
        )
        val manager = CooFxMeshParticleManager(4)
        manager.startEmitter(definition, CooFxMeshEmitterTransform(), 1L, 2L)
        manager.tick()
        manager.store.previousClipTimes[0] = 0F
        manager.store.clipTimes[0] = 0F
        manager.store.previousClipTimes[1] = 0.75F
        manager.store.clipTimes[1] = 0.75F

        val sidecar = manager.buildBatches(1F) { compiled }.single().nodeMatrixData
        assertEquals(11F, sidecar[3], 0.0001F)
        assertEquals(12.5F, sidecar[15], 0.0001F)
    }

    @Test
    fun `matrix bind pose 原样组合且 matrix node 动画被拒绝`() {
        val base = sourceAsset()
        val matrixNode = base.nodes.single().copy(
            matrix = listOf(
                1F, 0F, 0F, 0F,
                0F, 1F, 0F, 0F,
                0F, 0F, 1F, 0F,
                4F, 0F, 0F, 1F,
            ),
        )
        val staticSource = base.copy(nodes = listOf(matrixNode))
        val compiled = CooFxAssetCompiler().compile(staticSource)
        assertEquals(4F, compiled.nodeWorldMatrix(0, 0, 0F).m30(), 0.0001F)

        val animatedSource = staticSource.copy(
            animations = listOf(
                CooFxAnimation(
                    name = "invalid",
                    channels = listOf(
                        CooFxAnimationChannel(
                            node = 0,
                            path = CooFxAnimationPath.SCALE,
                            interpolation = CooFxInterpolation.STEP,
                            inputSeconds = listOf(0F),
                            outputValues = listOf(1F, 1F, 1F),
                            outputComponentCount = 3,
                        )
                    ),
                )
            )
        )
        assertFailsWith<IllegalArgumentException> { CooFxAssetCompiler().compile(animatedSource) }
    }

    @Test
    fun `同尺寸但不同顶点内容必须产生不同 package digest`() {
        val first = sourceAsset()
        val changedPrimitive = first.meshes.single().primitives.single().copy(
            positions = listOf(0F, 0F, 0F, 2F, 0F, 0F, 0F, 1F, 0F),
        )
        val second = first.copy(
            meshes = listOf(first.meshes.single().copy(primitives = listOf(changedPrimitive))),
        )

        val firstDigest = CooFxAssetCompiler().compile(first).contentDigest
        val secondDigest = CooFxAssetCompiler().compile(second).contentDigest

        assertNotEquals(firstDigest, secondDigest)
    }

    @Test
    fun `clip 重绑定到不同动画必须改变 package digest`() {
        val base = sourceAsset()
        val firstAnimation = CooFxAnimation(
            name = "first",
            channels = listOf(
                CooFxAnimationChannel(0, CooFxAnimationPath.TRANSLATION, CooFxInterpolation.LINEAR, listOf(0F, 1F), listOf(0F, 0F, 0F, 1F, 0F, 0F), 3)
            ),
        )
        val secondAnimation = firstAnimation.copy(
            name = "second",
            channels = listOf(firstAnimation.channels.single().copy(outputValues = listOf(0F, 0F, 0F, 2F, 0F, 0F))),
        )
        val first = base.copy(animations = listOf(firstAnimation, secondAnimation), clips = listOf(CooFxClip("clip", 0, CooFxClipLoopMode.LOOP)))
        val second = first.copy(clips = listOf(CooFxClip("clip", 1, CooFxClipLoopMode.LOOP)))

        assertNotEquals(CooFxAssetCompiler().compile(first).contentDigest, CooFxAssetCompiler().compile(second).contentDigest)
    }

    @Test
    fun `material-less primitive uses neutral material instead of authored material zero`() {
        val base = sourceAsset()
        val source = base.copy(
            meshes = listOf(base.meshes.single().copy(primitives = listOf(base.meshes.single().primitives.single().copy(material = null)))),
        )

        val compiled = CooFxAssetCompiler().compile(source)

        assertEquals(2, compiled.materials.size)
        assertEquals(1, compiled.primitives.single().materialIndex)
        assertEquals(1F, compiled.materials[1].baseColorFactor.red)
    }

    @Test
    fun `cyclic node topology is rejected without recursion overflow`() {
        val base = sourceAsset()
        val source = base.copy(
            nodes = listOf(
                base.nodes.single().copy(children = listOf(1)),
                base.nodes.single().copy(name = "cycle", children = listOf(0), mesh = null),
            ),
        )

        assertFailsWith<IllegalStateException> { CooFxAssetCompiler().compile(source) }
    }

    private fun sourceAsset(): CooFxSourceAsset {
        val resource = ResourceLocation.fromNamespaceAndPath("test", "coofx/burst.coofx.json")
        return CooFxSourceAsset(
            resource = resource,
            schemaVersion = 1,
            assetSeed = 1uL,
            modelResource = ResourceLocation.fromNamespaceAndPath("test", "coofx/models/burst.gltf"),
            scene = 0,
            nodes = listOf(
                CooFxNode(
                    name = "BurstNode",
                    children = emptyList(),
                    mesh = 0,
                    skin = null,
                    matrix = null,
                    translation = listOf(0F, 0F, 0F),
                    rotation = listOf(0F, 0F, 0F, 1F),
                    scale = listOf(1F, 1F, 1F),
                )
            ),
            meshes = listOf(
                CooFxMesh(
                    name = "BurstMesh",
                    primitives = listOf(
                        CooFxMeshPrimitive(
                            positions = listOf(0F, 0F, 0F, 1F, 0F, 0F, 0F, 1F, 0F),
                            normals = null,
                            texCoords = null,
                            colors = null,
                            indices = listOf(0, 1, 2),
                            material = 0,
                            morphTargetSemantics = emptyList(),
                        )
                    ),
                    weights = emptyList(),
                )
            ),
            materials = listOf(
                CooFxMaterial(
                    name = "BurstMaterial",
                    baseColorFactor = listOf(1F, 1F, 1F, 1F),
                    baseColorTexture = null,
                    alphaMode = CooFxAlphaMode.OPAQUE,
                    alphaCutoff = 0.5F,
                    doubleSided = false,
                    emissiveFactor = listOf(0F, 0F, 0F),
                )
            ),
            animations = emptyList(),
            emitters = listOf(
                CooFxEmitter(
                    id = "burst",
                    node = 0,
                    mesh = 0,
                    count = 12,
                    delayTicks = 2,
                    lifetimeTicks = 40,
                    velocity = CooFxFloat3Range(
                        minimum = CooFxFloat3(-0.1F, 0.2F, -0.1F),
                        maximum = CooFxFloat3(0.1F, 0.4F, 0.1F),
                    ),
                    scale = CooFxFloat3Range(
                        minimum = CooFxFloat3(0.5F, 0.5F, 0.5F),
                        maximum = CooFxFloat3(1.5F, 1.5F, 1.5F),
                    ),
                )
            ),
            deformationMetadata = CooFxDeformationMetadata(
                skinCount = 0,
                morphTargetCount = 0,
                hasAnimatedWeights = false,
                vatExtensionIds = emptySet(),
            ),
        )
    }
}
