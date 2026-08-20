package cn.coostack.cooparticlesapi.renderer.terrain

import cn.coostack.cooparticlesapi.renderer.pipeline.CooEffectUvMode
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelines
import cn.coostack.cooparticlesapi.renderer.pipeline.CooTerrainLayer
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.VertexFormatElement
import net.minecraft.client.renderer.chunk.SectionCompiler
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import java.nio.file.Path as NioPath
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CooTerrainPipelineContractTest {
    @Test
    fun `terrain vertex format preserves base effect and light uv independently`() {
        val format = CooTerrainVertexFormats.BLOCK_EFFECT

        assertEquals(
            listOf("Position", "Color", "BaseUV", "EffectUV", "LightUV", "Normal"),
            format.elementAttributeNames
        )
        assertTrue(format.contains(VertexFormatElement.UV0))
        assertTrue(format.contains(VertexFormatElement.UV1))
        assertTrue(format.contains(VertexFormatElement.UV2))
        assertTrue(format.getOffset(VertexFormatElement.UV0) != format.getOffset(VertexFormatElement.UV1))
        assertTrue(format.getOffset(VertexFormatElement.UV1) != format.getOffset(VertexFormatElement.UV2))
    }

    @Test
    fun `base uv mode writes atlas uv and effect uv to separate slots`() {
        val delegate = RecordingVertexConsumer()
        val consumer = CooEffectUvVertexConsumer(
            delegate,
            CooEffectUvMode.BASE_UV,
            BlockPos(17, 34, 51),
            0x1234L
        )

        consumer.addVertex(1.25F, 2.5F, 3.75F)
            .setUv(0.2F, 0.8F)
            .setUv2(240, 240)
            .setNormal(0F, 1F, 0F)

        assertEquals(0.2F to 0.8F, delegate.baseUv)
        assertEquals(packUv(0.2F) to packUv(0.8F), delegate.effectUv)
        assertTrue(delegate.effectUv!!.first in Short.MIN_VALUE..Short.MAX_VALUE)
        assertTrue(delegate.effectUv!!.second in Short.MIN_VALUE..Short.MAX_VALUE)
        assertEquals(0x34F0 to 0x12F0, delegate.lightUv)
    }

    @Test
    fun `face local uv uses the face axes without changing base uv`() {
        val delegate = RecordingVertexConsumer()
        val consumer = CooEffectUvVertexConsumer(delegate, CooEffectUvMode.FACE_LOCAL, BlockPos(17, 34, 51), 0L)

        consumer.addVertex(1.25F, 2.5F, 3.75F)
            .setUv(0.1F, 0.9F)
            .setNormal(0F, 1F, 0F)

        assertEquals(0.1F to 0.9F, delegate.baseUv)
        assertEquals(packUv(0.25F) to packUv(0.75F), delegate.effectUv)
    }

    @Test
    fun `world uv remains stable across negative section coordinates`() {
        val delegate = RecordingVertexConsumer()
        val consumer = CooEffectUvVertexConsumer(delegate, CooEffectUvMode.WORLD_XZ, BlockPos(-17, 34, -19), 0L)

        consumer.addVertex(15.25F, 2.5F, 13.75F)
            .setUv(0.1F, 0.9F)
            .setNormal(0F, 1F, 0F)

        assertEquals(packUv(0.25F) to packUv(0.75F), delegate.effectUv)
    }

    @Test
    fun `world uv keeps sub-block precision at the world border`() {
        val first = CooEffectUvResolver.periodicWorldCoordinate(30_000_000.25)
        val second = CooEffectUvResolver.periodicWorldCoordinate(30_000_000.75)
        val negative = CooEffectUvResolver.periodicWorldCoordinate(-30_000_000.25)

        assertEquals(0.5F, second - first)
        assertTrue(negative in 0F..<1024F)
    }

    @Test
    fun `chunk compiler and render section own the full terrain buffer lifecycle`() {
        val compiler = source("common/src/main/java/cn/coostack/cooparticlesapi/mixin/SectionCompilerMixin.java")
        val builderPack = source("common/src/main/java/cn/coostack/cooparticlesapi/mixin/SectionBufferBuilderPackMixin.java")
        val renderSection = source(
            "common/src/main/java/cn/coostack/cooparticlesapi/mixin/SectionRenderDispatcherRenderSectionMixin.java"
        )
        val clientManager = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/client/ClientRenderEntityManager.kt"
        )
        val clientPipelineManager = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/client/ClientRenderPipelineManager.kt"
        )
        val terrainManager = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainPipelineManager.kt"
        )
        val gameRendererFinish = source(
            "common/src/main/java/cn/coostack/cooparticlesapi/mixin/GameRendererTerrainFinishMixin.java"
        )
        val levelRenderer = source(
            "common/src/main/java/cn/coostack/cooparticlesapi/mixin/LevelRendererMixin.java"
        )
        val neoforgeCompiler = source(
            "neoforge/src/main/java/cn/coostack/cooparticlesapi/mixin/SectionCompilerNeoForgeMixin.java"
        )
        val mixinPlugin = source(
            "common/src/main/java/cn/coostack/cooparticlesapi/mixin/plugin/CooParticleMixinLoaderPlugin.java"
        )
        val neoforgeMixins = source("neoforge/src/main/resources/cooparticlesapi.neoforge.mixins.json")

        assertTrue("CooTerrainPipelineManager.resolveOverlayRenderTypes(state, baseLayer, pos)" in compiler)
        assertTrue("for (RenderType overlayLayer : overlayLayers)" in compiler)
        assertTrue("ItemBlockRenderTypes.getRenderLayer(fluidState)" in compiler)
        assertTrue("original.call(dispatcher, pos, level, overlayConsumer, state, fluidState)" in compiler)
        assertTrue("CooTerrainPipelineManager.vertexFormat(key, key.format())" in compiler)
        assertTrue("CooTerrainPipelineManager.decorateVertexConsumer(" in compiler)
        assertTrue("random.setSeed(state.getSeed(pos))" in compiler)
        assertTrue("CooTerrainPipelineManager.shouldPreserveVanillaTerrainGeometry(overlayLayers)" in compiler)
        assertTrue(
            "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;" +
                "Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/" +
                "SectionBufferBuilderPack;)Lnet/minecraft/client/renderer/chunk/SectionCompiler\$Results;" in compiler
        )
        assertFalse("Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler\$Results;" in compiler)
        assertTrue(
            "original.call(dispatcher, state, pos, level, poseStack, consumer, checkSides, random)" in compiler
        )
        assertTrue("poseStack, overlayConsumer, checkSides, random" in compiler)
        assertFalse("cooParticlesAPI\$writeNeoForgeTerrainOverlayBatch" in compiler)
        assertFalse("ModelData" in compiler)
        assertEquals(2, "poseStack.pushPose()".toRegex().findAll(compiler).count())
        assertEquals(2, "poseStack.popPose()".toRegex().findAll(compiler).count())
        assertTrue("CooTerrainPipelineManager.requiresTerrainSorting(renderType)" in compiler)
        assertTrue("meshData.sortQuads(sectionBufferBuilderPack.buffer(renderType), vertexSorting)" in compiler)
        assertTrue("Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler\$Results;" in neoforgeCompiler)
        assertTrue("ModelData modelData" in neoforgeCompiler)
        assertTrue("renderBatched(Lnet/minecraft/world/level/block/state/BlockState;" in neoforgeCompiler)
        assertTrue("SectionCompilerNeoForgeMixin" in neoforgeMixins)
        assertTrue("SectionCompilerMixin\".equals(mixinClassName)" in mixinPlugin)
        assertTrue("net/neoforged/neoforge/common/NeoForge.class" in mixinPlugin)
        assertFalse("Class.forName" in mixinPlugin)
        assertTrue("buffers.computeIfAbsent" in builderPack)
        assertTrue("new ByteBufferBuilder(key.bufferSize())" in builderPack)
        assertTrue("new VertexBuffer(VertexBuffer.Usage.STATIC)" in renderSection)
        assertTrue("RenderSystem.isOnRenderThread()" in renderSection)
        assertTrue("RenderSystem.recordRenderCall" in renderSection)
        assertTrue("CompletableFuture<VertexBuffer>" in renderSection)
        assertTrue("created.join()" in renderSection)
        assertTrue("cooParticlesAPI\$terrainBuffers.values().forEach(VertexBuffer::close)" in renderSection)
        assertTrue("cooParticlesAPI\$terrainBuffers.clear()" in renderSection)
        assertTrue("CooTerrainPipelineManager.recordPostDraw" in levelRenderer)
        assertTrue("CooTerrainPipelineManager.recordPostDraw(renderType, draw)" in levelRenderer)
        assertTrue("CooTerrainPipelineManager.shouldDeferVanillaTerrainOverlay()" in levelRenderer)
        assertTrue("CooTerrainPipelineManager.shouldPreserveVanillaTerrainGeometry(overlayLayers)" in neoforgeCompiler)
        assertTrue("if (!CooTerrainPipelineManager.isTerrainOverlayEnabled())" in levelRenderer)
        assertTrue("CooTerrainPipelineManager.collectPostEffects(" in clientManager)
        assertTrue("CooTerrainPipelineManager.collectScenePostEffects(" in clientManager)
        assertTrue("CooTerrainPipelineManager.shouldDeferShaderPackRenderEntities()" in clientManager)
        assertEquals(
            2,
            "CooTerrainPipelineManager.shouldDeferShaderPackRenderEntities()".toRegex().findAll(clientManager).count()
        )
        assertTrue("fun shouldDeferShaderPackRenderEntities(): Boolean" in terrainManager)
        assertTrue("scenePostMappingActiveThisFrame = true" in terrainManager)
        assertTrue("return scenePostMappingActiveThisFrame" in terrainManager)
        assertTrue("mapping.startedAt.toDouble()" in terrainManager)
        assertTrue("coerceIn(0.0, 1.0).toFloat()" in terrainManager)
        assertFalse("mapping.startedAt.toFloat()" in terrainManager)
        val scenePostBody = clientManager.substringAfter("fun runScenePost(context: RenderFrameContext)")
        assertTrue(
            scenePostBody.indexOf("CooTerrainPipelineManager.collectScenePostEffects(") <
                scenePostBody.indexOf(".filter(RenderEntityInstance<RenderEntity>::usesScenePost)")
        )
        assertTrue("CooPipelinePostEffectCompiler.compile(pipeline, subject)" in terrainManager)
        assertTrue("PostEffectFrameExecutor.captureAttachments(" in terrainManager)
        assertTrue("PostEffectAttachmentSpec" in terrainManager)
        assertTrue("attachments = List(attachmentCount)" in terrainManager)
        assertTrue("compiled.attachments.filter" in terrainManager)
        assertTrue("RenderType.chunkBufferLayers().any" in terrainManager)
        assertTrue("flushDeferredVanillaTerrainOverlays" in gameRendererFinish)
        assertTrue("finishDeferredFrame" in gameRendererFinish)
        assertFalse("renderIrisScenePostFallbackAfterFinalPass" in clientPipelineManager)
        assertFalse("renderIrisScenePostFallbackBeforeHand" in levelRenderer)
        assertFalse("fun renderIrisScenePostFallbackBeforeHand()" in clientPipelineManager)
        assertTrue("skipping the unsafe fallback after first-person hand rendering" in clientPipelineManager)
        assertTrue("private var irisShaderPackFrameActive = false" in clientPipelineManager)
        assertTrue("irisShaderPackFrameActive = CooParticlesAPIClient.checkIrisShaderPackUsed()" in clientPipelineManager)
        val irisFinishFallback = clientPipelineManager.substringAfter("fun finishLevelRender(")
        assertTrue("if (irisShaderPackFrameActive)" in irisFinishFallback)
        assertFalse(
            "CooParticlesAPIClient.checkIrisShaderPackUsed()" in
                irisFinishFallback.substringBefore("private fun runStages")
        )
        assertTrue("deferFrameFinish" in levelRenderer)
    }

    @Test
    fun `fabric section compiler target keeps the Minecraft 1 21 1 four argument signature`() {
        val compileMethods = SectionCompiler::class.java.declaredMethods.filter { method ->
            method.name == "compile"
        }

        assertEquals(1, compileMethods.size)
        assertEquals(4, compileMethods.single().parameterCount)
    }

    @Test
    fun `terrain shader cache key includes the generated descriptor`() {
        val shader = ResourceLocation.fromNamespaceAndPath("example", "terrain/shared")
        val first = CooTerrainShaderCacheKey(shader, "{\"samplers\":[\"BaseSampler\"]}")
        val second = CooTerrainShaderCacheKey(shader, "{\"samplers\":[\"SceneColor\"]}")

        assertFalse(first == second)
    }

    @Test
    fun `terrain shader descriptor ignores uniform values`() {
        val shader = ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "terrain/original_texture")
        val template = CooPipelines.block(
            ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "descriptor_values")
        ) {
            shader(shader)
            uniform("Strength", 0F)
        }
        val first = template.uniform("Strength", 0.25F)
        val second = template.uniform("Strength", 0.75F)

        val firstDescriptor = CooTerrainPipelineManager.buildGeneratedDescriptor(shader, first)
        val secondDescriptor = CooTerrainPipelineManager.buildGeneratedDescriptor(shader, second)
        assertEquals(firstDescriptor, secondDescriptor)
        assertFalse("0.25" in firstDescriptor)
        assertFalse("0.75" in secondDescriptor)
    }

    @Test
    fun `terrain descriptor only declares vertex inputs consumed by the fragment`() {
        val shader = ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "terrain/descriptor_activity")
        val template = CooPipelines.block(
            ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "descriptor_activity_pipeline")
        ) {
            shader(shader)
        }
        val unused = CooTerrainPipelineManager.buildGeneratedDescriptor(
            shader,
            template,
            "void main() { }"
        )
        assertFalse("CooEffectUvCameraPosition" in unused)
        assertFalse("CooEffectUvMode" in unused)
        assertFalse("CooGameTime" in unused)
        assertFalse("CooMappingDepthAvailable" in unused)

        val used = CooTerrainPipelineManager.buildGeneratedDescriptor(
            shader,
            template,
            "in vec2 effectUv; flat in float effectElapsedTicks; void main() { " +
                "float value = effectUv.x + effectElapsedTicks; }"
        )
        assertTrue("CooEffectUvCameraPosition" in used)
        assertTrue("CooEffectUvMode" in used)
        assertTrue("CooGameTime" in used)
    }

    @Test
    fun `level renderer draws custom batches in every vanilla terrain phase`() {
        val renderer = source("common/src/main/java/cn/coostack/cooparticlesapi/mixin/LevelRendererMixin.java")
        val mixins = source("common/src/main/resources/cooparticlesapi.mixins.json")

        assertTrue("renderSolidTerrainPipelines" in renderer)
        assertTrue("renderCutoutMippedTerrainPipelines" in renderer)
        assertTrue("renderCutoutTerrainPipelines" in renderer)
        assertTrue("renderTranslucentTerrainPipelines" in renderer)
        assertTrue("renderTripwireTerrainPipelines" in renderer)
        assertTrue("renderFabulousTranslucentTerrainPipelines" in renderer)
        assertTrue("renderFabulousTripwireTerrainPipelines" in renderer)
        assertTrue("ordinal = 6" in renderer)
        assertTrue("CooTerrainPipelineManager.layersFor(baseLayer)" in renderer)
        assertTrue("CooTerrainPipelineManager.beginOverlayBatch(immediateLayers)" in renderer)
        assertTrue("renderTerrainPipelines(RenderType.tripwire(), camera, frustumMatrix, projectionMatrix)" in renderer)
        assertTrue("SectionBufferBuilderPackMixin" in mixins)
        assertTrue("SectionCompilerMixin" in mixins)
        assertTrue("SectionRenderDispatcherRenderSectionMixin" in mixins)
        assertTrue("\"client\"" in mixins)
    }

    @Test
    fun `binding reload and compatibility changes rebuild terrain resources`() {
        val manager = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainPipelineManager.kt"
        )
        val levelRenderer = source(
            "common/src/main/java/cn/coostack/cooparticlesapi/mixin/LevelRendererMixin.java"
        )

        assertTrue("CooBlockPipelines.addChangeListener { onBlockBindingsChanged() }" in manager)
        assertTrue("signal is ShaderReloadSignal.FullReload" in manager)
        assertTrue("releaseResources()" in manager)
        assertTrue("requestSectionRebuild()" in manager)
        assertTrue("OpenGlPostEffectExecutionBackend.captureTerrainScene" in manager)
        assertTrue("CooPipelineTextureSource.BlockAtlas ->" in manager)
        assertTrue("CooPipelineTextureSource.SceneColor -> terrainColorTextureId" in manager)
        assertTrue("resolveNamedColorTexture(RenderSceneTargets.MASK, 0)" in manager)
        assertTrue("minecraft.levelRenderer.allChanged()" in manager)
        assertTrue("shaders.values.forEach(ShaderInstance::close)" in manager)
        assertTrue("failedShaders.clear()" in manager)
        assertTrue("shouldPreserveVanillaTerrainGeometry" in manager)
        assertTrue("return terrainOverlayDisabled || irisShaderPackActive" in manager)
        assertTrue("framePartialTick" in manager)
        assertTrue("shader.getUniform(\"CooGameTime\")" in manager)
        assertTrue("CooTerrainEffectRegistry.activationAt(" in manager)
        assertTrue("activationAt(renderType, pos)" in manager)
        assertFalse("CooTerrainEffectFrame" in manager)
        assertFalse("effectVariantPipelines" in manager)
        assertFalse("CooTerrainPropagation" in manager)
        assertFalse("propagationPipelines" in manager)
        assertTrue("CooIrisComposite" in manager)
        assertTrue("fun isTerrainPostRenderType(renderType: RenderType): Boolean" in manager)
        assertTrue("fun isTerrainMappingSectionVisible(" in manager)
        assertTrue("compositions.none" in manager)
        assertTrue("input.node in worldNodes" in manager)
        assertTrue("finishDeferredFrame" in manager)
        assertTrue("shouldDeferVanillaTerrainOverlay" in manager)
        assertTrue("!sodiumLoaded && irisShaderPackActive" in manager)
        assertTrue("if (!irisShaderPackActive) return false" in manager)
        assertTrue("GameRendererTerrainFinishMixin" in source(
            "common/src/main/resources/cooparticlesapi.mixins.json"
        ))
        val outputState = source(
            "common/src/main/java/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainRenderStateShard.java"
        )
        assertTrue("isFinalCompositeTerrainOverlayActive()" in outputState)
        assertTrue("terrainLayering()" in outputState)
        assertTrue("glPolygonOffset(-1F, -10F)" in outputState)
        assertTrue("isOffsetStageActive()" in outputState)
        assertTrue("CooTerrainPipelineManager.isTerrainAttachmentCaptureActive()" in outputState)
        assertTrue("CooTerrainPipelineManager.isFinalCompositeTerrainOverlayActive()" in outputState)
        assertTrue("terrainWriteMask(RenderType baseLayer)" in outputState)
        assertTrue("COLOR_WRITE.setupRenderState()" in outputState)
        assertTrue("beginFinalCompositeTerrainOverlay()" in manager)
        assertTrue("endFinalCompositeTerrainOverlay()" in manager)
        assertTrue("vanilla terrain remains in gbuffers_terrain" in manager)
        assertTrue("handleOverlayDrawFailure" in manager)
        assertTrue("handleSodiumOverlayFailure" in manager)
        assertTrue("terrainOverlayDisabled" in manager)
        assertTrue("@Volatile\n    private var sodiumLoaded" in manager)
        assertTrue("@Volatile\n    private var irisShaderPackActive" in manager)
        assertTrue("requestSectionRebuild()" in manager.substringAfter("fun handleSodiumOverlayFailure"))
        assertTrue("fun beginOverlayBatch(renderTypes: List<RenderType>): Boolean" in manager)
        assertTrue("markIrisTerrainUnavailable(" in manager)
        assertTrue("irisTerrainUnavailableThisFrame = true" in manager)
        assertTrue("skipping this frame and retrying next frame" in manager)
        assertTrue("irisTerrainUnavailableThisFrame = false" in manager.substringAfter("fun beginRenderFrame("))
        assertTrue("terrainOverlayDisabled = false" in manager.substringAfter("private fun onBlockBindingsChanged()"))
        assertFalse("handleSodiumOverlayFailure(\n                        \"Iris terrain depth attachment\"" in manager)
        assertFalse("handleSodiumOverlayFailure(\"Iris terrain target resolution\"" in manager)
        assertFalse("terrainOverlayDisabled = true" in manager.substringAfter("private fun markIrisTerrainUnavailable(")
            .substringBefore("private fun shaderFor("))
        assertFalse("requestSectionRebuild()" in manager.substringAfter("private fun markIrisTerrainUnavailable(")
            .substringBefore("private fun shaderFor("))
        assertTrue("if (!CooTerrainPipelineManager.beginOverlayBatch(immediateLayers))" in levelRenderer)
        val sodiumOverlay = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/sodium/CooSodiumTerrainOverlay.kt"
        )
        assertTrue("if (!CooTerrainPipelineManager.beginOverlayBatch(renderTypes)) return" in sodiumOverlay)
        assertTrue("postDrawGroups" in sodiumOverlay)
        assertTrue("immediateDrawGroups" in sodiumOverlay)
        assertTrue("isTerrainMappingSectionVisible" in sodiumOverlay)
        assertTrue("fullSectionRebuildPending = true" in manager.substringAfter("private fun requestSectionRebuild()"))
        assertTrue("flushPendingFullSectionRebuild()" in manager.substringAfter("fun beginRenderFrame("))
        assertTrue("disabling Coo terrain overlays" in manager)
        assertTrue("sections with vanilla geometry" in manager)
        assertFalse("if (!sodiumLoaded) return false" in manager.substringAfter("fun handleSodiumOverlayFailure"))
        assertTrue("releaseTerrainColorCapture()" in manager)
        assertFalse("vanillaShader(" in manager)
        assertTrue("disabling the custom terrain overlay and rebuilding vanilla geometry" in manager)
        assertTrue("deltaTracker.getGameTimeDeltaPartialTick(true)" in source(
            "common/src/main/java/cn/coostack/cooparticlesapi/mixin/LevelRendererMixin.java"
        ))

        val frameStart = manager.substringAfter("fun beginRenderFrame(")
            .substringBefore("fun recordPostDraw")
        assertTrue("drainChangedPositions" in frameStart)
        assertFalse("allChanged()" in frameStart)
        assertTrue("setSectionDirty(section.x, section.y, section.z)" in manager)

        val effectRegistry = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainEffectRegistry.kt"
        )
        assertTrue("positionIndex" in effectRegistry)
        assertTrue("fun advance(dimension: ResourceLocation, gameTime: Long)" in effectRegistry)
        assertTrue("fun drainChangedPositions(dimension: ResourceLocation)" in effectRegistry)
    }

    @Test
    fun `terrain sampler resolution clears optional inputs and exposes required fallback`() {
        val manager = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainPipelineManager.kt"
        )

        assertTrue("if (input.node != worldNode.name) return@forEach" in manager)
        assertTrue("if (input.optional) {\n                0" in manager)
        assertTrue("warnRequiredSamplerFallback(pipeline, input, source)" in manager)
        assertTrue("shader.setSampler(input.sampler, textureId)" in manager)
        assertTrue("minecraft.gameRenderer.lightTexture().turnOnLightLayer()" in manager)
        assertTrue("shader.setSampler(\"Sampler2\", RenderSystem.getShaderTexture(2))" in manager)
        assertTrue("required sampler {} from {} is unavailable" in manager)
        assertTrue("binding the block atlas fallback" in manager)
        assertTrue("warnedRequiredSamplerFallbacks.clear()" in manager)
    }

    @Test
    fun `terrain effect server api does not load client renderer types`() {
        val serverSources = listOf(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainEffectGroup.kt",
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainEffectGroupBuilder.kt",
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainEffectGroupDefinition.kt",
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainEffectGroupSnapshot.kt",
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainEffectManager.kt",
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/ServerGroupKey.kt",
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainEffectRegistry.kt",
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/packet/server/PacketTerrainEffectGroupS2C.kt"
        ).joinToString("\n", transform = ::source)

        assertFalse("net.minecraft.client" in serverSources)
        assertFalse("org.lwjgl" in serverSources)
        assertFalse("Minecraft.getInstance" in serverSources)
        assertFalse("RenderSystem" in serverSources)
    }

    @Test
    fun `terrain declarations use dedicated source files`() {
        val declarations = listOf(
            "CooEffectUv",
            "CooEffectUvResolver",
            "CooEffectUvVertexConsumer",
            "CooResolvedTerrainEffectGroup",
            "CooTerrainEffectBatchKey",
            "CooTerrainEffectGroup",
            "CooTerrainEffectGroupBuilder",
            "CooTerrainEffectGroupDefinition",
            "CooTerrainEffectGroupSnapshot",
            "CooTerrainEffectManager",
            "CooTerrainEffectRegistry",
            "CooTerrainPipelineManager",
            "CooTerrainShaderCacheKey",
            "DeferredFrameFinish",
            "DeferredVanillaDraw",
            "FramebufferAttachment",
            "GroupKey",
            "IrisDepthAttachmentRestore",
            "PipelineConfigKey",
            "PositionKey",
            "SectionCoordinate",
            "ServerGroupKey",
            "StoredGroup"
        )
        val declarationPattern = Regex(
            "(?m)^(?:internal\\s+|public\\s+)?(?:data\\s+)?(?:class|object|interface)\\s+([A-Za-z0-9_]+)"
        )

        declarations.forEach { declaration ->
            val source = source(
                "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/$declaration.kt"
            )
            assertEquals(listOf(declaration), declarationPattern.findAll(source).map { it.groupValues[1] }.toList())
        }
    }

    @Test
    fun `fabric and neoforge terrain batches keep vanilla block render states`() {
        listOf(
            source("fabric/src/main/kotlin/cn/coostack/cooparticlesapi/platform/FabricRenderTypesProvider.kt"),
            source("neoforge/src/main/kotlin/cn/coostack/cooparticlesapi/platform/NeoRenderTypesProvider.kt")
        ).forEach { provider ->
            val terrain = provider.substringAfter("override fun terrain(")
            assertTrue("CooTerrainVertexFormats.BLOCK_EFFECT" in terrain)
            assertTrue("RenderType.BIG_BUFFER_SIZE" in terrain)
            assertTrue("baseLayer.affectsCrumbling()" in terrain)
            assertTrue("baseLayer.sortOnUpload()" in terrain)
            assertTrue("CooTerrainPipelineManager.shaderFor(renderType, baseLayer)" in terrain)
            assertTrue("TextureAtlas.LOCATION_BLOCKS" in terrain)
            assertTrue("CooTerrainRenderStateShard.terrainOutput(baseLayer)" in terrain)
            assertTrue("RenderStateShard.CULL" in terrain)
            assertTrue("RenderStateShard.LEQUAL_DEPTH_TEST" in terrain)
            assertTrue("RenderStateShard.LIGHTMAP" in terrain)
            assertTrue("CooTerrainRenderStateShard.terrainLayering()" in terrain)
            assertTrue("CooTerrainRenderStateShard.terrainWriteMask(baseLayer)" in terrain)
            assertTrue(".createCompositeState(true)" in terrain)
            assertTrue("IrisCompat::markUnskippable" in terrain)
            assertTrue("IrisCompat.wrapEntityRenderType" !in terrain)
        }
    }

    @Test
    fun `terrain effect uniform updates reuse their render type batch`() {
        val manager = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainPipelineManager.kt"
        )
        val providerInterface = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/display/CooRenderTypesProvider.kt"
        )
        listOf(
            source("fabric/src/main/kotlin/cn/coostack/cooparticlesapi/platform/FabricRenderTypesProvider.kt"),
            source("neoforge/src/main/kotlin/cn/coostack/cooparticlesapi/platform/NeoRenderTypesProvider.kt")
        ).forEach { provider ->
            assertTrue("LinkedHashMap<Pair<Any, RenderType>, RenderType>()" in provider)
            assertTrue("terrainCache.getOrPut(batchKey to baseLayer)" in provider)
            assertTrue("CooTerrainMappingBatchKey" in provider)
            assertTrue("CooTerrainPipelineManager.shaderFor(renderType, baseLayer)" in provider)
        }
        assertTrue("batchKey: Any = pipeline" in providerInterface)
        assertTrue("CooTerrainEffectBatchKey(" in manager)
        assertTrue("val batchKey = synchronized(terrainLayers)" in manager)
        assertTrue("groupKey.second" in manager)
    }

    @Test
    fun `terrain overlay samples copied scene attachments without framebuffer feedback`() {
        val backend = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/OpenGlPostEffectExecutionBackend.kt"
        )
        val shader = source(
            "common/src/main/resources/assets/cooparticlesapi/shaders/core/terrain/propagation.fsh"
        )
        val manager = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainPipelineManager.kt"
        )
        val iris = source("common/src/main/kotlin/cn/coostack/cooparticlesapi/compat/IrisCompat.kt")

        assertTrue("captureTerrainScene(" in backend)
        assertTrue("sourceFramebufferId == target.buffer.fbo()" in backend)
        assertTrue("source framebuffer is incomplete" in backend)
        assertTrue("copyColor(" in backend)
        assertTrue("copySceneDepth(context, target)" in backend)
        assertTrue("explicit terrain scene inputs use their configured fallback" in backend)
        assertTrue("currentTerrainDepthTexture()" in iris)
        assertTrue("currentSceneDepthTexture()" in iris)
        assertTrue("currentSceneDepthNoHandTexture()" in iris)
        assertTrue("renderTargetsClass.getMethod(\"getDepthTexture\")" in iris)
        assertTrue("renderTargetsClass.getMethod(\"getDepthTextureNoHand\")" in iris)
        assertTrue("Class.forName(\"net.irisshaders.iris.pipeline.IrisRenderingPipeline\")" in iris)
        val depthMethodResolution = iris
            .substringAfter("private fun resolveTerrainDepthMethods()")
            .substringBefore("private data class ParticleRenderingMethods")
        assertFalse("getPipeline.invoke" in depthMethodResolution)
        assertTrue("IrisCompat.currentSceneDepthTexture()" in backend)
        assertTrue("attachIrisSceneDepth(sourceFramebuffer, irisSceneDepth)" in manager)
        assertTrue("terrainAttachmentCaptureActive = true" in manager)
        val outputState = source(
            "common/src/main/java/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainRenderStateShard.java"
        )
        assertTrue("CooTerrainPipelineManager.isTerrainAttachmentCaptureActive()" in outputState)
        assertTrue("delegate.setupRenderState()" in outputState)
        assertTrue("delegate.clearRenderState()" in outputState)
        assertTrue("glFramebufferTexture2D(" in manager)
        assertTrue("restoreIrisDepthAttachment()" in manager)
        assertTrue("texture(BaseSampler, baseUv)" in shader)
        assertTrue("texture(SceneColor" in shader)
        assertTrue("#coo_import <terrain_light_fog.glsl>" in shader)
        assertEquals(1, shader.lineSequence().count { it == "#coo_import <terrain_light_fog.glsl>" })
        assertFalse("#coo_import <cooparticlesapi:include/terrain_light_fog.glsl>" in shader)
        assertFalse("#moj_import" in shader)
        assertTrue("CooIrisComposite" in shader)
        assertTrue("uniform float CooAlphaCutoff;" in shader)
        assertTrue("if (atlasColor.a < CooAlphaCutoff)" in shader)
        assertTrue("discard;" in shader)
        assertTrue("float luminance = dot(baseRgb" in shader)
        assertTrue("float tintLuminance = max(dot(effectTint" in shader)
        assertTrue("vec3 colorized = effectTint * (luminance / tintLuminance)" in shader)
        assertTrue("vec4 color = vec4(mix(baseRgb, colorized, strength), atlasColor.a)" in shader)
        assertTrue("fragColor = CooIrisComposite != 0" in shader)
        assertFalse("sin((effectUv.x + effectUv.y)" in shader)
        assertTrue("effectTint" in shader)
        assertTrue("effectStrength" in shader)
        assertTrue("BaseSamplerScreenCopy" !in shader)
    }

    @Test
    fun `screen mapping uses terrain-only depth snapshots across vanilla sodium and iris`() {
        val builders = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/pipeline/CooPipelineBuilders.kt"
        )
        val backend = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/OpenGlPostEffectExecutionBackend.kt"
        )
        val resolver = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/client/ClientRenderSceneResourcesResolver.kt"
        )
        val levelRenderer = source(
            "common/src/main/java/cn/coostack/cooparticlesapi/mixin/LevelRendererMixin.java"
        )
        val manager = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/client/ClientRenderPipelineManager.kt"
        )
        val terrainManager = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainPipelineManager.kt"
        )
        val sodium = source(
            "common/src/main/java/cn/coostack/cooparticlesapi/mixin/compat/sodium/RenderSectionManagerMixin.java"
        )

        assertTrue("fun inputTerrainOpaqueDepth(" in builders)
        assertTrue("fun inputSceneDepthNoHand(" in builders)
        assertTrue("fun inputTerrainTranslucentDepthBefore(" in builders)
        assertTrue("fun inputTerrainTranslucentDepthAfter(" in builders)
        assertTrue("val irisDepth = IrisCompat.currentSceneDepthTexture()" in backend)
        assertTrue("irisDepth.textureId" in backend)
        assertTrue("sourceFormat.blitClass.depthBits != targetFormat.blitClass.depthBits" in backend)
        assertTrue("sourceFormat.blitClass.floatingPoint != targetFormat.blitClass.floatingPoint" in backend)
        assertTrue("internalFormat == GL_DEPTH_STENCIL || internalFormat == GL_DEPTH_COMPONENT -> null" in backend)
        assertFalse("DepthBlitClass(0, 0)" in backend)
        assertTrue("resolveSceneDepthFormat(context)" in backend)
        assertTrue("setDepthTextureFormat" in backend)
        assertTrue("current.depthFormat != depthFormat" in backend)
        assertTrue("if (!depthReady && sceneDepthAvailable) return false" in backend)
        assertTrue("resolveDepthFramebufferSize" in backend)
        assertTrue("terrainDepthSourceCache" in backend)
        assertTrue("clearPendingGlErrors()" in backend)
        assertTrue("Scene color blit failed" in backend)
        val sceneDepthResolver = backend
            .substringAfter("private fun resolveSceneDepthTexture")
            .substringBefore("private fun")
        assertTrue(
            sceneDepthResolver.indexOf("IrisCompat.currentSceneDepthTexture()") <
                sceneDepthResolver.indexOf("return input.textureId")
        )
        assertTrue("RenderSceneTargets.TERRAIN_OPAQUE_DEPTH" in resolver)
        assertTrue("RenderSceneTargets.SCENE_DEPTH_NO_HAND" in resolver)
        assertTrue("IrisCompat.currentSceneDepthNoHandTexture()" in resolver)
        assertTrue("val sceneDepthTextureId = irisSceneDepthTextureId ?: resolvedTargets.sceneDepthTextureId" in resolver)
        assertTrue("depthTextureId = sceneDepthNoHandTextureId" in resolver)
        assertTrue("RenderSceneTargets.TERRAIN_TRANSLUCENT_DEPTH_BEFORE" in resolver)
        assertTrue("RenderSceneTargets.TERRAIN_TRANSLUCENT_DEPTH_AFTER" in resolver)
        assertTrue("CooTerrainPipelineManager.captureOpaqueTerrainDepth();" in levelRenderer)
        assertTrue("CooTerrainPipelineManager.captureTranslucentTerrainDepthBefore();" in sodium)
        assertTrue("CooTerrainPipelineManager.captureTranslucentTerrainDepthAfter();" in sodium)
        assertTrue("override fun refreshSceneFrame(context: RenderFrameContext)" in backend)
        assertTrue("PostEffectFrameExecutor.refreshSceneFrame(context)" in manager)
        assertTrue("if (!terrainDepthSnapshotsRequiredThisFrame) return" in terrainManager)
        assertTrue("line.output.isTerrainDepthSnapshot()" in terrainManager)
        assertTrue("Iris final pass 前" in manager)
    }

    @Test
    fun `terrain alpha cutoff follows vanilla cutout layers`() {
        assertEquals(0F, CooTerrainPipelineManager.alphaCutoff(CooTerrainLayer.SOLID))
        assertEquals(0.1F, CooTerrainPipelineManager.alphaCutoff(CooTerrainLayer.CUTOUT_MIPPED))
        assertEquals(0.1F, CooTerrainPipelineManager.alphaCutoff(CooTerrainLayer.CUTOUT))
        assertEquals(0F, CooTerrainPipelineManager.alphaCutoff(CooTerrainLayer.TRANSLUCENT))
    }

    @Test
    fun `terrain shader sources stay in the API namespace`() {
        val legacyDirectory = projectPath(
            "common/src/main/resources/assets/minecraft/shaders/core/cooparticlesapi"
        )
        val apiDirectory = projectPath(
            "common/src/main/resources/assets/cooparticlesapi/shaders/core/terrain"
        )

        assertTrue(
            !legacyDirectory.exists() || legacyDirectory.toFile().walkTopDown().none { file -> file.isFile }
        )
        assertTrue(apiDirectory.resolve("block_effect.vsh").exists())
        assertTrue(apiDirectory.resolve("propagation.fsh").exists())
        assertTrue("remapTerrainShaderSource" in source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainPipelineManager.kt"
        ))
    }

    @Test
    fun `effect uv uses signed short storage with full range decoding`() {
        val shader = source(
            "common/src/main/resources/assets/cooparticlesapi/shaders/core/terrain/block_effect.vsh"
        )

        assertEquals(Short.MIN_VALUE.toInt(), packUv(0F))
        assertEquals(Short.MAX_VALUE.toInt(), packUv(1F))
        assertTrue("(vec2(EffectUV) + 32768.0) / 65535.0" in shader)
    }

    @Test
    fun `sodium overlay preserves lightmap and writes activation tick`() {
        val packed = CooEffectUvResolver.packLightWithActivation((240 shl 16) or 240, 0x1234L)
        val packedU = packed and 0xFFFF
        val packedV = (packed ushr 16) and 0xFFFF
        val decodedTick = ((packedV ushr 8) shl 8) or (packedU ushr 8)

        assertEquals(240, packedU and 0xFF)
        assertEquals(240, packedV and 0xFF)
        assertEquals(0x1234, decodedTick)
        assertTrue("packLightWithActivation(vertex.light, activatedAt)" in source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/sodium/CooSodiumTerrainOverlay.kt"
        ))
    }

    @Test
    fun `world effect uv modes use continuous world position`() {
        val shader = source(
            "common/src/main/resources/assets/cooparticlesapi/shaders/core/terrain/block_effect.vsh"
        )
        val manager = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainPipelineManager.kt"
        )

        assertEquals(2, CooEffectUvMode.WORLD_XZ.shaderValue)
        assertEquals(3, CooEffectUvMode.WORLD_XY.shaderValue)
        assertEquals(4, CooEffectUvMode.WORLD_YZ.shaderValue)
        assertTrue("effectUvPosition = position + CooEffectUvCameraPosition" in shader)
        assertTrue("effectUv = mod(effectUvPosition.xz, 1024.0)" in shader)
        assertTrue("effectUv = mod(effectUvPosition.xy, 1024.0)" in shader)
        assertTrue("effectUv = mod(effectUvPosition.yz, 1024.0)" in shader)
        assertTrue("shader.getUniform(\"CooEffectUvMode\")?.set(pipeline.effectUvMode.shaderValue)" in manager)
        assertTrue("CooEffectUvResolver.periodicWorldCoordinate(camera.x)" in manager)
        assertTrue("generatedDescriptorProvider" in manager)
        assertTrue("buildGeneratedDescriptor" in manager)
        assertTrue("CooShaderSourceLoader.load(resources, mapped)" in manager)
    }

    @Test
    fun `propagation shader keeps atlas alpha while sampling iris scene rgb`() {
        val shader = source(
            "common/src/main/resources/assets/cooparticlesapi/shaders/core/terrain/propagation.fsh"
        )

        assertTrue("vec4 atlasColor = texture(BaseSampler, baseUv)" in shader)
        assertTrue("baseRgb = texture(SceneColor, gl_FragCoord.xy / ScreenSize).rgb" in shader)
        assertTrue("atlasColor.a);" in shader)
        assertFalse("baseColor.a);" in shader)
    }

    @Test
    fun `iris scene and terrain depth keep separate sources`() {
        val manager = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainPipelineManager.kt"
        )

        assertTrue("sceneDepthTextureId = captured?.get(RenderSceneTargets.SCENE_DEPTH)?.depthTextureId" in manager)
        assertTrue("terrainDepthTextureId = IrisCompat.currentTerrainDepthTexture()?.textureId" in manager)
        assertTrue("val irisSceneDepth = IrisCompat.currentSceneDepthTexture()" in manager)
        assertTrue("CooPipelineTextureSource.SceneDepth -> sceneDepthTextureId" in manager)
        assertTrue("CooPipelineTextureSource.TerrainDepth -> terrainDepthTextureId" in manager)
        assertTrue(manager.indexOf("captureTerrainScene(") < manager.indexOf("attachIrisSceneDepth("))
    }

    @Test
    fun `iris sodium overlay uses terrain depth without global bias`() {
        val overlay = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/sodium/CooSodiumTerrainOverlay.kt"
        )

        val irisBranch = overlay.substringAfter("if (irisComposite) {")
            .substringBefore("val shader =")
        assertTrue("RenderSystem.depthMask(false)" in irisBranch)
        assertTrue("RenderSystem.depthFunc(GL_LEQUAL)" in irisBranch)
        assertFalse("glPolygonOffset" in overlay)
        assertFalse("glDepthRange" in overlay)
        assertTrue("OpenGlPostEffectExecutionBackend.withPreservedGlState" in overlay)
        val backend = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/OpenGlPostEffectExecutionBackend.kt"
        )
        assertTrue("previousShaderTextures" in backend)
        assertTrue("previousColorMask" in backend)
        assertTrue("previousVertexArray" in backend)
         assertTrue("glIsVertexArray(previousVertexArray)" in backend)
         assertTrue("safePreviousVertexArray" in backend)
        assertTrue("previousPolygonOffsetFactor" in backend)
        assertTrue("previousPolygonOffsetUnits" in backend)
        assertTrue("previousTextureBindings" in backend)
        assertTrue("RenderSystem.activeTexture(GL_TEXTURE0 + index)" in backend)
        assertTrue("RenderSystem.bindTexture(previousTextureBindings[index])" in backend)
        assertTrue("glBindTexture(GL_TEXTURE_2D, previousTextureBindings[index])" in backend)
        assertTrue("RenderSystem.activeTexture(previousActive)" in backend)
        assertTrue("RenderSystem.polygonOffset(previousPolygonOffsetFactor, previousPolygonOffsetUnits)" in backend)
        assertTrue("previousScissorBox" in backend)
        assertTrue("val previousShader = RenderSystem.getShader()" in backend)
        assertTrue("RenderSystem.setShader { previousShader }" in backend)
    }

    private fun source(path: String): String {
        return projectPath(path).readText()
    }

    private fun projectPath(path: String): NioPath {
        val requested = Path(path)
        if (requested.exists()) return requested
        var cursor = Path(System.getProperty("user.dir")).absolute()
        while (cursor.parent != null) {
            val candidate = cursor.resolve(path)
            if (candidate.exists()) return candidate
            cursor = cursor.parent
        }
        return requested
    }

    private fun packUv(value: Float): Int {
        return (value.coerceIn(0F, 1F) * 65535F).toInt() - 32768
    }

    private class RecordingVertexConsumer : VertexConsumer {
        var baseUv: Pair<Float, Float>? = null
        var effectUv: Pair<Int, Int>? = null
        var lightUv: Pair<Int, Int>? = null

        override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer = this

        override fun setColor(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer = this

        override fun setUv(u: Float, v: Float): VertexConsumer = apply {
            baseUv = u to v
        }

        override fun setUv1(u: Int, v: Int): VertexConsumer = apply {
            effectUv = u to v
        }

        override fun setUv2(u: Int, v: Int): VertexConsumer = apply {
            lightUv = u to v
        }

        override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer = this
    }
}
