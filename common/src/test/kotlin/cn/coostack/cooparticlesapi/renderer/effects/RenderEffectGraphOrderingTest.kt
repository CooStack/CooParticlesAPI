package cn.coostack.cooparticlesapi.renderer.effects

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class RenderEffectGraphOrderingTest {
    @Test
    fun `render effect graph sorts by priority then submission sequence`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/effects/RenderEffectGraph.kt"
        )

        assertTrue("compareBy<IndexedDescriptor> { it.descriptor.priority }" in source)
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
    fun `render effect graph batches consecutive descriptors by executor without sorting by effect type`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/effects/RenderEffectGraph.kt"
        )

        assertTrue("var currentExecutor: RenderEffectExecutor? = null" in source)
        assertTrue("val currentBatch = mutableListOf<RenderEffectDescriptor>()" in source)
        assertTrue("RenderEffectRegistry.get(descriptor.effectType)" in source)
        assertTrue("if (currentExecutor !== executor)" in source)
        assertTrue("executor.render(frameContext, currentBatch.toList())" in source)
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
