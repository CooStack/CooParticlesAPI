package cn.coostack.cooparticlesapi.renderer.pipeline

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameStage
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneTargets
import cn.coostack.cooparticlesapi.renderer.post.PostEffectInputSource
import cn.coostack.cooparticlesapi.renderer.post.PostEffectOutput
import cn.coostack.cooparticlesapi.renderer.post.PostEffectParamValue
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.RenderInput
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.CooTextureFormat
import cn.coostack.cooparticlesapi.test.options.renderer.world.DemoLightOrbRenderEntity
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 校验 Pipeline 纹理契约、BSL Bloom 图编译和不可变参数配置。 */
class CooRenderPipelineTest {
    /** 为动态 uniform 测试提供亮度输入。 */
    private data class Subject(val bright: Float)

    @Test
    fun `nodes default to rgba8 level zero while external pipelines can request hdr mip targets`() {
        val defaultPipeline = CooPipelines.generic<Any>(id("default_texture_contract")) {
            val main = pass("main") {
                fragment(id("post/main.fsh"))
            }
            line(main.color(), screenTarget())
        }
        val defaultOutput = defaultPipeline.nodes.single().outputs.single()
        assertEquals(CooTextureFormat.RGBA8, defaultOutput.format)
        assertEquals(1, defaultOutput.mipLevels)

        val hdrPipeline = CooPipelines.generic<Any>(id("hdr_texture_contract")) {
            val hdr = pass("hdr") {
                fragment(id("post/hdr.fsh"))
                outputFormat(CooTextureFormat.RGBA16F)
                mipLevels(4)
            }
            val composite = pass("composite") {
                fragment(id("post/composite.fsh"))
                input("Hdr", format = CooTextureFormat.RGBA16F, mipLevels = 4)
            }
            line(hdr.color(), composite.input("Hdr"))
            line(composite.color(), screenTarget())
        }

        val compiled = CooPipelineCompiler.compile(hdrPipeline)
        val hdrOutput = compiled.nodes.single { it.name == "hdr" }.outputs.single()
        val hdrInput = compiled.nodes.single { it.name == "composite" }.inputs.single()
        assertEquals(CooTextureFormat.RGBA16F, hdrOutput.format)
        assertEquals(4, hdrOutput.mipLevels)
        assertEquals(CooTextureFormat.RGBA16F, hdrInput.expectedFormat)
        assertEquals(4, hdrInput.minimumMipLevels)
    }

    @Test
    fun `compiler rejects incompatible texture format and mip contracts`() {
        val formatMismatch = CooPipelines.generic<Any>(id("format_mismatch")) {
            val source = pass("source") { fragment(id("post/source.fsh")) }
            val sink = pass("sink") {
                fragment(id("post/sink.fsh"))
                input("Input", format = CooTextureFormat.RGBA16F)
            }
            line(source.color(), sink.input("Input"))
            line(sink.color(), screenTarget())
        }
        assertFailsWith<IllegalArgumentException> { CooPipelineCompiler.compile(formatMismatch) }

        val mipMismatch = CooPipelines.generic<Any>(id("mip_mismatch")) {
            val source = pass("source") {
                fragment(id("post/source.fsh"))
                mipLevels(2)
            }
            val sink = pass("sink") {
                fragment(id("post/sink.fsh"))
                input("Input", mipLevels = 3)
            }
            line(source.color(), sink.input("Input"))
            line(sink.color(), screenTarget())
        }
        assertFailsWith<IllegalArgumentException> { CooPipelineCompiler.compile(mipMismatch) }
    }

