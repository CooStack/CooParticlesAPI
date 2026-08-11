package cn.coostack.cooparticlesapi.renderer.post

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelinePostEffectCompiler
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelines
import net.minecraft.resources.ResourceLocation
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 行为契约测试：覆盖 PostEffectFrameExecutor / OpenGl backend / Iris backend 在
 * 重构后引入的关键不变量。
 *
 * Pipeline 编译部分直接验证对象行为；依赖客户端 OpenGL 环境的部分沿用源码契约检查。
 * 每条断言都对应一个可能影响帧间资源或执行顺序的回归点。
 */
class PostEffectFrameExecutorBehaviorTest {

    @Test
    fun `line keeps producer pass and attachment identity`() {
        val pipeline = CooPipelines.generic<Any>(id("behavior_line_attachment")) {
            val gbuffer = pass("gbuffer") {
                fragment(id("post/gbuffer.fsh"))
                input("SceneColor")
                colorAttachments(2)
            }
            val composite = pass("composite") {
                fragment(id("post/composite.fsh"))
                input("Normal")
            }
            line(sceneColor(), gbuffer.input("SceneColor"))
            line(gbuffer.color(1), composite.input("Normal"))
            line(composite.color(), screenTarget())
        }

        val passes = requireNotNull(CooPipelinePostEffectCompiler.compile(pipeline)).type.chain.passes
        val normalInput = passes.single { it.name == "composite" }.inputs.single()

        assertEquals(listOf("gbuffer", "composite"), passes.map(PostEffectPass::name))
        assertEquals(PostEffectInputSource.PASS_OUTPUT, normalInput.source)
        assertEquals("gbuffer", normalInput.sourcePassName)
        assertEquals(1, normalInput.sourcePassAttachment)
    }

    @Test
    fun `ping pong feedback reads the previous iteration output`() {
        val pipeline = CooPipelines.generic<Any>(id("behavior_ping_pong_feedback")) {
            val blur = pingPong("blur", iterations = 3, feedbackSampler = "Input") {
                fragment(id("post/blur.fsh"))
            }
            line(sceneColor(), blur.input("Input"))
            line(blur.color(), screenTarget())
        }

        val iterations = requireNotNull(CooPipelinePostEffectCompiler.compile(pipeline))
            .type.chain.passes

        assertEquals(PostEffectInputSource.SCENE_COLOR, iterations[0].inputs.single().source)
        iterations.drop(1).forEachIndexed { previousIndex, pass ->
            val feedback = pass.inputs.single()
            assertEquals(PostEffectInputSource.PASS_OUTPUT, feedback.source)
            assertEquals("blur_iteration_$previousIndex", feedback.sourcePassName)
            assertEquals(0, feedback.sourcePassAttachment)
        }
    }

    @Test
    fun `ping pong alternates targets and parameters`() {
        val pipeline = CooPipelines.generic<Any>(id("behavior_ping_pong_alternation")) {
            val blur = pingPong("blur", iterations = 4, feedbackSampler = "Input") {
                fragment(id("post/blur.fsh"))
                alternate("Axis", 1F, -1F)
            }
            line(sceneColor(), blur.input("Input"))
            line(blur.color(), screenTarget())
        }

        val compiled = requireNotNull(CooPipelinePostEffectCompiler.compile(pipeline))
        val instance = compiled.type.create(params = compiled.defaultParams)
        val iterations = compiled.type.chain.passes
        val targetKeys = iterations.map { it.outputTargetKey }
        val axes = iterations.map { pass ->
            pass.uniforms.single { it.name == "Axis" }.provider(instance)
        }

        assertEquals(2, targetKeys.toSet().size)
        assertNotEquals(targetKeys[0], targetKeys[1])
        assertEquals(targetKeys[0], targetKeys[2])
        assertEquals(targetKeys[1], targetKeys[3])
        assertEquals(
            listOf(
                PostEffectParamValue.FloatValue(1F),
                PostEffectParamValue.FloatValue(-1F),
                PostEffectParamValue.FloatValue(1F),
                PostEffectParamValue.FloatValue(-1F)
            ),
            axes
        )
    }

