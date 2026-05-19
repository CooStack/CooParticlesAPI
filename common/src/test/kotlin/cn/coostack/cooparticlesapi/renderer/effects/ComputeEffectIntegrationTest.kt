package cn.coostack.cooparticlesapi.renderer.effects

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class ComputeEffectIntegrationTest {
    @Test
    fun `compute dispatch is integrated as a builtin render effect type`() {
        val typesSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/effects/builtin/BuiltinRenderEffectTypes.kt"
        )
        val descriptorSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/effects/builtin/BuiltinRenderEffectDescriptors.kt"
        )
        val rendererSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/compute/ComputeDispatchRenderer.kt"
        )

        assertTrue("COMPUTE_DISPATCH" in typesSource)
        assertTrue("data class ComputeDispatchRenderRequest" in descriptorSource)
        assertTrue("fun computeDispatch(" in descriptorSource)
        assertTrue("object ComputeDispatchRenderer" in rendererSource)
        assertTrue("ComputeDispatchRenderRequest" in rendererSource)
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
