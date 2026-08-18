package cn.coostack.cooparticlesapi.cparticle

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 校验 CParticle 在 Iris 下按渲染层选择兼容路径。
 *
 * 示例：常规层由 Iris program 生成全部 gbuffer 输出，有界 Screen 层只写主颜色附件。
 * 禁止让 CParticle fragment shader 改写 shaderpack 的辅助附件。
 */
class CParticleShaderPackTranslucencyContractTest {
    /**
     * 校验常规实例先展开为 `DefaultVertexFormat.PARTICLE`，再交给 Iris 绘制。
     *
     * 示例：颜色按 RGBA8 打包，light 按两个 short 写入 `UV2`。
     * 有界 Screen 层例外：它需要在采样纹理和 mask 后完成 Alpha 预乘。
     */
    @Test
    fun `iris path draws gpu expanded vanilla particle vertices`() {
        val irisCompat = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/compat/IrisCompat.kt"
        )
        val renderer = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/render/CParticleRenderer.kt"
        )
        val layer = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/CParticleRenderLayer.kt"
        )
        val buffer = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/render/CParticleGlBuffer.kt"
        )
        val vertex = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/core/vertex/cparticle.vsh"
        )

        assertTrue("fun runWithParticleShader(" in irisCompat)
        assertTrue("IrisCompat.runWithParticleShader(" in renderer)
        assertTrue("expandForParticleShader(" in renderer)
        assertTrue("drawExpanded(" in renderer)
        assertTrue("DefaultVertexFormat.PARTICLE.setupBufferState()" in buffer)
        assertTrue("DefaultVertexFormat.PARTICLE.vertexSize.toLong()" in buffer)
        assertTrue("transformFeedbackVaryings(\"tfPosition\", \"tfUv\", \"tfPacked\")" in renderer)
        assertTrue("tfPacked = uvec2(" in vertex)
        assertTrue("layer.applyIndexedState(0)" in renderer)
        assertTrue("GL_ARB_draw_buffers_blend" in layer)
        assertTrue("glBlendFuncSeparateiARB" in layer)
        assertTrue("indexedBlendStateAvailable" in renderer)
        assertTrue("CParticleIndexedBlendState.setFactors(" in renderer)
        assertTrue("withPrimaryColorWriteOnly" in renderer)
        assertTrue("withAllColorWritesDisabled" in renderer)
        assertTrue("glColorMaski(drawBuffer, false, false, false, false)" in renderer)
        assertTrue("glDrawBuffer(" !in renderer)
        assertTrue("glDrawBuffers(" !in renderer)
    }

    /**
     * 校验普通半透明层使用原版 RGB Alpha Over 与独立 Alpha 输出因子。
     *
     * 示例：RGB 使用 `SRC_ALPHA / ONE_MINUS_SRC_ALPHA`，Alpha 使用 `ONE / ZERO`。
     * 禁止把 RGB 因子重复用于 Alpha 通道，这会改变 shaderpack 颜色附件的覆盖语义。
     */
    @Test
    fun `translucent layer keeps vanilla separate alpha blend factors`() {
        val layer = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/CParticleRenderLayer.kt"
        )

        assertTrue("val blendSrcAlpha: Int" in layer)
        assertTrue("val blendDstAlpha: Int" in layer)
        assertTrue(
            "TRANSLUCENT(1, true, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO, true)" in layer
        )
        assertTrue(
            "glBlendFuncSeparate(blendSrc, blendDst, blendSrcAlpha, blendDstAlpha)" in layer
        )
    }

    /**
     * 校验 GPU lightmap 坐标与 Minecraft 1.21.1 原版公式一致。
     *
     * 示例：亮度 `1` 使用 `(1 + 0.5) / 16` 采样第二个 texel 的中心。
     * 禁止使用 `light / 16`，该坐标位于相邻 texel 边界，和原版 `texelFetch` 不等价。
     */
    @Test
    fun `gpu lightmap uv matches vanilla particle shader`() {
        val vertex = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/core/vertex/cparticle.vsh"
        )

        assertTrue("(float(blockLight) * 16.0 + 8.0) / 256.0" in vertex)
        assertTrue("(float(skyLight) * 16.0 + 8.0) / 256.0" in vertex)
        assertTrue("uint(blockLight * 16) | (uint(skyLight * 16) << 16u)" in vertex)
    }

    /**
     * 读取仓库内一个 UTF-8 源文件。
     *
     * 示例：读取 CParticle shader 或渲染器源码。
     * 禁止使用绝对路径，避免把测试绑定到开发机目录。
     *
     * @param relativePath 仓库根目录下的相对路径
     * @return 完整文件内容
     */
    private fun readProjectFile(relativePath: String): String =
        Files.readString(findRepoRoot().resolve(relativePath))

    /**
     * 从 Gradle 测试工作目录向上定位仓库根目录。
     *
     * 示例：根工程与 `common` 子工程都通过 `settings.gradle` 定位。
     * 禁止在仓库外目录运行此测试。
     *
     * @return 最近的仓库根目录
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