    @Test
    fun `mask bloom keeps a full source mip chain and configures reconstructed levels`() {
        val preset = CooPipelines.MASK_BLOOM
        val nodes = preset.nodes.associateBy(CooPipelineNode::name)
        val geometry = nodes.getValue("geometry")
        val geometryColor = geometry.color()
        val geometryMask = geometry.mask()
        assertEquals(listOf(geometryColor, geometryMask), geometry.outputs)
        assertEquals(CooTextureFormat.RGBA16F, geometryColor.format)
        assertEquals(CooTextureFormat.RGBA16F, geometryMask.format)
        assertTrue(preset.lines.any { line ->
            line.output == geometryColor && line.input is CooPipelineTarget.World
        })
        assertTrue(preset.lines.any { line ->
            line.output == geometryMask &&
                (line.input as? CooPipelineInputPort)?.node == "bloom_extract"
        })
        assertTrue(geometry.outputs.any { output -> output.semantic == CooPipelineOutputSemantic.MASK })
        val compiledPipeline = CooPipelineCompiler.compile(preset)
        assertEquals(
            listOf(geometryMask),
            compiledPipeline.attachments
                .filter { attachment -> attachment.output.node == geometry.name }
                .map(CooCompiledAttachment::output)
        )
        assertEquals(CooTextureFormat.RGBA16F, nodes.getValue("bloom_extract").outputs.first().format)
        assertEquals(12, nodes.getValue("bloom_extract").outputs.first().mipLevels)
        assertEquals(CooTextureFormat.RGBA16F, nodes.getValue("bloom_bsl_atlas").outputs.first().format)
        assertEquals(1, nodes.getValue("bloom_bsl_atlas").outputs.first().mipLevels)
        assertEquals(CooTextureFormat.RGBA8, nodes.getValue("composite").outputs.first().format)
        assertEquals(2, nodes.getValue("composite").inputs.size)

        val passes = requireNotNull(CooPipelinePostEffectCompiler.compile(preset)).type.chain.passes
        val extractPass = passes.single { it.name == "bloom_extract" }
        val atlasPass = passes.single { it.name == "bloom_bsl_atlas" }
        assertTrue(extractPass.generateMipmaps)
        assertEquals(12, extractPass.mipLevels)
        assertEquals(1, atlasPass.mipLevels)
        assertEquals(CooTextureFormat.RGBA16F, atlasPass.outputFormat)

        val configured = preset.bloomMipLevels(4)
        assertEquals(
            12,
            configured.nodes.single { it.name == "bloom_extract" }.outputs.first().mipLevels
        )
        assertEquals(
            12,
            configured.nodes.single { it.name == "bloom_bsl_atlas" }
                .inputs.single { it.sampler == "BloomInput" }
                .minimumMipLevels
        )
        assertEquals(
            CooUniformValue.IntValue(4),
            configured.resolveUniform("composite", "MipLevels", Subject(1F))
        )
        assertEquals(
            CooUniformValue.IntValue(4),
            configured.resolveUniform("bloom_bsl_atlas", "BloomLevels", Subject(1F))
        )
        val configuredPasses = requireNotNull(CooPipelinePostEffectCompiler.compile(configured)).type.chain.passes
        assertEquals(12, configuredPasses.single { it.name == "bloom_extract" }.mipLevels)
        assertEquals(1, configuredPasses.single { it.name == "bloom_bsl_atlas" }.mipLevels)
        assertFailsWith<IllegalArgumentException> { preset.bloomMipLevels(0) }
        assertFailsWith<IllegalArgumentException> { preset.bloomMipLevels(8) }
    }

