package cn.coostack.cooparticlesapi.test.options.renderer

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DemoWorldRenderEffectOptionsContractTest {
    @Test
    fun `world object visuals are render entity demos not post effect demos`() {
        val postOptions = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/options/renderer/PostEffectDemoOptions.kt"
        )
        val apiBuilder = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/APITestGroupBuilder.kt"
        )
        val worldOptions = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/options/renderer/world/DemoWorldRenderEffectOptions.kt"
        )

        assertFalse("blackHole(" in postOptions)
        assertFalse("isolatedLightOrbBloom" in postOptions)
        assertFalse("isolatedLightBeamBloom" in postOptions)
        assertFalse("roaringSoundWave" in postOptions)
        assertTrue("DemoWorldRenderEffectOptions.blackHole(player)" in apiBuilder)
        assertTrue("DemoWorldRenderEffectOptions.shield(player)" in apiBuilder)
        assertTrue("DemoWorldRenderEffectOptions.lightBeam(player)" in apiBuilder)
        assertTrue("DemoWorldRenderEffectOptions.lightOrb(player)" in apiBuilder)
        assertTrue("SimpleRendererEntityOption" in worldOptions)
        assertTrue("DemoBlackHoleRenderEntity" in worldOptions)
        assertTrue("DemoShieldRenderEntity" in worldOptions)
        assertTrue("DemoLightBeamRenderEntity" in worldOptions)
        assertTrue("DemoLightOrbRenderEntity" in worldOptions)
    }

    @Test
    fun `world object render entities are four separate auto render entities`() {
        val entitySources = listOf(
            "DemoBlackHoleRenderEntity.kt",
            "DemoShieldRenderEntity.kt",
            "DemoLightBeamRenderEntity.kt",
            "DemoLightOrbRenderEntity.kt"
        ).map { fileName ->
            readProjectFile(
                "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/options/renderer/world/$fileName"
            )
        }
        val rendererFiles = listOf(
            "DemoBlackHoleRenderEntityRenderer.kt" to "DemoBlackHoleRenderEntity",
            "DemoShieldRenderEntityRenderer.kt" to "DemoShieldRenderEntity",
            "DemoLightBeamRenderEntityRenderer.kt" to "DemoLightBeamRenderEntity",
            "DemoLightOrbRenderEntityRenderer.kt" to "DemoLightOrbRenderEntity"
        )
        val rendererSources = rendererFiles.map { (fileName, _) ->
            readProjectFile(
                "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/options/renderer/world/$fileName"
            )
        }
        val support = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/options/renderer/world/DemoWorldRenderModelSupport.kt"
        )
        val clientRegistry = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/options/renderer/world/DemoWorldRenderEffectClientRegistry.kt"
        )
        val manager = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/client/ClientRenderEntityManager.kt"
        )

        entitySources.forEach { entity ->
            assertTrue("@CooAutoRegister" in entity)
            assertTrue("AutoRenderEntity" in entity)
            assertTrue("@CodecField" in entity)
            assertFalse("kindId" in entity)
            assertFalse("writeUtf(" in entity)
            assertFalse("writeFloat(" in entity)
        }
        assertFalse(projectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/options/renderer/world/DemoWorldRenderEffectRenderer.kt"
        ).toFile().exists())
        rendererSources.forEach { renderer ->
            assertTrue("RenderEntityModelRenderer" in renderer)
            assertTrue("buildModel(" in renderer)
            assertFalse("RenderEntityModelRenderer<DemoWorldRenderEffectSpec>" in renderer)
            assertFalse("DemoWorldRenderEffectKind" in renderer)
            assertFalse("when (entity)" in renderer)
            assertFalse("Tesselator" in renderer)
            assertFalse("BufferUploader" in renderer)
            assertFalse("GameRenderer" in renderer)
        }
        rendererFiles.zip(rendererSources).forEach { (rendererFile, renderer) ->
            val (fileName, entityName) = rendererFile
            assertTrue("RenderEntityModelRenderer<$entityName>" in renderer)
            assertTrue("FramePostRenderEntityRenderer<$entityName>" in renderer)
            assertTrue("RenderEntityInstance<$entityName>" in renderer)
            assertTrue("buildModel(entity: $entityName" in renderer)
            assertTrue("collectRenderContributions(" in renderer)
            assertTrue("collectModelMaskBloom(" in renderer)
        }
        assertFalse("RenderEntityModelRenderer" in support)
        assertTrue("RenderEntityModelBuilder()" in support)
        assertTrue("model.pipe(\"world_model\")" in support)
        assertTrue("model.addVertex(pipe" in support)
        assertTrue("model.addTriangle(" in support)
        assertTrue("model.addQuad(" in support)
        assertFalse("post(BuiltinPostEffectTypes.HALO.id)" in support)
        assertFalse("post(BuiltinPostEffectTypes.BLOOM.id)" in support)
        assertTrue("BuiltinRenderEffectTypes.MASK_BLOOM" in support)
        assertTrue("BuiltinRenderEffectDescriptors.sharedModelMaskBloom(" in support)
        assertTrue("RenderEntityModelExecutors.active().draw" in support)
        assertFalse("RenderBackendCapability.SCENE_DEPTH_READ" in support)
        assertTrue("RenderBackendCapability.SAFE_WORLD_COMPOSITE" in support)
        assertTrue("CompositeMode.ADDITIVE" in support)
        assertTrue("RenderFrameStage.WORLD_PASS" in support)
        assertTrue("RenderSceneTargets.SCENE_COLOR" in support)
        assertTrue("RenderSceneTargets.SCENE_DEPTH" in support)
        assertTrue("needsSceneDepth = true" in support)
        assertTrue("effectGraphEnabled = true" in support)
        assertTrue("fun annulus(" in support)
        assertTrue("fun sphereShell(" in support)
        assertTrue("fun verticalBeam(" in support)
        assertTrue("fun spiral(" in support)
        assertTrue("DemoWorldRenderModelSupport.line(" in rendererSources[2])
        assertFalse("verticalBeam(" in rendererSources[2])
        assertTrue("sphereShell(" in rendererSources[3])
        assertTrue("annulus(" in rendererSources[0])
        assertTrue("sphereShell(" in rendererSources[1])
        assertTrue("DemoBlackHoleRenderEntity.ID" in clientRegistry)
        assertTrue("DemoBlackHoleRenderEntityRenderer()" in clientRegistry)
        assertTrue("DemoShieldRenderEntity.ID" in clientRegistry)
        assertTrue("DemoShieldRenderEntityRenderer()" in clientRegistry)
        assertTrue("DemoLightBeamRenderEntity.ID" in clientRegistry)
        assertTrue("DemoLightBeamRenderEntityRenderer()" in clientRegistry)
        assertTrue("DemoLightOrbRenderEntity.ID" in clientRegistry)
        assertTrue("DemoLightOrbRenderEntityRenderer()" in clientRegistry)
        assertTrue("ClientRenderEntityRegistry.registerRenderer" in clientRegistry)
        assertTrue("DemoWorldRenderEffectClientRegistry.register()" in manager)
    }

    @Test
    fun `render entity model pipe api stays simple and graph based`() {
        val builder = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/model/RenderEntityModelBuilder.kt"
        )
        val primitive = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/model/RenderEntityModelPrimitive.kt"
        )
        val primitiveMode = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/model/RenderEntityModelPrimitiveMode.kt"
        )
        val pipeBuilder = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/model/RenderEntityModelPipeBuilder.kt"
        )
        val graphBuilder = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/model/RenderEntityModelPipeGraphBuilder.kt"
        )
        val executor = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/model/OpenGlRenderEntityModelExecutor.kt"
        )
        val client = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/CooParticlesAPIClient.kt"
        )
        val instance = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/RenderEntityInstance.kt"
        )
        val vertexShader = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/core/vertex/render_entity_model.vsh"
        )
        val fragmentShader = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/core/fragment/render_entity_model.fsh"
        )
        val maskBloomExecutor = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/effects/builtin/OpenGlMaskBloomEffectExecutor.kt"
        )
        val maskBloomDescriptors = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/effects/builtin/BuiltinRenderEffectDescriptors.kt"
        )
        val maskBloomComposite = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/post/mask_bloom_composite.fsh"
        )
        val texturedBillboardFragment = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/core/fragment/mask_bloom_textured_billboard.fsh"
        )

        assertTrue("fun pipe(id: String" in builder)
        assertTrue("fun addVertex(" in builder)
        assertTrue("fun addTriangle(" in builder)
        assertTrue("fun addQuad(" in builder)
        assertTrue("RenderEntityModelPrimitiveMode.TRIANGLES" in builder)
        assertTrue("val primitiveMode: RenderEntityModelPrimitiveMode" in primitive)
        assertTrue("enum class RenderEntityModelPrimitiveMode" in primitiveMode)
        assertTrue("LINES" in primitiveMode)
        assertTrue("TRIANGLES" in primitiveMode)
        assertTrue("fun post(type: ResourceLocation)" in pipeBuilder)
        assertTrue("fun graph(block: RenderEntityModelPipeGraphBuilder.() -> Unit)" in pipeBuilder)
        assertTrue("fun connect(" in graphBuilder)
        assertTrue("RenderEntityModelRenderer" in instance)
        assertTrue("RenderEntityModelExecutors.active().draw" in instance)
        assertFalse("collectModelPostContributions" in instance)
        assertFalse("CooPostEffectTypes.get(pipe.postEffectType" in instance)
        assertFalse("PostEffectBinding.WorldPos(null, entity.pos.x" in instance)
        assertTrue("object OpenGlRenderEntityModelExecutor : RenderEntityModelExecutor" in executor)
        assertTrue("pipe.postEffectType == null" in executor)
        assertTrue("DynamicVertexBuffer" in executor)
        assertTrue("GL_LINES" in executor)
        assertTrue("GL_TRIANGLES" in executor)
        assertTrue("primitive.primitiveMode.toGlMode()" in executor)
        assertTrue("CooVertexFormat.POINT_COLOR_FORMAT" in executor)
        assertTrue("glEnable(GL_DEPTH_TEST)" in executor)
        assertTrue("glDepthFunc(GL_LEQUAL)" in executor)
        assertTrue("glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE" in executor)
        assertFalse("Tesselator" in executor)
        assertFalse("BufferUploader" in executor)
        assertFalse("GameRenderer" in executor)
        assertTrue("RenderEntityModelExecutors.install(OpenGlRenderEntityModelExecutor)" in client)
        assertTrue("RenderEffectRegistry.register(BuiltinRenderEffectTypes.MASK_BLOOM, OpenGlMaskBloomEffectExecutor)" in client)
        assertTrue("OpenGlRenderEntityModelExecutor.release()" in client)
        assertTrue("OpenGlMaskBloomEffectExecutor.release()" in client)
        assertTrue("RenderEntityModelExecutors.reset()" in client)
        assertTrue("layout (location = 1) in vec4 vertexColor" in vertexShader)
        assertTrue("uniform mat4 transMat" in vertexShader)
        assertTrue("uniform float intensity" in fragmentShader)
        assertTrue("object OpenGlMaskBloomEffectExecutor : RenderEffectExecutor" in maskBloomExecutor)
        assertTrue("MaskBloomRenderRequest" in maskBloomExecutor)
        assertTrue("request.renderMask(maskContext(context, request))" in maskBloomExecutor)
        assertTrue("sceneDepthFramebuffer?.let { copyDepthBuffer(it) }" in maskBloomExecutor)
        val maskBloomCapabilityBlock = maskBloomDescriptors
            .substringAfter("private val maskBloomCapabilities = setOf(")
            .substringBefore(")")
        assertTrue("private val maskBloomCapabilities = setOf" in maskBloomDescriptors)
        assertTrue("RenderBackendCapability.FINAL_FRAME_POST" in maskBloomCapabilityBlock)
        assertTrue("RenderBackendCapability.SAFE_WORLD_COMPOSITE" in maskBloomCapabilityBlock)
        assertFalse("RenderBackendCapability.SCENE_COLOR_COPY" in maskBloomCapabilityBlock)
        assertFalse("RenderBackendCapability.SCENE_DEPTH_READ" in maskBloomCapabilityBlock)
        assertTrue("requiredCapabilities: Set<RenderBackendCapability> = maskBloomCapabilities" in maskBloomDescriptors)
        assertTrue("Supplier { -1 }" in maskBloomExecutor)
        assertTrue("TARGET_SCALE_DIVISOR = 2" in maskBloomExecutor)
        assertTrue("post/bloom_bright_extract.fsh" in maskBloomExecutor)
        assertTrue("drawBrightExtract(" in maskBloomExecutor)
        assertTrue("setFloat(\"threshold\", config.threshold)" in maskBloomExecutor)
        assertTrue("setFloat(\"softKnee\", config.thresholdSoftness)" in maskBloomExecutor)
        assertTrue("post/bloom_blur_horizontal.fsh" in maskBloomExecutor)
        assertTrue("post/bloom_blur_vertical.fsh" in maskBloomExecutor)
        assertTrue("post/mask_bloom_composite.fsh" in maskBloomExecutor)
        assertTrue("glBlendFuncSeparate(GL_ONE, GL_ONE, GL_ZERO, GL_ONE)" in maskBloomExecutor)
        assertFalse("Supplier { depthTexture ?: -1 }" in maskBloomExecutor)
        assertFalse("Mask bloom textured billboard helper is not implemented" in maskBloomExecutor)
        assertTrue("drawTexturedBillboard(context, content)" in maskBloomExecutor)
        assertTrue("texturedBillboardProgram()" in maskBloomExecutor)
        assertTrue("core/vertex/billboard_from_model_uv.vsh" in maskBloomExecutor)
        assertTrue("core/fragment/mask_bloom_textured_billboard.fsh" in maskBloomExecutor)
        assertTrue("content.textures.drawWith" in maskBloomExecutor)
        assertTrue("CooVertexFormat.POINT_TEXTURE_UV_FORMAT" in maskBloomExecutor)
        assertTrue("glDepthFunc(GL_LEQUAL)" in maskBloomExecutor)
        assertTrue("uniform sampler2D bloom" in maskBloomComposite)
        assertTrue("uniform sampler2D mask" in maskBloomComposite)
        assertTrue("uniform sampler2D tex" in texturedBillboardFragment)
        assertTrue("uniform vec4 tint" in texturedBillboardFragment)
        assertTrue("uniform float sourceBoost" in texturedBillboardFragment)
        assertTrue("uniform bool fullQuadMask" in texturedBillboardFragment)
        assertTrue("smoothstep(alphaCutoff" in texturedBillboardFragment)
        assertTrue("discard" in texturedBillboardFragment)
    }

    private fun readProjectFile(relativePath: String): String {
        return Files.readString(projectFile(relativePath))
    }

    private fun projectFile(relativePath: String): Path {
        return Path.of(System.getProperty("user.dir")).resolve("..").resolve(relativePath).normalize()
    }
}