    @Test
    fun `downstream card reads the final ping pong iteration`() {
        val pipeline = CooPipelines.generic<Any>(id("behavior_ping_pong_downstream")) {
            val blur = pingPong("blur", iterations = 3, feedbackSampler = "Input") {
                fragment(id("post/blur.fsh"))
            }
            val composite = pass("composite") {
                fragment(id("post/composite.fsh"))
                input("Bloom")
            }
            line(sceneColor(), blur.input("Input"))
            line(blur.color(), composite.input("Bloom"))
            line(composite.color(), screenTarget())
        }

        val composite = requireNotNull(CooPipelinePostEffectCompiler.compile(pipeline))
            .type.chain.passes.single { it.name == "composite" }
        val bloomInput = composite.inputs.single()

        assertEquals(PostEffectInputSource.PASS_OUTPUT, bloomInput.source)
        assertEquals("blur_iteration_2", bloomInput.sourcePassName)
        assertEquals(0, bloomInput.sourcePassAttachment)
    }

    @Test
    fun `named framebuffer keeps its output and attachment contract`() {
        val target = id("framebuffer/behavior_gbuffer")
        val pipeline = CooPipelines.generic<Any>(id("behavior_named_framebuffer")) {
            val gbuffer = pass("gbuffer") {
                fragment(id("post/gbuffer.fsh"))
                colorAttachments(2)
            }
            line(gbuffer.color(1), framebufferTarget(target, 1))
        }

        val pass = requireNotNull(CooPipelinePostEffectCompiler.compile(pipeline))
            .type.chain.passes.single()

        assertEquals(target, pass.outputTargetId)
        assertEquals(2, pass.colorAttachmentCount)
    }

