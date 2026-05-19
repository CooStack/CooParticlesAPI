package cn.coostack.cooparticlesapi.renderer.runtime

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
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
    fun `v2 runtime contract types exist`() {
        val rendererPath = projectFile("common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/RenderEntityRenderer.kt")
        val profilePath = projectFile("common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/RenderEntityVisualProfile.kt")

        assertTrue(rendererPath.exists(), "Expected RenderEntityRenderer.kt to exist")
        assertTrue(profilePath.exists(), "Expected RenderEntityVisualProfile.kt to exist")

        val rendererSource = Files.readString(rendererPath)
        assertTrue("interface RenderEntityRenderer" in rendererSource)
        assertTrue("fun describeFeatures" in rendererSource)
        assertTrue("fun createVisualProfile" in rendererSource)
        assertTrue("fun initialize" in rendererSource)
        assertTrue("interface WorldPassRenderEntityRenderer" in rendererSource)
        assertTrue("interface FramePostRenderEntityRenderer" in rendererSource)
        assertTrue("interface RenderEntityUpdateHook" in rendererSource)
        assertTrue("interface RenderEntityReleaseHook" in rendererSource)

        val profileSource = Files.readString(profilePath)
        assertTrue("data class RenderEntityVisualProfile" in profileSource)
        assertTrue("compositeMode" in profileSource)
        assertTrue("needsSceneColorCopy" in profileSource)
        assertTrue("needsSceneDepth" in profileSource)
        assertTrue("localChainEnabled" in profileSource)
        assertTrue("frameEffectsEnabled" in profileSource)
        assertTrue("renderPriority" in profileSource)
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
