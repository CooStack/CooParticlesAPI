package cn.coostack.cooparticlesapi.test

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class TestControlKeyBindingsContractTest {
    @Test
    fun `gaming test navigation keys only advance on release clicks`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/TestControlKeyBindings.kt"
        )

        assertTrue("fun isReleasedSingleClick" in source)
        assertTrue("fun isReleasedDoubleClick" in source)
        assertTrue("event.isReleased(keyId) && event.isSingleClick(keyId)" in source)
        assertTrue("event.isReleased(keyId) && event.isDoubleClick(keyId)" in source)
        assertTrue("isReleasedSingleClick(event, TestControlKeyBindings.NEXT_KEY)" in source)
        assertTrue("isReleasedSingleClick(event, TestControlKeyBindings.PREVIOUS_KEY)" in source)
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
