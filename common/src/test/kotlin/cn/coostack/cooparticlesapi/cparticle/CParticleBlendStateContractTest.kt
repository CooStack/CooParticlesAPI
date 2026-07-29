package cn.coostack.cooparticlesapi.cparticle

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 校验 GPU 粒子层完整管理 OpenGL 混合状态。
 *
 * 示例：加法层在绘制前始终选择 `GL_FUNC_ADD`。
 * 禁止渲染器把自己的混合方程遗留到外层粒子 pass。
 */
class CParticleBlendStateContractTest {
    /**
     * 校验仅有写深度的累加层在颜色绘制后回放深度。
     *
     * 示例：有界 Screen Blend 先完成颜色叠加，再单独写入深度。
     * 禁止普通 Alpha Over 层进入深度回放路径。
     */
    @Test
    fun `only depth-writing additive layers replay depth after colors accumulate`() {
        assertTrue(CParticleRenderLayer.ADDITION_BLEND.requiresDeferredDepthWrite)
        assertTrue(CParticleRenderLayer.ADDITION_BLEND_TRANSLUCENT.requiresDeferredDepthWrite)
        assertTrue(CParticleRenderLayer.ADDITION_BLEND_NOT_HDR.requiresDeferredDepthWrite)
        assertFalse(CParticleRenderLayer.ADDITION_BLEND_NOT_HDR_NO_DEPTH_WRITE.requiresDeferredDepthWrite)
        assertFalse(CParticleRenderLayer.ADDITION_BLEND_TRANSLUCENT_NO_DEPTH_WRITE.requiresDeferredDepthWrite)
        assertFalse(CParticleRenderLayer.ADDITION_BLEND_TRANSLUCENT_NO_DEPTH_WRITE.depthWrite)
        assertFalse(CParticleRenderLayer.TRANSLUCENT.requiresDeferredDepthWrite)

        val renderer = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/render/CParticleRenderer.kt"
        )
        val fragment = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/core/fragment/cparticle.fsh"
        )
        val colorPass = renderer.substringAfter("for (layer in drawLayers)")
            .substringBefore("if (deferredDepthSystems.isNotEmpty())")
        val depthReplay = renderer.substringAfter("if (deferredDepthSystems.isNotEmpty())")
            .substringBefore("} finally {")

        assertTrue("if (layer.requiresDeferredDepthWrite)" in colorPass)
        assertTrue(colorPass.indexOf("glDepthMask(false)") < colorPass.indexOf("system.glBuffer.draw"))
        assertTrue("deferredDepthSystems.addAll(layerSystems)" in colorPass)
        assertTrue("shader.setInt(\"uDepthOnly\", 1)" in depthReplay)
        assertTrue("glColorMaski(0, false, false, false, false)" in depthReplay)
        assertTrue("glDepthMask(true)" in depthReplay)
        assertTrue(
            depthReplay.indexOf("glColorMaski(0, false, false, false, false)") <
                depthReplay.indexOf("system.glBuffer.draw")
        )
        assertTrue("uniform int uDepthOnly;" in fragment)
        assertTrue(fragment.indexOf("if (uDepthOnly != 0)") < fragment.indexOf("texture(uLightmap"))
    }

    /**
     * 校验每个 `ADDITION_BLEND_*` 层使用的状态边界。
     *
     * 示例：GPU 绘制结束后恢复调用前的非默认混合方程。
     * 禁止只设置 RGB 因子，因为 shaderpack 还依赖独立的 Alpha 因子。
     */
    @Test
    fun `additive layers select add equation and renderer restores previous equations`() {
        val layer = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/CParticleRenderLayer.kt"
        )
        val renderer = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/render/CParticleRenderer.kt"
        )
        val applyState = layer.substringAfter("fun applyState()")
            .substringBefore("companion object")
        val stateCapture = renderer.substringAfter("// ---- 状态快照 ----")
            .substringBefore("try {")
        val drawLayers = renderer.substringAfter("private val drawLayers")
            .substringBefore("private fun ensureProgram")
        val drawLoop = renderer.substringAfter("for (layer in drawLayers)")
            .substringBefore("} finally {")
        val stateRestore = renderer.substringAfter("} finally {")
            .substringBefore("fun release()")
        val equationIndex = applyState.indexOf("glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD)")
        val factorsIndex = applyState.indexOf(
            "glBlendFuncSeparate(blendSrc, blendDst, blendSrcAlpha, blendDstAlpha)"
        )

        assertTrue(equationIndex >= 0)
        assertTrue(factorsIndex > equationIndex)
        assertTrue("glGetInteger(GL_BLEND_EQUATION_RGB)" in stateCapture)
        assertTrue("glGetInteger(GL_BLEND_EQUATION_ALPHA)" in stateCapture)
        assertTrue("glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha)" in stateRestore)
        assertTrue("layer.applyState()" in drawLoop)
        assertTrue(drawLoop.indexOf("layer.applyState()") < drawLoop.indexOf("system.glBuffer.draw"))
        listOf(
            "CParticleRenderLayer.ADDITION_BLEND,",
            "CParticleRenderLayer.ADDITION_BLEND_TRANSLUCENT,",
            "CParticleRenderLayer.ADDITION_BLEND_TRANSLUCENT_NO_DEPTH_WRITE,",
            "CParticleRenderLayer.ADDITION_BLEND_NOT_HDR,",
            "CParticleRenderLayer.ADDITION_BLEND_NOT_HDR_NO_DEPTH_WRITE,",
            "CParticleRenderLayer.ADDITION_BLEND_TRANSLUCENT_NOT_HDR,",
            "CParticleRenderLayer.ADDITION_BLEND_TRANSLUCENT_NOT_HDR_NO_DEPTH_WRITE,",
            "CParticleRenderLayer.PARTICLE_SHEET_TRANSLUCENT_NO_DEPTH_WRITE,",
        ).forEach { assertTrue(it in drawLayers) }
    }

    /**
     * 读取仓库内一个 UTF-8 文件，用于源码级渲染契约。
     *
     * 示例：传入 `common/src` 下的相对路径。
     * 禁止使用绝对路径，测试必须与工作区位置无关。
     *
     * @param relativePath 仓库根目录下的相对路径
     * @return 完整文件内容
     */
    private fun readProjectFile(relativePath: String): String =
        Files.readString(findRepoRoot().resolve(relativePath))

    /**
     * 从测试工作目录向上定位 Gradle 仓库根目录。
     *
     * 示例：测试从根工程或 `common` 子工程启动时都能定位。
     * 禁止在不包含 `settings.gradle` 的目录树中调用。
     *
     * @return 最近的包含 `settings.gradle` 的祖先目录
     */
    private fun findRepoRoot(): Path {
        var cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (cursor.parent != null) {
            if (Files.exists(cursor.resolve("settings.gradle"))) return cursor
            cursor = cursor.parent
        }
        error("Could not locate repository root from ${System.getProperty("user.dir")}")
    }
}