    @Test
    fun `preset configuration returns immutable graph variants`() {
        val preset = CooPipelines.MASK_BLOOM
        val staticIntensity = preset.intensity(2.8F)
        val configured: CooRenderPipeline<Subject> = preset
            .intensity { subject: Subject -> 2.8F * subject.bright.coerceAtLeast(0F) }

        assertTrue(RenderFrameStage.SCENE_POST in preset.stages)
        assertTrue(RenderFrameStage.FRAME_POST !in preset.stages)
        assertNotSame(preset, configured)
        assertNull(preset.resolveUniform("bloom_bsl_atlas", "Sigma", Subject(1F)))
        assertEquals(
            CooUniformValue.FloatValue(2.8F),
            staticIntensity.resolveUniform("bloom_extract", "Intensity", Subject(1F))
        )
        assertNull(configured.resolveUniform("bloom_bsl_atlas", "Sigma", Subject(1F)))
        assertEquals(5.6F, configured.resolveIntensity(Subject(2F)))
        assertEquals(
            CooUniformValue.FloatValue(5.6F),
            configured.resolveUniform("bloom_extract", "Intensity", Subject(2F))
        )
        val composite = requireNotNull(CooPipelinePostEffectCompiler.compile(configured))
            .type.chain.passes.single { it.name == "composite" }
        assertTrue(composite.uniforms.none { it.name == "Intensity" })
    }

    @Test
    fun `mask bloom expands to hdr extract atlas and composite chain`() {
        val pipeline = CooPipelines.MASK_BLOOM
        val compiled = requireNotNull(CooPipelinePostEffectCompiler.compile(pipeline))
        val passes = compiled.type.chain.passes
        val extract = passes.single { it.name == "bloom_extract" }
        val atlas = passes.single { it.name == "bloom_bsl_atlas" }
        val composite = passes.single { it.name == "composite" }
        val geometry = pipeline.nodes.single { it.name == "geometry" }

        assertEquals(1, geometry.mask().attachment)
        assertEquals(PostEffectInputSource.SCENE_RESOURCE, extract.inputs.single().source)
        assertEquals(1, extract.inputs.single().sourceResourceAttachment)
        assertEquals(
            "bloom_extract",
            atlas.inputs.single { it.samplerName == "BloomInput" }.sourcePassName
        )
        assertEquals(PostEffectInputSource.PASS_OUTPUT, atlas.inputs.single().source)
        assertTrue(composite.inputs.none { it.samplerName == "BloomSource" })
        assertTrue(composite.inputs.none { it.samplerName == "BloomCore" })
        assertEquals(
            "bloom_bsl_atlas",
            composite.inputs.single { it.samplerName == "BloomAtlas" }.sourcePassName
        )
        assertEquals(
            PostEffectInputSource.SCENE_COLOR,
            composite.inputs.single { it.samplerName == "SceneColor" }.source
        )
    }

    @Test
    fun `mask bloom exposes threshold parameters and defaults to unfiltered mask`() {
        val preset = CooPipelines.MASK_BLOOM
        assertEquals(
            CooUniformValue.FloatValue(0F),
            preset.resolveUniform("bloom_extract", "threshold", Subject(1F))
        )
        assertEquals(
            CooUniformValue.FloatValue(0.5F),
            preset.resolveUniform("bloom_extract", "softKnee", Subject(1F))
        )
        assertEquals(
            CooUniformValue.BoolValue(true),
            preset.resolveUniform("bloom_extract", "PremultipliedInput", Subject(1F))
        )
        assertEquals(
            CooUniformValue.FloatValue(3F),
            preset.resolveUniform("bloom_extract", "Intensity", Subject(1F))
        )
        val configured = preset.bloomThreshold(0.75F).bloomSoftKnee(0.2F)
        assertEquals(
            CooUniformValue.FloatValue(0.75F),
            configured.resolveUniform("bloom_extract", "threshold", Subject(1F))
        )
        assertEquals(
            CooUniformValue.FloatValue(0.2F),
            configured.resolveUniform("bloom_extract", "softKnee", Subject(1F))
        )
    }

    @Test
    fun `template parameters accept static and dynamic uniform values`() {
        val fixed = CooPipelines.MASK_BLOOM.parameter(
            "intensity",
            CooUniformValue.FloatValue(0.8F)
        )
        val dynamic: CooRenderPipeline<Subject> = CooPipelines.MASK_BLOOM
            .parameterValue("intensity") { subject: Subject ->
                CooUniformValue.FloatValue(subject.bright)
            }

        assertEquals(
            CooUniformValue.FloatValue(0.8F),
            fixed.resolveUniform("bloom_extract", "Intensity", Subject(3F))
        )
        assertEquals(
            CooUniformValue.FloatValue(3F),
            dynamic.resolveUniform("bloom_extract", "Intensity", Subject(3F))
        )
    }

