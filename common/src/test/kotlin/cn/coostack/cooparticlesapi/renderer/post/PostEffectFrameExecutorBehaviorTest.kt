package cn.coostack.cooparticlesapi.renderer.post

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 行为契约测试：覆盖 PostEffectFrameExecutor / OpenGl backend / Iris backend 在
 * 重构后引入的关键不变量。
 *
 * 现有 :common 测试 classpath 不包含 Minecraft jar（生产代码标记为 compileOnly），
 * 所以我们沿用仓库里其它 *ContractTest 的源码字符串匹配风格。每条断言都直接对应
 * 一个真实可能回归的现实风险点，注释说明为什么这串字符串值得保护。
 */
class PostEffectFrameExecutorBehaviorTest {

    @Test
    fun `bloom downsample and upsample helpers wire by previous pass name not by bright color slot`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/PostEffectFrameExecutor.kt"
        )

        // downsample/upsample helpers must take an explicit sourcePassName, otherwise the bloom
        // pyramid collapses back to the BRIGHT_COLOR slot and downsample_2 starts reading the
        // wrong texture.
        assertTrue("private fun bloomDownsamplePass(name: String, sourcePassName: String): PostEffectPass" in source)
        assertTrue("private fun bloomUpsamplePass(name: String, sourcePassName: String): PostEffectPass" in source)
        assertTrue("source = PostEffectInputSource.PASS_OUTPUT" in source)
        assertTrue("sourcePassName = sourcePassName" in source)
    }

    @Test
    fun `bloom blur passes rewrite bright color inputs into pass outputs of the previous pass`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/PostEffectFrameExecutor.kt"
        )

        // Each blur iteration must rewire BRIGHT_COLOR -> PASS_OUTPUT to a specific previous pass,
        // otherwise blur_horizontal_l1_i2 would re-read whatever last wrote to BLOOM target.
        assertTrue("private fun bloomSingleIterationBlurPass(" in source)
        assertTrue("if (input.source == PostEffectInputSource.BRIGHT_COLOR)" in source)
        assertTrue("source = PostEffectInputSource.PASS_OUTPUT" in source)
    }

    @Test
    fun `bloom expansion tracks per level final pass so downsample reads correct upstream`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/PostEffectFrameExecutor.kt"
        )

        // perLevelFinalPass is the cure for the original bug: downsample_(N+1) used to read the
        // BRIGHT_COLOR target, which (after upsample writes) pointed at the wrong mip.
        assertTrue("val perLevelFinalPass = mutableMapOf<Int, String>()" in source)
        assertTrue("perLevelFinalPass[level] = previousPassName" in source)
        assertTrue("var previousPassName" in source)
    }

    @Test
    fun `bloom upsample chain telescopes from deepest mip back up to full resolution`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/PostEffectFrameExecutor.kt"
        )

        // Deepest upsample seeds from the level's last blur; higher levels read the previous
        // upsample (one mip smaller). If this stops being conditional on `level == levels`, the
        // chain breaks.
        assertTrue("for (level in levels downTo 1)" in source)
        assertTrue("if (level == levels)" in source)
        assertTrue("perLevelFinalPass.getValue(level)" in source)
    }

    @Test
    fun `bloom composite reads the upsampled chain output explicitly so resolution does not depend on last bloom write`() {
        val executorSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/PostEffectFrameExecutor.kt"
        )
        val backendSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/OpenGlPostEffectExecutionBackend.kt"
        )

        // Composite must explicitly bind to the most recent upsample by name, not depend on
        // "whatever wrote to BLOOM last".
        assertTrue("withBrightSourcePass(composite, sourcePassName = previousPassName)" in executorSource)

        // Backend resolves PASS_OUTPUT by per-pass texture, so the explicit binding actually
        // takes effect end to end.
        assertTrue("input.producedByPassName?.let { state.lastPassOutputTextures[it] }" in backendSource)
    }

    @Test
    fun `iris safe backend keeps scene capabilities so external iris depth and color targets are routed through the resolver`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/backend/IrisSafeRenderBackend.kt"
        )

        // Iris compatibility strategy: ClientRenderTargetResolver probes the bound framebuffer
        // and exposes Iris-managed color / depth attachments through RenderFrameContext.
        // The capability advertisement here is what gates that resolver path on at frame build
        // time — removing any of these makes the "draw on top of Iris" pipeline fall back to
        // the wrong target.
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

        // orderPasses must do real topological sort over PASS_OUTPUT edges, otherwise A/C -> B
        // fan-in chains run out of order.
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

        // The two skip paths must remain in place so plans never silently drop a pass.
        assertTrue("missing required input(s):" in source)
        assertTrue("missing required capability(s):" in source)
        assertTrue("val missingCapabilities = pass.requiredCapabilities - context.backend.capabilities" in source)
    }

    @Test
    fun `open gl backend resolves pass outputs by name and bloom outputs by managed target key`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/OpenGlPostEffectExecutionBackend.kt"
        )

        // Two distinct lookups must coexist: lastPassOutputTextures (per-pass) for explicit
        // PASS_OUTPUT links, and lastOutputTextures[PostEffectOutput.BLOOM] for legacy
        // BRIGHT_COLOR semantics. Removing either one regresses bloom.
        assertTrue("lastPassOutputTextures" in source)
        assertTrue("state.lastOutputTextures[PostEffectOutput.BLOOM]" in source)
        assertTrue("step.output.targetKey" in source)
        assertTrue("step.output.scaleDivisor" in source)
        // Mipmap regeneration must be wired to bloom outputs so bloom_composite's textureLod
        // chain has real data on every level.
        assertTrue("step.output.output == PostEffectOutput.BLOOM" in source)
        assertTrue("it.useMipmap()" in source)
    }

    @Test
    fun `final screen writes invalidate scene copy so later post instances read the previous output`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/OpenGlPostEffectExecutionBackend.kt"
        )

        // After one post instance writes to FINAL_SCREEN, the next instance must not reuse the
        // old scene copy. It should copy the current final framebuffer and chain on top of that.
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

        // Eviction tracking must exist with a frame counter, last-seen map, grace constant and a
        // body that releases buffers — without these, long sessions accumulate orphaned FBOs.
        assertTrue("instanceLastSeenFrame" in source)
        assertTrue("frameCounter" in source)
        assertTrue("TARGET_EVICTION_GRACE_FRAMES" in source)
        assertTrue("private fun evictStaleTargets()" in source)
        assertTrue("it.buffer.release()" in source)

        // release() must reset the eviction state too, otherwise after a shader reload the cache
        // still thinks old instances are alive.
        assertTrue("instanceLastSeenFrame.clear()" in source)
        assertTrue("frameCounter = 0" in source)
    }

    @Test
    fun `client init swaps render backend based on iris shader pack usage`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/CooParticlesAPIClient.kt"
        )

        // The Iris compatibility strategy is "let Iris draw, then paint on top": this is enforced
        // by syncRenderBackend swapping in IrisSafeRenderBackend whenever a shader pack is in use.
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
}
