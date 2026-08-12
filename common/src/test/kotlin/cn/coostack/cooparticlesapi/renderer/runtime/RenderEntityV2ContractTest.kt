package cn.coostack.cooparticlesapi.renderer.runtime

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RenderEntityV2ContractTest {
    @Test
    fun `render entity keeps sync-facing API surface`() {
        val source = readProjectFile("common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/RenderEntity.kt")

        assertTrue("abstract fun getRenderID()" in source)
        assertTrue("abstract fun getCodec()" in source)
        assertTrue("open fun serverTick()" in source)
        assertTrue("open fun clientTick()" in source)
        assertTrue("open fun shouldSync()" in source)
        assertTrue("open fun loadProfileFromEntity(another: RenderEntity)" in source)
        assertTrue("fun markDirty()" in source)
        assertTrue("fun requestSync()" in source)
        assertTrue("fun clearDirty()" in source)
        assertTrue("override fun spawn(world: Level, pos: Vec3)" in source)
    }

    @Test
    fun `render entity companion keeps codec helpers available`() {
        val source = readProjectFile("common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/RenderEntity.kt")

        assertTrue("fun decodeBase(buf: FriendlyByteBuf, instance: RenderEntity)" in source)
        assertTrue("fun encodeBase(buf: FriendlyByteBuf, entity: RenderEntity)" in source)
        assertTrue("fun <T : RenderEntity> createCodec(" in source)
        assertTrue("): StreamCodec<FriendlyByteBuf, RenderEntity>" in source)
    }

    @Test
    fun `runtime contract is pipeline based`() {
        val rendererPath = projectFile("common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/RenderEntityRenderer.kt")
        val inputPath = projectFile("common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/RenderInput.kt")

        assertTrue(rendererPath.exists(), "Expected RenderEntityRenderer.kt to exist")
        assertTrue(inputPath.exists(), "Expected RenderInput.kt to exist")
        listOf(
            "LegacyRenderEntityRenderer.kt",
            "LocalEffectChain.kt",
            "LocalEffectStep.kt",
            "LocalRenderInput.kt",
            "LocalRenderTargetPool.kt",
            "RenderContribution.kt",
            "RenderEntityVisualProfile.kt"
        ).forEach { fileName ->
            assertFalse(
                projectFile(
                    "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/$fileName"
                ).exists(),
                "$fileName must be removed"
            )
        }

        val rendererSource = Files.readString(rendererPath)
        val inputSource = Files.readString(inputPath)
        assertTrue("interface RenderEntityRenderer" in rendererSource)
        assertTrue("val pipeline: CooRenderPipeline<T>" in rendererSource)
        assertTrue("fun render(input: RenderInput<T>)" in rendererSource)
        assertTrue("class RenderInput" in inputSource)
        assertTrue("val entity: T" in inputSource)
        assertTrue("val tickDelta: Float" in inputSource)
        assertFalse("RenderEntityFeatureSet" in rendererSource)
        assertFalse("RenderContribution" in rendererSource)
        assertFalse("describeFeatures" in rendererSource)
        assertFalse("glowMaskConfig" in rendererSource)
        assertFalse("FramePostRenderEntityRenderer" in rendererSource)
        assertFalse("SharedModelMaskBloomRenderEntityRenderer" in rendererSource)
        assertFalse("DedicatedGlowMaskRenderEntityRenderer" in rendererSource)
    }

    @Test
    fun `runtime protects every renderer callback with glsl state scope`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/RenderEntityInstance.kt"
        )

        val renderCalls = Regex("renderer\\.render\\(").findAll(source).count()
        val stateScopes = Regex("CooGLSLStateManager\\.useState\\s*\\{").findAll(source).count()
        assertEquals(2, renderCalls, "Update this contract when adding another renderer callback")
        assertEquals(renderCalls, stateScopes, "Every renderer callback must have its own GLSL state scope")
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