    @Test
    fun `nothing template assigns to a concrete render entity renderer`() {
        val renderer = object : RenderEntityRenderer<DemoLightOrbRenderEntity> {
            override val pipeline = CooPipelines.MASK_BLOOM.intensity { entity: DemoLightOrbRenderEntity ->
                entity.intensity
            }

            override fun render(input: RenderInput<DemoLightOrbRenderEntity>) = Unit
        }
        val entity = DemoLightOrbRenderEntity().apply { intensity = 2.4F }

        assertEquals(
            CooUniformValue.FloatValue(2.4F),
            renderer.pipeline.resolveUniform("bloom_extract", "Intensity", entity)
        )
    }

    @Test
    fun `dynamic uniform resolves against current subject`() {
        val pipeline: CooRenderPipeline<Subject> = CooPipelines.DEFAULT.uniform("strength") { subject: Subject ->
            subject.bright * 0.5F
        }

        assertEquals(CooUniformValue.FloatValue(1.5F), pipeline.resolveUniform("strength", Subject(3F)))
    }

    /** 验证共享 post pass 会从每个运行时实例读取各自的 subject。 */
    @Test
    fun `compiled post effect resolves uniforms from each instance subject`() {
        val pipeline = CooPipelines.generic<Subject>(id("instance_subject")) {
            val main = pass("main") {
                fragment(id("post/instance_subject.fsh"))
                uniform("strength") { subject: Subject -> subject.bright * 0.5F }
            }
            line(main.color(), screenTarget())
        }
        val compiled = requireNotNull(CooPipelinePostEffectCompiler.compile(pipeline))
        val uniform = compiled.type.chain.passes.single().uniforms.single { it.name == "strength" }

        val first = compiled.type.create(subject = Subject(2F))
        val second = compiled.type.create(subject = Subject(6F))

        assertEquals(PostEffectParamValue.FloatValue(1F), uniform.provider(first))
        assertEquals(PostEffectParamValue.FloatValue(3F), uniform.provider(second))
    }

    @Test
    fun `compiler orders nodes from explicit lines`() {
        val pipeline = CooPipelines.generic<Any>(id("ordered")) {
            val composite = pass("composite") {
                fragment(id("post/composite.fsh"))
                input("Blurred")
                order(0)
            }
            val blur = pass("blur") {
                fragment(id("post/blur.fsh"))
                input("SceneColor")
                order(20)
            }
            line(sceneColor(), blur.input("SceneColor"))
            line(blur.color(), composite.input("Blurred"))
            line(composite.color(), screenTarget())
        }

        assertEquals(listOf("blur", "composite"), CooPipelineCompiler.compile(pipeline).nodes.map { it.name })
    }

    @Test
    fun `node attachment is a texture source for another node`() {
        val pipeline = CooPipelines.generic<Any>(id("attachments")) {
            val gbuffer = pass("gbuffer") {
                fragment(id("post/gbuffer.fsh"))
                input("SceneColor")
                colorAttachments(2)
            }
            val lighting = pass("lighting") {
                fragment(id("post/lighting.fsh"))
                input("Normal")
            }
            line(sceneColor(), gbuffer.input("SceneColor"))
            line(gbuffer.color(1), lighting.input("Normal"))
            line(lighting.color(), screenTarget())
        }
        val compiled = CooPipelineCompiler.compile(pipeline)

        assertTrue(compiled.lines.any { it.output == pipeline.nodes.first().color(1) })
        assertTrue(compiled.attachments.any { it.output == pipeline.nodes.first().color(1) })
        val post = requireNotNull(CooPipelinePostEffectCompiler.compile(pipeline))
        val normal = post.type.chain.passes.single { it.name == "lighting" }.inputs.single()
        assertEquals("gbuffer", normal.sourcePassName)
        assertEquals(1, normal.sourcePassAttachment)
    }

