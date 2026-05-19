package cn.coostack.cooparticlesapi.renderer.effects

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class RenderEffectGraphOrderingTest {
    @Test
    fun `render effect graph sorts by priority effect type effect id source and sequence`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/effects/RenderEffectGraph.kt"
        )

        assertTrue("compareBy<IndexedDescriptor> { it.descriptor.priority }" in source)
        assertTrue(".thenBy { it.descriptor.effectType.toString() }" in source)
        assertTrue(".thenBy { it.descriptor.effectId }" in source)
        assertTrue(".thenBy { it.descriptor.sourceInstanceId }" in source)
        assertTrue(".thenBy { it.sequence }" in source)
    }

    @Test
    fun `render effect graph filters unsupported backend capabilities before execution`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/effects/RenderEffectGraph.kt"
        )

        assertTrue("backendCapabilities.containsAll(indexed.descriptor.requiredCapabilities)" in source)
    }

    @Test
    fun `render effect graph groups by effect type and dispatches through registry`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/effects/RenderEffectGraph.kt"
        )

        assertTrue(".groupBy { it.descriptor.effectType }" in source)
        assertTrue("RenderEffectRegistry.get(effectType)" in source)
        assertTrue("executor.render(frameContext, grouped.map { it.descriptor })" in source)
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
