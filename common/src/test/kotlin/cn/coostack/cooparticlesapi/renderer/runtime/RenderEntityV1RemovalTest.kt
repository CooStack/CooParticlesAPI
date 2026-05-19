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
    fun `v2 docs no longer instruct users to bind render entities to global pipes`() {
        val renderEntityDoc = readProjectFile("docs/render-entity.md")
        val shaderPipeDoc = readProjectFile("docs/shader-pipe.md")

        assertFalse("bindEntityRenderPipe(" in renderEntityDoc)
        assertFalse("bindEntityRenderPipe(" in shaderPipeDoc)
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
