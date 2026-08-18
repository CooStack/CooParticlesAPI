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
        assertTrue("val shaderPackHandled: Boolean get() = false" in rendererApi)
        assertTrue("!renderer.shaderPackHandled" in instance)
        assertTrue("IrisCompat.runWithRenderEntityShader" in instance)
        assertTrue("irisWorldPassSubmitted" in instance)
        assertTrue("frameWorldModelMatrix" in instance)
        assertTrue("stack.set(frameWorldModelMatrix)" in instance)
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
        val pipelineManager = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/client/ClientRenderPipelineManager.kt"
        )
        val cooFxRuntime = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/client/CooFxClientRuntime.kt"
        )

        assertTrue("renderIrisWorldPass" in levelMixin)
        assertTrue("renderIrisWorldPass" in manager)
        assertTrue(".filter(RenderEntityInstance<RenderEntity>::isShaderPackHandled)" in manager)
        assertTrue("GameRenderer.getRendertypeEntityTranslucentShader()" in irisCompat)
        assertTrue("entityShader.apply()" in irisCompat)
        assertTrue("entityShader.clear()" in irisCompat)
        assertTrue("getDepthTextureNoTranslucents" in irisCompat)
        assertTrue("getDepthTextureId" in irisCompat)
        assertTrue("renderIrisCooFxWorldPass" in levelMixin)
        assertTrue(
            levelMixin.indexOf("renderIrisWorldPass") < levelMixin.indexOf("renderIrisCooFxWorldPass")
        )
        assertTrue("if (!CooParticlesAPIClient.checkIrisShaderPackUsed())" in pipelineManager)
        assertTrue("cooFxWorldPassDelegate?.invoke(context)" in pipelineManager)
        assertTrue("GameRenderer.getRendertypeEntitySolidShader()" in irisCompat)
        assertTrue("GameRenderer.getRendertypeEntityCutoutNoCullShader()" in irisCompat)
        assertTrue("IrisEntityShaderKind.SOLID" in cooFxRuntime)
        assertTrue("IrisEntityShaderKind.CUTOUT" in cooFxRuntime)
        assertTrue("withIrisDrawBufferStatePreserved" in cooFxRuntime)
        assertTrue("withPrimaryColorWriteOnly" in cooFxRuntime)
        val cooFxEntityPhase = cooFxRuntime
            .substringAfter("IrisCompat.runWithRenderEntityShader(")
            .substringBefore("} finally {")
        assertTrue("particleRenderer.drawExpandedIrisEntity" in cooFxEntityPhase)
        assertFalse("val emitsLight = false" in cooFxRuntime)
        assertFalse("renderIrisEmissive(program, batch, primitive)" in cooFxEntityPhase)
        assertTrue("emissive 第二次提交待 NEW_ENTITY 专用实现与状态测试后恢复" in cooFxRuntime)
         assertTrue("Iris emissive second pass deferred" in cooFxRuntime)
         assertFalse("particleRenderer.render(listOf(batch))" in cooFxEntityPhase)
        assertTrue("glCullFace(GL_BACK)" in cooFxRuntime)
        assertTrue("glFrontFace(GL_CCW)" in cooFxRuntime)
        assertTrue("glCullFace(previousCullFace)" in cooFxRuntime)
        assertTrue("glFrontFace(previousFrontFace)" in cooFxRuntime)
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
