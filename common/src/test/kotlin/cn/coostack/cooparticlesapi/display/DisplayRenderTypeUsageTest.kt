package cn.coostack.cooparticlesapi.display

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class DisplayRenderTypeUsageTest {
    @Test
    fun `test shape display entity uses named layered render type before falling back to glow`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/options/display/TestShapeDisplayEntity.kt"
        )

        assertTrue("provider.layered(LAYERED_GLOW_ID)?.consumer(buffer)" in source)
        assertTrue("buffer.getBuffer(provider.glow())" in source)
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