    @Test
    fun `fullscreen card keeps an explicitly declared vertex shader`() {
        val vertex = id("post/custom_screen.vsh")
        val pipeline = CooPipelines.generic<Any>(id("custom_vertex")) {
            val main = pass("main") {
                vertex(vertex)
                fragment(id("post/custom_screen.fsh"))
            }
            line(main.color(), screenTarget())
        }

        val pass = requireNotNull(CooPipelinePostEffectCompiler.compile(pipeline)).type.chain.passes.single()

        assertEquals(vertex, pass.vertex)
    }

    @Test
    fun `line compiler keeps each node framebuffer identity`() {
        val pipeline = CooPipelines.generic<Any>(id("line_identity")) {
            val extract = pass("extract") {
                fragment(id("post/extract.fsh"))
                input("SceneColor")
            }
            val blur = pass("blur") {
                fragment(id("post/blur.fsh"))
                input("Extracted")
            }
            val composite = pass("composite") {
                fragment(id("post/composite.fsh"))
                input("Blurred")
            }
            line(sceneColor(), extract.input("SceneColor"))
            line(extract.color(), blur.input("Extracted"))
            line(blur.color(), composite.input("Blurred"))
            line(composite.color(), screenTarget())
        }

        val compiled = CooPipelineCompiler.compile(pipeline)
        val extractTarget = requireNotNull(compiled.attachment(pipeline.nodes[0].color())).framebuffer
        val blurTarget = requireNotNull(compiled.attachment(pipeline.nodes[1].color())).framebuffer

        assertNotEquals(RenderSceneTargets.TEMPORARY, extractTarget)
        assertNotEquals(RenderSceneTargets.TEMPORARY, blurTarget)
        assertNotEquals(extractTarget, blurTarget)
    }

    @Test
    fun `named framebuffer keeps requested color attachment`() {
        val namedFramebuffer = id("framebuffer/gbuffer")
        val pipeline = CooPipelines.generic<Any>(id("named_framebuffer")) {
            val gbuffer = pass("gbuffer") {
                fragment(id("post/gbuffer.fsh"))
                colorAttachments(2)
            }
            line(gbuffer.color(1), framebufferTarget(namedFramebuffer, 1))
        }

        val compiled = CooPipelineCompiler.compile(pipeline)
        val secondAttachment = requireNotNull(compiled.attachment(pipeline.nodes.single().color(1)))
        val postPass = requireNotNull(CooPipelinePostEffectCompiler.compile(pipeline)).type.chain.passes.single()

        assertEquals(namedFramebuffer, secondAttachment.framebuffer)
        assertEquals(2, postPass.colorAttachmentCount)
        assertEquals(namedFramebuffer, postPass.outputTargetId)
    }

    @Test
    fun `named framebuffer read is ordered after its writer`() {
        val namedFramebuffer = id("framebuffer/ordered")
        val pipeline = CooPipelines.generic<Any>(id("named_framebuffer_order")) {
            val resolve = pass("resolve") {
                fragment(id("post/resolve.fsh"))
                inputFramebuffer("Color", namedFramebuffer)
                order(-10)
            }
            val capture = pass("capture") {
                fragment(id("post/capture.fsh"))
                order(20)
            }
            line(capture.color(), framebufferTarget(namedFramebuffer))
            line(resolve.color(), screenTarget())
        }

        val compiled = CooPipelineCompiler.compile(pipeline)
        val captureOutput = pipeline.nodes.single { it.name == "capture" }.color()
        val attachment = requireNotNull(compiled.attachment(captureOutput))
        val post = requireNotNull(CooPipelinePostEffectCompiler.compile(pipeline))
        val resolveInput = post.type.chain.passes.single { it.name == "resolve" }.inputs.single()

        assertEquals(listOf("capture", "resolve"), compiled.nodes.map { it.name })
        assertEquals(1, attachment.lastUse)
        assertEquals(PostEffectInputSource.PASS_OUTPUT, resolveInput.source)
        assertEquals("capture", resolveInput.sourcePassName)
        assertEquals(0, resolveInput.sourcePassAttachment)
    }

