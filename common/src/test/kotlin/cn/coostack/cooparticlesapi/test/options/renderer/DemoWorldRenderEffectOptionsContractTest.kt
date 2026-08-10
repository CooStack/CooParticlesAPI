package cn.coostack.cooparticlesapi.test.options.renderer

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DemoWorldRenderEffectOptionsContractTest {
    @Test
    fun `post effect demos use shader effect pipeline api`() {
        val options = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/options/renderer/PostEffectDemoOptions.kt"
        )
        val option = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/options/renderer/PostEffectDemoOption.kt"
        )

        assertTrue("CooShaderEffects.register" in options)
        assertTrue("pingPong(\"blur\"" in options)
        assertTrue("line(extract.color(), blur.input(\"bright\"))" in options)
        assertTrue("CooShaderEffect" in option)
        assertTrue("effect.play" in option)
        assertFalse("CooPostEffectTypes" in options)
        assertFalse("CooPostEffects" in options)
        assertFalse("PostEffectType" in options)
        assertFalse("PostEffectInstance" in option)
    }

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
            assertTrue("@CooAutoRegisterRenderer" in renderer)
            assertTrue("RenderEntityRenderer" in renderer)
            assertTrue("override val pipeline = CooPipelines.MASK_BLOOM" in renderer)
            assertTrue("override fun render(input: RenderInput" in renderer)
            assertTrue("buildModel(" in renderer)
            assertFalse("AutoRegisteredRenderEntityRenderer" in renderer)
            assertFalse("FramePostRenderEntityRenderer" in renderer)
            assertFalse("collectRenderContributions" in renderer)
            assertFalse("DemoWorldRenderEffectKind" in renderer)
            assertFalse("when (entity)" in renderer)
            assertFalse("Tesselator" in renderer)
            assertFalse("BufferUploader" in renderer)
            assertFalse("GameRenderer" in renderer)
        }
        rendererFiles.zip(rendererSources).forEach { (rendererFile, renderer) ->
            val (fileName, entityName) = rendererFile
            assertTrue("RenderEntityRenderer<$entityName>" in renderer, fileName)
            assertTrue("RenderInput<$entityName>" in renderer, fileName)
            assertTrue("buildModel(entity: $entityName" in renderer, fileName)
            assertTrue(".intensity { entity: $entityName" in renderer, fileName)
        }
        assertFalse("RenderEntityModelRenderer" in support)
        assertTrue("RenderEntityModelBuilder()" in support)
        assertTrue("model.layer(\"world_model\")" in support)
        assertTrue("model.addVertex(layer" in support)
        assertTrue("model.addTriangle(" in support)
        assertTrue("model.addQuad(" in support)
        assertFalse("post(BuiltinPostEffectTypes.HALO.id)" in support)
        assertFalse("post(BuiltinPostEffectTypes.BLOOM.id)" in support)
        assertTrue("RenderEntityModelExecutors.active().draw" in support)
        assertFalse("BuiltinRenderEffectDescriptors" in support)
        assertFalse("RenderContribution" in support)
        assertTrue("fun annulus(" in support)
        assertTrue("fun sphereShell(" in support)
        assertTrue("fun verticalBeam(" in support)
        assertTrue("fun spiral(" in support)
        assertTrue("DemoWorldRenderModelSupport.line(" in rendererSources[2])
        assertFalse("verticalBeam(" in rendererSources[2])
        assertTrue("sphereShell(" in rendererSources[3])
        assertTrue("annulus(" in rendererSources[0])
        assertTrue("sphereShell(" in rendererSources[1])
        assertFalse(projectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/options/renderer/world/DemoWorldRenderEffectClientRegistry.kt"
        ).toFile().exists())
        assertFalse("DemoWorldRenderEffectClientRegistry" in manager)
    }

    @Test
    fun `water ball declares its shader resources through pipeline`() {
        val renderer = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/options/renderer/world/" +
                "DemoWaterBallRenderEntityRenderer.kt"
        )

        assertTrue("CooPipelines.entity<DemoWaterBallRenderEntity>" in renderer)
        assertTrue("vertex(id(\"core/vertex/render_entity_water_ball.vsh\"))" in renderer)
        assertTrue("fragment(id(\"core/fragment/render_entity_water_ball.fsh\"))" in renderer)
        assertTrue("inputTexture(\"noiseTex\", id(\"noise.png\"))" in renderer)
        assertTrue("uniform(\"radius\")" in renderer)
        assertTrue("uniformValue(" in renderer)
        assertTrue("RenderEntityModelExecutors.active().draw" in renderer)
        assertFalse("AdvancedShaderProgramBuilder" in renderer)
        assertFalse("IdentifierTexture" in renderer)
        assertFalse("DynamicVertexBuffer" in renderer)
    }

    /** 验证模型渲染继续使用统一 pipeline，并复用静态图编译结果。 */
    @Test
    fun `render entity model layers use unified pipeline nodes`() {
        val builder = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/model/RenderEntityModelBuilder.kt"
        )
        val layer = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/model/RenderEntityModelLayer.kt"
        )
        val primitive = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/model/RenderEntityModelPrimitive.kt"
        )
        val primitiveMode = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/model/RenderEntityModelPrimitiveMode.kt"
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
        val pipelineRuntime = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/RenderEntityPipelineRuntime.kt"
        )
        val input = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/RenderInput.kt"
        )
        val vertexShader = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/core/vertex/render_entity_model.vsh"
        )
        val fragmentShader = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/core/fragment/render_entity_model.fsh"
        )
        val maskBloomDescriptors = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/effects/builtin/BuiltinRenderEffectDescriptors.kt"
        )

        assertTrue("fun layer(id: String)" in builder)
        assertTrue("fun addVertex(" in builder)
        assertTrue("fun addTriangle(" in builder)
        assertTrue("fun addQuad(" in builder)
        assertTrue("RenderEntityModelPrimitiveMode.TRIANGLES" in builder)
        assertTrue("data class RenderEntityModelLayer" in layer)
        assertFalse("val shader:" in layer)
        assertFalse("val postEffect" in layer)
        assertFalse("val params:" in layer)
        assertTrue("val layer: RenderEntityModelLayer" in primitive)
        assertTrue("val primitiveMode: RenderEntityModelPrimitiveMode" in primitive)
        assertTrue("enum class RenderEntityModelPrimitiveMode" in primitiveMode)
        assertTrue("LINES" in primitiveMode)
        assertTrue("TRIANGLES" in primitiveMode)
        listOf(
            "RenderEntityModelPipe.kt",
            "RenderEntityModelPipeBuilder.kt",
            "RenderEntityModelPipeGraph.kt",
            "RenderEntityModelPipeGraphBuilder.kt",
            "RenderEntityModelPipeNode.kt",
            "RenderEntityModelPipeEdge.kt",
            "RenderEntityModelPipeline.kt",
            "RenderEntityModelPipelineBuilder.kt",
            "RenderEntityModelPipelineGraph.kt",
            "RenderEntityModelPipelineGraphBuilder.kt",
            "RenderEntityModelPipelineNode.kt",
            "RenderEntityModelPipelineEdge.kt",
            "RenderEntityModelRenderer.kt",
            "RenderTypeRenderEntityModelExecutor.kt"
        ).forEach { fileName ->
            assertFalse(projectFile(
                "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/model/$fileName"
            ).toFile().exists(), fileName)
        }
        assertTrue("renderer.render(" in instance)
        assertTrue("RenderEntityPipelineRuntimeCache.get(renderer)" in instance)
        assertFalse("CooPipelineCompiler.compile(renderer.pipeline)" in instance)
        assertTrue("CooPipelineCompiler.compile(pipeline)" in pipelineRuntime)
        assertTrue("worldNodes.forEach { node" in instance)
        assertTrue("pipeline = renderer.pipeline" in instance)
        assertTrue("node = node" in instance)
        assertTrue("internal val pipeline: CooRenderPipeline<T>" in input)
        assertTrue("internal val node: CooPipelineNode" in input)
        assertFalse("collectModelPostContributions" in instance)
        assertFalse("CooPostEffectTypes.get(pipeline.postEffectType" in instance)
        assertFalse("PostEffectBinding.WorldPos(null, entity.pos.x" in instance)
        assertTrue("object OpenGlRenderEntityModelExecutor : RenderEntityModelExecutor" in executor)
        assertTrue("input.node.shader" in executor)
        assertTrue("is CooPipelineShader.Stages" in executor)
        assertTrue("is CooPipelineShader.Core" in executor)
        assertTrue("input.pipeline.lines.filter" in executor)
        assertTrue("input.node.uniforms.forEach" in executor)
        assertTrue("CooPipelineTextureSource.SceneColor" in executor)
        assertTrue("CooPipelineTextureSource.SceneDepth" in executor)
        assertFalse("pipeline.postEffectType" in executor)
        assertTrue("DynamicVertexBuffer" in executor)
        assertTrue("GL_LINES" in executor)
        assertTrue("GL_TRIANGLES" in executor)
        assertTrue("primitive.primitiveMode.toGlMode()" in executor)
        assertTrue("CooVertexFormat.POINT_COLOR_TEXTURE_UV_FORMAT" in executor)
        assertTrue("glEnable(GL_DEPTH_TEST)" in executor)
        assertTrue("glDepthFunc(GL_LEQUAL)" in executor)
        assertTrue("glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE" in executor)
        assertFalse("Tesselator" in executor)
        assertFalse("BufferUploader" in executor)
        assertFalse("GameRenderer" in executor)
        assertTrue("RenderEntityModelExecutors.install(OpenGlRenderEntityModelExecutor)" in client)
        assertTrue("OpenGlRenderEntityModelExecutor.release()" in client)
        assertTrue("RenderEntityModelExecutors.reset()" in client)
        assertTrue("layout (location = 1) in vec4 vertexColor" in vertexShader)
        assertTrue("layout (location = 2) in vec2 vertexUv" in vertexShader)
        assertTrue("uniform mat4 transMat" in vertexShader)
        assertTrue("uniform float intensity" in fragmentShader)
        assertFalse("MaskBloom" in maskBloomDescriptors)
        assertFalse(projectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/effects/builtin/OpenGlMaskBloomEffectExecutor.kt"
        ).toFile().exists())
    }

    private fun readProjectFile(relativePath: String): String {
        return Files.readString(projectFile(relativePath))
    }

    private fun projectFile(relativePath: String): Path {
        return Path.of(System.getProperty("user.dir")).resolve("..").resolve(relativePath).normalize()
    }
}
