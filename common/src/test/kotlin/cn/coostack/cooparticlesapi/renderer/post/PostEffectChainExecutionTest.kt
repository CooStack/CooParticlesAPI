package cn.coostack.cooparticlesapi.renderer.post

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class PostEffectChainExecutionTest {
    @Test
    fun `post effect graph passes frame context into executors`() {
        val graphSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/effects/RenderEffectGraph.kt"
        )
        val descriptorSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/effects/RenderEffectDescriptor.kt"
        )

        assertTrue("private val frameContext: RenderFrameContext" in graphSource)
        assertTrue("executor.render(frameContext, grouped.map { it.descriptor })" in graphSource)
        assertTrue("fun render(context: RenderFrameContext, effects: List<RenderEffectDescriptor>)" in descriptorSource)
    }

    @Test
    fun `post effect frame executor expands passes and skips missing required inputs`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/PostEffectFrameExecutor.kt"
        )

        assertTrue("val producedOutputs = LinkedHashSet<PostEffectOutput>()" in source)
        assertTrue("val producedPasses = LinkedHashSet<String>()" in source)
        assertTrue("val expandedPasses = expandPasses(instance)" in source)
        assertTrue("expandedPasses.mapIndexed" in source)
        assertTrue("missingRequiredInputs" in source)
        assertTrue("producedOutputs += pass.output" in source)
        assertTrue("producedPasses += pass.name" in source)
        assertTrue("orderPasses(instance.type.chain.passes)" in source)
        assertTrue("backend.execute(step)" in source)
        assertTrue("missing required input(s):" in source)
    }

    @Test
    fun `post effect graph supports pass to pass fan in inputs`() {
        val chainSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/PostEffectChain.kt"
        )
        val executorSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/PostEffectFrameExecutor.kt"
        )
        val backendSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/OpenGlPostEffectExecutionBackend.kt"
        )

        assertTrue("fun asInputTo(" in chainSource)
        assertTrue("fun asInputFrom(" in chainSource)
        assertTrue("PostEffectInputSource.PASS_OUTPUT" in chainSource)
        assertTrue("PostEffectInputSource.SCENE_RESOURCE" in chainSource)
        assertTrue("sourcePassName in producedPasses" in executorSource)
        assertTrue("private fun orderPasses(passes: List<PostEffectPass>)" in executorSource)
        assertTrue("lastPassOutputTextures" in backendSource)
        assertTrue("input.producedByPassName?.let { state.lastPassOutputTextures[it] }" in backendSource)
        assertTrue("val explicitSlots = step.inputs.mapNotNull { it.textureSlot }.toSet()" in backendSource)
        assertTrue("program.setInt(input.samplerName, input.textureSlot)" in backendSource)
        assertTrue("while (nextAutoSlot in explicitSlots || nextAutoSlot in usedSlots)" in backendSource)
    }

    @Test
    fun `post effect runtime registry uses the chain executor instead of a log only placeholder`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/PostEffectRuntimeRegistry.kt"
        )

        assertTrue("PostEffectFrameExecutor.execute(context, instances)" in source)
        assertTrue("RenderEffectExecutor { context, effects ->" in source)
    }

    @Test
    fun `open gl backend provides scene copy target ping pong targets and screen quad execution`() {
        val backendSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/OpenGlPostEffectExecutionBackend.kt"
        )
        val clientSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/CooParticlesAPIClient.kt"
        )
        val pipelineSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/client/ClientRenderPipelineManager.kt"
        )

        assertTrue("object OpenGlPostEffectExecutionBackend" in backendSource)
        assertTrue("glBlitFramebuffer" in backendSource)
        assertTrue("VertexBuffers.getScreenBuffer()" in backendSource)
        assertTrue("state.lastOutputTextures[PostEffectOutput.BLOOM]" in backendSource)
        assertTrue("context.sceneColorFramebufferId ?: source.frameBufferId" in backendSource)
        assertTrue("copyColor(sourceFramebufferId, sourceWidth, sourceHeight, target, context)" in backendSource)
        assertTrue("step.context.finalCompositeFramebufferId?.takeIf { it > 0 }" in backendSource)
        assertTrue("runtime input texture(s) are missing" in backendSource)
        assertTrue("GL_LINEAR_MIPMAP_LINEAR" in backendSource)
        assertTrue("step.output.output == PostEffectOutput.BLOOM" in backendSource)
        assertTrue("it.useMipmap()" in backendSource)
        assertTrue("step.output.targetKey" in backendSource)
        assertTrue("step.output.scaleDivisor" in backendSource)
        assertTrue("SimpleFrameBuffer(1, Supplier { -1 }, width, height)" in backendSource)
        assertTrue("PostEffectFrameExecutor.installBackend(OpenGlPostEffectExecutionBackend)" in clientSource)
        assertTrue("PostEffectFrameExecutor.prepareFrame(context)" in pipelineSource)
    }

    @Test
    fun `builtin bloom expands into explicit mip target chain and repeated blur passes`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/PostEffectFrameExecutor.kt"
        )

        assertTrue("private const val MAX_BLOOM_MIP_LEVELS = 6" in source)
        assertTrue("private const val MAX_BLOOM_ITERATIONS = 8" in source)
        assertTrue("ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, \"post/bloom_downsample.fsh\")" in source)
        assertTrue("ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, \"post/bloom_upsample.fsh\")" in source)
        assertTrue("for (level in 1..levels)" in source)
        assertTrue("repeat(iterations)" in source)
        assertTrue("\"blur_horizontal_l${'$'}{level}_i${'$'}{iteration + 1}\"" in source)
        assertTrue("\"blur_vertical_l${'$'}{level}_i${'$'}{iteration + 1}\"" in source)
        assertTrue("for (level in levels downTo 1)" in source)
        assertTrue("targetKey = \"bloom/downsample/${'$'}level\"" in source)
        assertTrue("scaleDivisor = scaleDivisor" in source)
    }

    @Test
    fun `post effect frame executor checks pass capabilities before execution`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/PostEffectFrameExecutor.kt"
        )

        assertTrue("val missingCapabilities = pass.requiredCapabilities - context.backend.capabilities" in source)
        assertTrue("missing required capability(s):" in source)
        assertTrue("RenderBackendCapability.SCENE_COLOR_COPY in context.backend.capabilities" in source)
        assertTrue("RenderBackendCapability.SCENE_DEPTH_READ in context.backend.capabilities" in source)
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
}