    @Test
    fun `world card can terminate at a named framebuffer without a fullscreen card`() {
        val namedFramebuffer = id("framebuffer/terrain_capture")
        val pipeline = CooPipelines.block(id("world_named_framebuffer")) {
            val terrain = world("terrain") {
                shader(id("terrain/capture"))
                colorAttachments(2)
            }
            line(terrain.color(1), framebufferTarget(namedFramebuffer, 1))
        }

        val compiled = CooPipelineCompiler.compile(pipeline)
        val attachment = requireNotNull(compiled.attachment(pipeline.nodes.single().color(1)))

        assertEquals(namedFramebuffer, attachment.framebuffer)
        assertTrue(compiled.nodes.all { it.kind == CooPipelineNodeKind.WORLD })
        assertEquals(null, CooPipelinePostEffectCompiler.compile(pipeline))
    }

    @Test
    fun `ping pong card compiles exact iterations with alternating targets and uniforms`() {
        val pipeline = CooPipelines.generic<Any>(id("ping_pong")) {
            val relax = pingPong("relax", iterations = 4) {
                fragment(id("post/relax.fsh"))
                alternate(
                    "Axis",
                    CooUniformValue.Vec2Value(1F, 0F),
                    CooUniformValue.Vec2Value(0F, 1F)
                )
                iterationUniform("Iteration") { iteration ->
                    CooUniformValue.IntValue(iteration.index)
                }
            }
            val composite = pass("composite") {
                fragment(id("post/composite.fsh"))
                input("Relaxed")
            }
            line(sceneColor(), relax.input("Input"))
            line(relax.color(), composite.input("Relaxed"))
            line(composite.color(), screenTarget())
        }

        val compiled = requireNotNull(CooPipelinePostEffectCompiler.compile(pipeline))
        val iterations = compiled.type.chain.passes.filter { it.name.startsWith("relax_iteration_") }
        val instance = compiled.type.create(params = compiled.defaultParams)

        assertEquals(4, iterations.size)
        assertEquals(PostEffectInputSource.SCENE_COLOR, iterations[0].inputs.single().source)
        iterations.drop(1).forEachIndexed { index, pass ->
            val feedback = pass.inputs.single { it.samplerName == "Input" }
            assertEquals(PostEffectInputSource.PASS_OUTPUT, feedback.source)
            assertEquals("relax_iteration_$index", feedback.sourcePassName)
        }
        assertEquals(2, iterations.map { it.outputTargetKey }.toSet().size)
        assertTrue(iterations.all { it.reuseOutputTarget })
        assertEquals(
            listOf(
                PostEffectParamValue.Vec2Value(1F, 0F),
                PostEffectParamValue.Vec2Value(0F, 1F),
                PostEffectParamValue.Vec2Value(1F, 0F),
                PostEffectParamValue.Vec2Value(0F, 1F)
            ),
            iterations.map { pass ->
                pass.uniforms.single { it.name == "Axis" }.provider(instance)
            }
        )
        assertEquals(
            "relax_iteration_3",
            compiled.type.chain.passes.single { it.name == "composite" }
                .inputs.single { it.samplerName == "Relaxed" }
                .sourcePassName
        )
    }

    @Test
    fun `ping pong card rejects non-positive iteration count`() {
        assertFailsWith<IllegalArgumentException> {
            CooPipelines.generic<Any>(id("invalid_ping_pong")) {
                pingPong("relax", iterations = 0) {
                    fragment(id("post/relax.fsh"))
                }
            }
        }
    }

