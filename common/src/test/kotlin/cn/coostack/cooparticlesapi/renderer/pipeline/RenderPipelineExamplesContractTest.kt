package cn.coostack.cooparticlesapi.renderer.pipeline

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RenderPipelineExamplesContractTest {
    @Test
    fun `runnable examples use block pipelines and shader effects`() {
        val examples = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/options/renderer/pipeline/" +
                "RenderPipelineExamples.kt"
        )
        val client = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/CooParticlesAPIClient.kt"
        )
        val screenDistortion = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/post/screen_distortion.fsh"
        )
        val solidTint = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/core/terrain/solid_tint.fsh"
        )

        assertTrue("val ORIGINAL_TEXTURE_BLOCK = CooPipelines.block" in examples)
        assertTrue("inputBlockAtlas(\"BaseSampler\")" in examples)
        assertTrue("inputSceneColor(\"SceneColor\", optional = true)" in examples)
        assertTrue("uniform(\"EffectTint\", CooUniformValue.Vec3Value(0.1F, 0.85F, 0.35F))" in examples)
        assertTrue("uniform(\"EffectStrength\", 0F)" in examples)
        assertFalse("CooBlocks.TEST_CONTROLLER" in examples)
        assertFalse("LODESTONE_ENERGY" in examples)
        assertFalse("Blocks.LODESTONE" in examples)
        assertFalse("CooBlockPipelines.bind(Blocks.STONE" in examples)
        assertTrue("val SOLID_TINT_BLOCK = CooPipelines.block" in examples)
        assertTrue("fun bindVanillaBlockExample(block: Block)" in examples)
        assertFalse("BaseSampler" in solidTint)
        assertTrue("val HEAT_HAZE = CooShaderEffects.register" in examples)
        assertTrue("fun playHeatHaze() = HEAT_HAZE.play" in examples)
        assertTrue("uniform float radius" in screenDistortion)
        assertTrue("smoothstep" in screenDistortion)
        val originalTexture = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/core/terrain/original_texture.fsh"
        )
        assertTrue("uniform sampler2D SceneColor" in originalTexture)
        assertTrue("uniform int CooIrisComposite" in originalTexture)
        assertTrue("gl_FragCoord.xy / ScreenSize" in originalTexture)
        assertFalse("RenderPipelineExamples.registerBlockExamples()" in client)
        assertTrue("RenderPipelineExamples.ensureStarfieldFbo()" in client)
        assertFalse("ShaderPipe" in examples)
        assertFalse("PostEffectType" in examples)
        assertFalse("CooPostEffects" in examples)
    }

    @Test
    fun `starfield example renders a named fbo with screen facing terrain sampling`() {
        val examples = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/options/renderer/pipeline/" +
                "RenderPipelineExamples.kt"
        )
        val postShader = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/post/starfield_fbo.fsh"
        )
        val terrainShader = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/core/terrain/starfield_fbo.fsh"
        )

        assertTrue("val STARFIELD_FBO = CooShaderEffects.register" in examples)
        assertTrue("inputTexture(\"CosmosSampler\", id(\"test/terrain/cosmos.png\")" in examples)
        assertTrue("inputTexture(\"IridescenceSampler\", id(\"test/terrain/iridescence.png\")" in examples)
        assertTrue("outputToFramebuffer(TerrainFboExampleIds.STARFIELD_TARGET)" in examples)
        assertTrue("inputFramebuffer(\"StarfieldSampler\", TerrainFboExampleIds.STARFIELD_TARGET)" in examples)
        assertTrue("uniform float progress" in postShader)
        assertTrue("texture(IridescenceSampler" in postShader)
        assertTrue("gl_FragCoord.xy / max(ScreenSize" in terrainShader)
        assertTrue(
            Files.exists(
                projectFile("common/src/main/resources/assets/cooparticlesapi/textures/test/terrain/cosmos.png")
            )
        )
        assertTrue(
            Files.exists(
                projectFile("common/src/main/resources/assets/cooparticlesapi/textures/test/terrain/iridescence.png")
            )
        )
    }

    @Test
    fun `starfield block test binds the block type and removes it when cancelled`() {
        val option = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/block/" +
                "StarfieldFboBlockTypeTestOption.kt"
        )
        val packet = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/packet/server/" +
                "PacketBindStarfieldFboBlockS2C.kt"
        )
        val groupBuilder = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/block/builtin/BlockAPITestGroupBuilder.kt"
        )
        val client = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/CooParticlesAPIClient.kt"
        )

        assertTrue("BlockPos.containing(player.position())" in option)
        assertTrue("level.getBlockState(targetPos)" in option)
        assertTrue("PacketBindStarfieldFboBlockS2C(boundState, bindingId, true)" in option)
        assertTrue("PacketBindStarfieldFboBlockS2C(boundState, bindingId, false)" in option)
        assertTrue("override fun onFailed() = clearBinding()" in option)
        assertTrue("override fun onSuccess() = clearBinding()" in option)
        assertTrue("OPTION_ID = \"starfield-fbo-block-type\"" in option)
        assertTrue("StarfieldFboBlockTypeTestOption(player)" in groupBuilder)
        assertTrue("var state: BlockState" in packet)
        assertTrue("var bindingId: UUID" in packet)
        assertTrue("var enabled: Boolean" in packet)
        assertTrue("CooBlockPipelines.bindScoped(bindingId, state.block, RenderPipelineExamples.STARFIELD_BLOCK)" in packet)
        assertTrue("CooBlockPipelines.unbindScoped(bindingId)" in packet)
        assertFalse("BlockPos" in packet)
        val transientCleanup = client.substringAfter("fun clearTransientClientState()")
            .substringBefore("var subTicks")
        assertTrue("CooBlockPipelines.clearScopedBindings()" in transientCleanup)
    }

    @Test
    fun `render documentation uses current renderer annotation`() {
        val documentation = readProjectFile("docs/shader-renderentity-post-framework.md")

        assertTrue("@CooAutoRegisterRenderer\nclass MyRenderEntityRenderer" in documentation)
        assertFalse("@CooAutoRegisterRenderer(" in documentation)
        assertTrue("目前不支持从任意相机离屏重绘完整世界" in documentation)
        assertTrue("CooPipelines.entity<Nothing>(id(\"laser_mask_bloom\"))" in documentation)
        assertTrue("parameter(\"intensity\", source, \"MaskStrength\")" in documentation)
        assertTrue(".parameter(\"intensity\") { entity: LaserRenderEntity" in documentation)
        assertTrue("line(source, fromChannel = 1, resolve, toChannel = 1)" in documentation)
        assertTrue("相同 Pipeline ID 仍然共用 attachment" in documentation)
        assertTrue("值不同的实体会拆批执行" in documentation)
        assertTrue("CooPipelines.MASK_BLOOM.intensity(2.8F)" in documentation)
    }

    private fun readProjectFile(relativePath: String): String {
        return Files.readString(projectFile(relativePath))
    }

    private fun projectFile(relativePath: String): Path {
        var cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (cursor.parent != null) {
            if (Files.exists(cursor.resolve("settings.gradle"))) {
                return cursor.resolve(relativePath)
            }
            cursor = cursor.parent
        }
        error("Could not locate repository root from ${System.getProperty("user.dir")}")
    }
}
