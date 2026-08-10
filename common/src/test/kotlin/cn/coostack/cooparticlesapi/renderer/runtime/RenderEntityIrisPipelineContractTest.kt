package cn.coostack.cooparticlesapi.renderer.runtime

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RenderEntityIrisPipelineContractTest {
    @Test
    fun `iris execution is internal to runtime`() {
        val rendererApi = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/RenderEntityRenderer.kt"
        )
        val instance = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/RenderEntityInstance.kt"
        )

        assertFalse("IrisWorldPassMode" in rendererApi)
        assertFalse("IrisWorldPassRenderEntityRenderer" in rendererApi)
        assertTrue("IrisCompat.runWithRenderEntityShader" in instance)
        assertTrue("irisWorldPassSubmitted" in instance)
    }

    @Test
    fun `opted in world pass is submitted before iris final composition`() {
        val levelMixin = readProjectFile(
            "common/src/main/java/cn/coostack/cooparticlesapi/mixin/LevelRendererMixin.java"
        )
        val manager = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/client/ClientRenderEntityManager.kt"
        )
        val irisCompat = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/compat/IrisCompat.kt"
        )

        assertTrue("renderIrisWorldPass" in levelMixin)
        assertTrue("renderIrisWorldPass" in manager)
        assertTrue("GameRenderer.getRendertypeEntityTranslucentShader()" in irisCompat)
        assertTrue("entityShader.apply()" in irisCompat)
        assertTrue("entityShader.clear()" in irisCompat)
    }

    private fun readProjectFile(relativePath: String): String {
        return Files.readString(findRepoRoot().resolve(relativePath))
    }

    private fun findRepoRoot(): Path {
        var cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (cursor.parent != null) {
            if (Files.exists(cursor.resolve("settings.gradle"))) return cursor
            cursor = cursor.parent
        }
        error("Could not locate repository root from ${System.getProperty("user.dir")}")
    }
}