    @Test
    fun `terminal ping pong card keeps separate feedback targets`() {
        val pipeline = CooPipelines.generic<Any>(id("terminal_ping_pong")) {
            val relax = pingPong("relax", iterations = 3) {
                fragment(id("post/relax.fsh"))
            }
            line(sceneColor(), relax.input("Input"))
            line(relax.color(), screenTarget())
        }

        val compiled = requireNotNull(CooPipelinePostEffectCompiler.compile(pipeline))
        val iterations = compiled.type.chain.passes.filter { it.name.startsWith("relax_iteration_") }

        assertEquals(3, iterations.size)
        assertNotEquals(iterations[0].outputTargetKey, iterations[1].outputTargetKey)
        assertEquals(iterations[0].outputTargetKey, iterations[2].outputTargetKey)
        assertEquals(PostEffectOutput.FINAL_SCREEN, iterations.last().output)
    }

    @Test
    fun `compiler rejects final screen combined with another target`() {
        val pipeline = CooPipelines.generic<Any>(id("ambiguous_final_output")) {
            val main = pass("main") {
                fragment(id("post/main.fsh"))
            }
            line(main.color(), screenTarget())
            line(main.color(), bloomTarget())
        }

        assertFailsWith<IllegalArgumentException> { CooPipelineCompiler.compile(pipeline) }
    }

    @Test
    fun `compiler rejects non-zero attachment connected to screen`() {
        val pipeline = CooPipelines.generic<Any>(id("invalid_screen_attachment")) {
            val main = pass("main") {
                fragment(id("post/main.fsh"))
                colorAttachments(2)
            }
            line(main.color(1), screenTarget())
        }

        assertFailsWith<IllegalArgumentException> { CooPipelineCompiler.compile(pipeline) }
    }

    @Test
    fun `compiler derives scene resources and capabilities`() {
        val pipeline = CooPipelines.generic<Any>(id("resources")) {
            val main = pass("main") {
                fragment(id("post/main.fsh"))
                input("SceneColor")
                input("SceneDepth", optional = true)
                input("Mask")
            }
            line(sceneColor(), main.input("SceneColor"))
            line(sceneDepth(), main.input("SceneDepth"))
            line(mask(), main.input("Mask"))
            line(main.color(), bloomTarget())
        }
        val compiled = CooPipelineCompiler.compile(pipeline)

        assertTrue(RenderSceneTargets.SCENE_COLOR in compiled.sceneTargets)
        assertTrue(RenderSceneTargets.SCENE_DEPTH in compiled.sceneTargets)
        assertTrue(RenderSceneTargets.MASK in compiled.sceneTargets)
        assertTrue(RenderSceneTargets.BLOOM in compiled.sceneTargets)
        assertTrue(RenderBackendCapability.SCENE_COLOR_COPY in compiled.requiredCapabilities)
        assertTrue(RenderBackendCapability.SCENE_DEPTH_READ in compiled.optionalCapabilities)
    }

    @Test
    fun `world nodes reject graph output inputs`() {
        assertFailsWith<IllegalArgumentException> {
            CooPipelines.generic<Any>(id("world_output_input")) {
                val source = world("source")
                val target = world("target") {
                    input("Input")
                }
                line(source.color(), target.input("Input"))
            }
        }
    }

    @Test
    fun `compiler rejects a line cycle`() {
        val pipeline = CooPipelines.generic<Any>(id("cycle")) {
            val first = pass("first") {
                fragment(id("post/first.fsh"))
                input("FromSecond")
            }
            val second = pass("second") {
                fragment(id("post/second.fsh"))
                input("FromFirst")
            }
            line(first.color(), second.input("FromFirst"))
            line(second.color(), first.input("FromSecond"))
        }

        assertFailsWith<IllegalArgumentException> { CooPipelineCompiler.compile(pipeline) }
    }

    private fun id(path: String): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, path)
    }
}
