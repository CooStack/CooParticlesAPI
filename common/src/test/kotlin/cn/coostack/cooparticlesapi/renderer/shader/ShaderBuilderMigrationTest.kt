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

    @Test
    fun `legacy shader pipe implementation is removed`() {
        listOf(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/exceptions/RenderPipeInputException.kt",
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/exceptions/RenderPipeLinkerNotSetException.kt",
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/exceptions/RenderPipeNotFoundException.kt",
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/exceptions/RenderPipeOutputNotSetException.kt",
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/client/ShaderPipeManagers.kt",
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/api/pipe/GlobalUniform.kt",
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/api/pipe/PipeChannels.kt",
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/api/pipe/PipeLinkDsl.kt",
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/api/pipe/PipeLinker.kt",
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/api/pipe/PipeLinkerNode.kt",
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/api/pipe/ShaderPipe.kt",
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/api/pipe/handler/ShaderProgramUploader.kt",
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/pipe/Matrix4fGlobalUniform.kt",
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/pipe/manager/FramePipeChannels.kt",
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/pipe/manager/ShaderPipeManager.kt",
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/pipe/pipes/ExternalTextureShaderPipe.kt",
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/pipe/pipes/MCHookedShaderPipe.kt",
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/pipe/pipes/OutputDepthPipe.kt",
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/pipe/pipes/PingPongShaderPipe.kt",
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/pipe/pipes/SimpleShaderPipe.kt",
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/pipe/pipes/TextureShaderPipe.kt"
        ).forEach { relativePath ->
            assertFalse(projectFile(relativePath).toFile().exists(), relativePath)
        }
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
