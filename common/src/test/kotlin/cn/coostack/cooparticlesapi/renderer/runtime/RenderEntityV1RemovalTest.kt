package cn.coostack.cooparticlesapi.renderer.runtime

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RenderEntityV1RemovalTest {
    @Test
    fun `client render entity manager no longer routes v1 render passes`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/client/ClientRenderEntityManager.kt"
        )

        assertFalse("RenderEntityRenderPass" in source)
        assertFalse("bindEntityRenderPipe" in source)
        assertFalse("renderPass(" in source)
    }

    @Test
    fun `render docs no longer expose removed render apis`() {
        val docsDir = findRepoRoot().resolve("docs")
        if (!Files.isDirectory(docsDir)) {
            return
        }
        val removedApis = listOf(
            "bindEntityRenderPipe(",
            "ShaderPipe",
            "LegacyRenderEntityRenderer",
            "FramePostRenderEntityRenderer",
            "SharedModelMaskBloomRenderEntityRenderer",
            "DedicatedGlowMaskRenderEntityRenderer",
            "describeFeatures",
            "glowMaskConfig",
            "RenderEntityModelPipe",
            "terrainEffect",
            "CooTerrainEffectContext",
            "CooTerrainEffectFrame"
        )
        Files.walk(docsDir).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(".md") }
                .forEach { docPath ->
                    val text = Files.readString(docPath)
                    removedApis.forEach { removedApi ->
                        assertFalse(
                            removedApi in text,
                            "doc still references removed API $removedApi: $docPath"
                        )
                    }
                }
        }
    }

    @Test
    fun `render entity no longer exposes v1 render hooks`() {
        val source = readProjectFile("common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/RenderEntity.kt")

        assertFalse("abstract fun initialize(" in source)
        assertFalse("abstract fun render(" in source)
        assertFalse("abstract fun release(" in source)
        assertFalse("renderOnWorld(" in source)
        assertFalse("getRenderPass()" in source)
        assertFalse("getInputBlendMode()" in source)
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
