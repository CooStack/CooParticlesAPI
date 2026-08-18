package cn.coostack.cooparticlesapi.coofx.client

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CooFxFailureTransitionReporterTest {
    @Test
    fun `repeated failure emits one diagnostic until recovery`() {
        val diagnostics = mutableListOf<String>()
        val reporter = CooFxFailureTransitionReporter(
            emitFailure = { failure -> diagnostics += "failure:${failure.message}" },
            emitRecovery = { diagnostics += "recovered" },
        )

        reporter.onFailure(IllegalStateException("first"))
        reporter.onFailure(IllegalStateException("second"))
        reporter.onSuccess()
        reporter.onSuccess()
        reporter.onFailure(IllegalStateException("third"))

        assertEquals(listOf("failure:first", "recovered", "failure:third"), diagnostics)
    }

    @Test
    fun `scene updates and gpu uploads report only failure transitions`() {
        val registry = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/client/CooFxSceneClientRegistry.kt"
        )
        val runtime = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/client/CooFxClientRuntime.kt"
        )

        val client = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/client/CooFXClient.kt"
        )

        assertEquals(3, "onFailure\\(state\\.".toRegex().findAll(registry).count())
        assertEquals(3, "onSuccess \\{ state\\.".toRegex().findAll(registry).count())
        assertTrue("private val uploadFailures" in runtime)
        assertTrue("uploadFailure.onFailure(failure)" in runtime)
        assertTrue("uploadFailure.onSuccess()" in runtime)
        assertTrue("private var renderFailures = createRenderFailureReporter()" in client)
        assertTrue("runCatching {" in client.substringAfter("private fun renderWorldFrame"))
        assertTrue("renderFailures.onFailure(failure)" in client)
        assertTrue("renderFailures.onSuccess()" in client)
        assertTrue("CooFX world pass 渲染失败；将继续重试并抑制重复诊断" in client)
        assertTrue("CooFX world pass 渲染已恢复" in client)
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