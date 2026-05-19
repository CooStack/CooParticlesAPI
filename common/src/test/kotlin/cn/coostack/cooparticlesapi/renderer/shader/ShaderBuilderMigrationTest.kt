package cn.coostack.cooparticlesapi.renderer.shader

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShaderBuilderMigrationTest {
    @Test
    fun `legacy shader program builder delegates to advanced builder facade`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/ShaderProgramBuilder.kt"
        )

        assertTrue("private val delegate = AdvancedShaderProgramBuilder()" in source)
        assertTrue("fun buildCompute(): CooComputeShaderProgram" in source)
        assertTrue("fun bufferLayout(layout: ShaderBufferLayout<*>)" in source)
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
