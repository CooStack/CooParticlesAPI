package cn.coostack.cooparticlesapi.renderer.runtime

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class RenderEntityIrisPipelineContractTest {
    @Test
    fun `raw world pass renderers explicitly opt into iris`() {
        val rendererApi = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/RenderEntityRenderer.kt"
        )
        val instance = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/RenderEntityInstance.kt"
        )

        assertTrue("enum class IrisWorldPassMode" in rendererApi)
        assertTrue("interface IrisWorldPassRenderEntityRenderer" in rendererApi)
        assertTrue("fun irisWorldPassMode(entity: T): IrisWorldPassMode" in rendererApi)
        assertTrue("IrisCompat.runWithEntityShader(irisMode)" in instance)
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
        assertTrue("GameRenderer.getRendertypeEntitySolidShader()" in irisCompat)
        assertTrue("GameRenderer.getRendertypeEntityCutoutShader()" in irisCompat)
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
