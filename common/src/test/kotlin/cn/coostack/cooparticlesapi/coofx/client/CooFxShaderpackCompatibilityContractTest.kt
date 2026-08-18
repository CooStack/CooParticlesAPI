package cn.coostack.cooparticlesapi.coofx.client

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class CooFxShaderpackCompatibilityContractTest {
    @Test
    fun `iris warning is session deduplicated and compatibility boundary is documented`() {
        val runtime = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/client/CooFxClientRuntime.kt"
        )
        val architecture = readProjectFile("docs/coofx/architecture.md")
        val usage = readProjectFile("docs/coofx/blender-import-and-usage.md")

        assertTrue("private var irisShaderPackWarningEmitted = false" in runtime)
        assertTrue("warnAboutIrisShaderPackCompatibilityIfNeeded(irisShaderPackActive)" in runtime)
        assertTrue("if (!active || irisShaderPackWarningEmitted) return" in runtime)
        assertTrue("当前资产的着色结果可能与无光影不同" in runtime)
        assertTrue("CooFX 阴影暂不支持" in runtime)
        assertTrue("未通过目标光影验证的 shaderpack 视为 shader 部分不兼容" in runtime)
        assertTrue("entity color modulation、UV/overlay、PBR、雾、deferred/G-buffer、顶点位移和 emissive second pass" in runtime)
        assertTrue("不提供 CooFX 可依赖的 shaderpack identity 或能力协商 API" in architecture)
        assertTrue("没有提交 CooFX 专用 shadow-map pass" in architecture)
        assertTrue("不会把普通 CooFX shader 静默当作通用 fallback" in usage)
    }

    @Test
    fun `expanded entity submission limits iris writes to the primary attachment`() {
        val runtime = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/client/CooFxClientRuntime.kt"
        )
        val entitySubmission = runtime
            .substringAfter("IrisCompat.runWithRenderEntityShader(")
            .substringBefore("overlayTexture.teardownOverlayColor()")

        assertTrue("withPrimaryColorWriteOnly" in runtime)
        assertTrue("withPrimaryColorWriteOnly {" in entitySubmission)
        assertTrue("particleRenderer.drawExpandedIrisEntity(expandedEntityVertexCount)" in entitySubmission)
        assertTrue("finally" in runtime.substringAfter("private fun <T> withColorMasks"))
    }

    @Test
    fun `expanded entity bridge fences iris attributes and restores array state`() {
        val renderer = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/runtime/mesh/render/CooFxMeshParticleRenderer.kt"
        )

        assertTrue("disableExpandedIrisAttributes" in renderer)
        assertTrue("glDisableVertexAttribArray(6)" in renderer)
        assertTrue("glDisableVertexAttribArray(7)" in renderer)
        assertTrue("glDisableVertexAttribArray(8)" in renderer)
        assertTrue("glVertexAttribI3i(6, 0, 0, 0)" in renderer)
        assertTrue("glVertexAttrib2f(7, 0F, 0F)" in renderer)
        assertTrue("glVertexAttrib4f(8, 1F, 0F, 0F, 1F)" in renderer)
        assertTrue("glGetVertexAttribIiv" in renderer)
        assertTrue("glGetVertexAttribfv" in renderer)
        assertTrue("glVertexAttribI4iv" in renderer)
        assertTrue("glVertexAttrib4fv" in renderer)
        assertTrue("GL_VERTEX_ATTRIB_ARRAY_ENABLED" in renderer)
        assertTrue("GL_VERTEX_ATTRIB_ARRAY_DIVISOR" in renderer)
        assertTrue("CParticleCapabilities.setVertexAttribDivisor" in renderer)
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
