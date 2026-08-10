package cn.coostack.cooparticlesapi.network.particle.emitters

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class ControlableParticleDataSizeTest {
    @Test
    fun `controlable particle data exposes serializable depth size`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/particle/emitters/ControlableParticleData.kt"
        )

        assertTrue("buf.writeFloat(data.depthSize)" in source)
        assertTrue("val depthSize = buf.readFloat()" in source)
        assertTrue("private var currentDepthSize = 0f" in source)
        assertTrue("var depthSize: Float" in source)
        assertTrue("this.depthSize = depthSize" in source)
        assertTrue("this.depthSize = data.depthSize" in source)
        assertTrue("this.previewDepthSize = data.depthSize" in source)
        assertTrue("return ControlableParticleData().also(::copyTo)" in source)
        assertTrue("target.depthSize = depthSize" in source)
    }

    @Test
    fun `controlable particle render state carries depth size`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/particles/ControlableParticle.kt"
        )

        assertTrue("private var currentDepthSize = 0f" in source)
        assertTrue("var previewDepthSize = currentDepthSize" in source)
        assertTrue("var depthSize: Float" in source)
        assertTrue("previewDepthSize = currentDepthSize" in source)
        assertTrue("getDepthSize(tickDelta)" in source)
        assertTrue("maxOf(getWeightSize(tickDelta), getHeightSize(tickDelta), getDepthSize(tickDelta))" in source)
        assertTrue("if (depthSize == 0f)" in source)
        assertTrue("addBoxFace(" in source)
        assertTrue("forward.x * vz * depthSize" in source)
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