    @Test
    fun `iris safe backend keeps scene capabilities so external iris depth and color targets are routed through the resolver`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/backend/IrisSafeRenderBackend.kt"
        )

        // Iris 后端保留场景能力，目标解析器才能取得 Iris 管理的颜色与深度 attachment。
        assertTrue("RenderBackendCapability.SCENE_COLOR_COPY" in source)
        assertTrue("RenderBackendCapability.SCENE_DEPTH_READ" in source)
        assertTrue("RenderBackendCapability.SAFE_WORLD_COMPOSITE" in source)
        assertTrue("RenderBackendCapability.FINAL_FRAME_POST" in source)
    }

    @Test
    fun `pass output topological ordering uses dependents and dependencies maps`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/PostEffectFrameExecutor.kt"
        )

        // PASS_OUTPUT 连线必须参与拓扑排序，才能保证多输入节点在依赖项之后执行。
        assertTrue("private fun orderPasses(passes: List<PostEffectPass>): List<PostEffectPass>" in source)
        assertTrue("PostEffectInputSource.PASS_OUTPUT" in source)
        assertTrue("val dependents = linkedMapOf<String, MutableList<String>>()" in source)
        assertTrue("Post effect graph contains a cycle:" in source)
    }

    @Test
    fun `unresolved pass outputs and missing capabilities both produce skipped reasons`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/PostEffectFrameExecutor.kt"
        )

        // 缺少输入或能力时必须给出原因，不能静默丢弃 pass。
        assertTrue("missing required input(s):" in source)
        assertTrue("missing required capability(s):" in source)
        assertTrue("val missingCapabilities = pass.requiredCapabilities - context.backend.capabilities" in source)
    }

    @Test
    fun `open gl backend resolves pass outputs by name and bloom outputs by managed target key`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/OpenGlPostEffectExecutionBackend.kt"
        )

        // 显式 PASS_OUTPUT 与管理目标使用不同索引，两条解析路径都必须保留。
        assertTrue("lastPassOutputTextures" in source)
        assertTrue("state.lastOutputTextures[PostEffectOutput.BLOOM]" in source)
        assertTrue("step.output.targetKey" in source)
        assertTrue("step.output.scaleDivisor" in source)
        // 输出是否刷新 mip 链由 pass 契约决定，不能再通过 BLOOM 枚举猜测。
        assertTrue("step.output.generateMipmaps" in source)
        assertTrue("step.output.mipLevels" in source)
        assertTrue("validateTextureContract" in source)
        assertTrue("GL_TEXTURE_INTERNAL_FORMAT" in source)
        assertTrue("GL_TEXTURE_BASE_LEVEL" in source)
        assertTrue("GL_TEXTURE_MAX_LEVEL" in source)
        assertTrue("(0 until input.minimumMipLevels).all" in source)
        assertTrue("textureMipLevelCount()" in source)
        assertTrue("IrisCompat.currentTerrainDepthTexture()" in source)
        assertTrue("irisDepthReadFramebuffer" in source)
        assertTrue("glGetUniformLocation(program.program, mipLevelsUniform) >= 0" in source)
        assertTrue(
            source.indexOf("uploadUniforms(this, step.uniforms)") <
                source.indexOf("bindInputs(step, state, this)")
        )
        assertFalse("step.output.output == PostEffectOutput.BLOOM" in source)
        assertFalse("it.useMipmap()" in source)
    }

    @Test
    fun `final screen writes invalidate scene copy so later post instances read the previous output`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/OpenGlPostEffectExecutionBackend.kt"
        )

        // 写入最终屏幕后必须使场景副本失效，后续实例才能接着读取最新结果。
        assertTrue("private fun drawToFinal(step: PostEffectExecutionStep, state: InstanceFrameState)" in source)
        assertTrue("chainedSceneFramebufferId = framebuffer" in source)
        assertTrue("chainedSceneFramebufferId ?: context.sceneColorFramebufferId" in source)
        assertTrue("preparedSceneFrame = null" in source)
    }

    @Test
    fun `open gl backend evicts stale per instance targets to bound the FBO cache`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/OpenGlPostEffectExecutionBackend.kt"
        )

        // 缓存淘汰必须记录帧与最后使用时间，并实际释放过期 FBO。
        assertTrue("instanceLastSeenFrame" in source)
        assertTrue("frameCounter" in source)
        assertTrue("frameCounter - lastSeen > 60L" in source)
        assertTrue("private fun evictStaleTargets()" in source)
        assertTrue("it.buffer.release()" in source)

        // shader reload 后 release() 也必须清空淘汰状态。
        assertTrue("instanceLastSeenFrame.clear()" in source)
        assertTrue("frameCounter = 0" in source)
    }

    @Test
    fun `simple framebuffer always restores framebuffer and viewport state`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/glsl/SimpleFrameBuffer.kt"
        )
        val initScope = source.substringAfter("override fun init()")
            .substringBefore("override fun setTextureFilterMod")
        val writeScope = source.substringAfter("override fun writeFrameBufferWith")
            .substringBefore("override fun readFrameBufferWith")
        val readScope = source.substringAfter("override fun readFrameBufferWith")
            .substringBefore("override fun reset()")
        val resizeScope = source.substringAfter("override fun resize")
            .substringBefore("override fun copyDepthBuffer")
        val textureScope = source.substringAfter("private fun bindTextureTo")
            .substringBefore("private fun normalizeMagFilter")

        listOf(initScope, writeScope, readScope).forEach { scope ->
            assertTrue("GL_READ_FRAMEBUFFER_BINDING" in scope)
            assertTrue("GL_DRAW_FRAMEBUFFER_BINDING" in scope)
            assertTrue("try {" in scope)
            assertTrue("} finally {" in scope)
            assertTrue("glBindFramebuffer(GL_READ_FRAMEBUFFER, previousRead)" in scope)
            assertTrue("glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previousDraw)" in scope)
        }
        assertTrue("GL_READ_FRAMEBUFFER_BINDING" in resizeScope)
        assertTrue("GL_DRAW_FRAMEBUFFER_BINDING" in resizeScope)
        assertTrue("try {" in resizeScope)
        assertTrue("} finally {" in resizeScope)
        assertTrue("if (previousRead == resizedFramebuffer) fbo else previousRead" in resizeScope)
        assertTrue("if (previousDraw == resizedFramebuffer) fbo else previousDraw" in resizeScope)
        assertTrue("glViewport(previousViewport[0]" in writeScope)
        assertTrue("readScope()" in readScope)
        assertTrue("try {" in textureScope)
        assertTrue("fc.run()" in textureScope)
        assertTrue("} finally {" in textureScope)
    }

    @Test
    fun `client init swaps render backend based on iris shader pack usage`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/CooParticlesAPIClient.kt"
        )

        // shader pack 启用时必须切换到 Iris 安全后端，先保留 Iris 结果再执行自定义着色。
        assertTrue("fun checkIrisShaderPackUsed(): Boolean" in source)
        assertTrue("fun syncRenderBackend(): RenderBackend" in source)
        assertTrue("IrisSafeRenderBackend" in source)
        assertTrue("VanillaSafeRenderBackend" in source)
        assertTrue("ClientRenderPipelineManager.setActiveBackend(selectedRenderBackend)" in source)
    }

    private fun readProjectFile(relativePath: String): String {
        return Files.readString(projectFile(relativePath))
    }

    private fun projectFile(relativePath: String): Path {
        val repoRoot = findRepoRoot()
        return repoRoot.resolve(relativePath)
    }

    private fun findRepoRoot(): Path {
        var cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (cursor.parent != null) {
            if (Files.exists(cursor.resolve("settings.gradle"))) {
                return cursor
            }
            cursor = cursor.parent
        }
        error("Could not locate repository root from ${System.getProperty("user.dir")}")
    }

    private fun id(path: String): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, path)
    }
}
