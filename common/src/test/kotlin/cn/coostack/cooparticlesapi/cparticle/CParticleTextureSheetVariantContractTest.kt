package cn.coostack.cooparticlesapi.cparticle

import java.nio.file.Path as NioPath
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 校验 CPU 粒子与 CParticle 共用的 TextureSheet 变体契约。
 *
 * 示例：普通半透明无深度写入层必须映射到同名 GPU 层。
 * 禁止只在 [CParticleRenderLayer] 中增加名称而遗漏 CPU ParticleRenderType 注册。
 */
class CParticleTextureSheetVariantContractTest {
    /**
     * 校验预乘 Screen Blend 在多次叠加后趋近白色，同时保持颜色有界。
     *
     * 示例：偏蓝的发光粒子叠加 128 次后，三个颜色通道都接近 `1`。
     * 禁止改用 Alpha Over；该公式只会趋近原始蓝色。
     */
    @Test
    fun `bounded translucent addition converges toward white`() {
        val source = doubleArrayOf(0.2, 0.55, 1.0)
        val destination = doubleArrayOf(0.0, 0.0, 0.0)
        val alpha = 0.25

        repeat(128) {
            for (channel in destination.indices) {
                destination[channel] += source[channel] * alpha * (1.0 - destination[channel])
                assertTrue(destination[channel] in 0.0..1.0)
            }
        }

        destination.forEach { assertTrue(it > 0.99) }
    }

    @Test
    fun `bounded translucent addition clamps hdr source before blending`() {
        val source = doubleArrayOf(4.0, 2.0, 1.5)
        val destination = doubleArrayOf(0.95, 0.95, 0.95)
        val finalAlpha = 0.8 * 0.5 * 0.25

        for (channel in destination.indices) {
            val boundedSource = source[channel].coerceIn(0.0, 1.0) * finalAlpha
            destination[channel] += boundedSource * (1.0 - destination[channel])
            assertTrue(destination[channel] in 0.0..1.0)
        }
    }

    /**
     * 校验新增 GPU 层的 RGB 混合因子与深度写入组合。
     *
     * 示例：`ADDITION_BLEND_TRANSLUCENT_NOT_HDR` 使用预乘 Alpha 的有界 Screen Blend。
     * 禁止退化成普通 Alpha 混合，否则叠加结果只会趋近源颜色。
     */
    @Test
    fun `gpu layers expose all bounded and no depth variants`() {
        val translucentNoDepth = enumValueOf<CParticleRenderLayer>("PARTICLE_SHEET_TRANSLUCENT_NO_DEPTH_WRITE")
        assertEquals(770, translucentNoDepth.blendSrc)
        assertEquals(771, translucentNoDepth.blendDst)
        assertFalse(translucentNoDepth.depthWrite)

        val bounded = enumValueOf<CParticleRenderLayer>("ADDITION_BLEND_NOT_HDR")
        assertEquals(1, bounded.blendSrc)
        assertEquals(769, bounded.blendDst)
        assertTrue(bounded.depthWrite)

        val boundedNoDepth = enumValueOf<CParticleRenderLayer>("ADDITION_BLEND_NOT_HDR_NO_DEPTH_WRITE")
        assertEquals(1, boundedNoDepth.blendSrc)
        assertEquals(769, boundedNoDepth.blendDst)
        assertFalse(boundedNoDepth.depthWrite)

        val boundedTranslucent = enumValueOf<CParticleRenderLayer>("ADDITION_BLEND_TRANSLUCENT_NOT_HDR")
        assertEquals(1, boundedTranslucent.blendSrc)
        assertEquals(769, boundedTranslucent.blendDst)
        assertEquals(0, boundedTranslucent.blendSrcAlpha)
        assertEquals(1, boundedTranslucent.blendDstAlpha)
        assertTrue(boundedTranslucent.depthWrite)
        assertTrue(boundedTranslucent.premultiplyRgbByAlpha)

        val boundedTranslucentNoDepth =
            enumValueOf<CParticleRenderLayer>("ADDITION_BLEND_TRANSLUCENT_NOT_HDR_NO_DEPTH_WRITE")
        assertEquals(1, boundedTranslucentNoDepth.blendSrc)
        assertEquals(769, boundedTranslucentNoDepth.blendDst)
        assertEquals(0, boundedTranslucentNoDepth.blendSrcAlpha)
        assertEquals(1, boundedTranslucentNoDepth.blendDstAlpha)
        assertFalse(boundedTranslucentNoDepth.depthWrite)
        assertTrue(boundedTranslucentNoDepth.premultiplyRgbByAlpha)
        assertFalse(bounded.premultiplyRgbByAlpha)

        val renderer = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/render/CParticleRenderer.kt"
        )
        val fragment = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/core/fragment/cparticle.fsh"
        )
        val vertex = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/core/vertex/cparticle.vsh"
        )
        val cpuFragment = readProjectFile(
            "common/src/main/resources/assets/minecraft/shaders/core/coo_particle_screen.fsh"
        )
        val vanillaFragment = readProjectFile(
            "common/src/main/resources/assets/minecraft/shaders/core/particle.fsh"
        )
        val irisShaderKeyMixin = readProjectFile(
            "common/src/main/java/cn/coostack/cooparticlesapi/mixin/compat/iris/ShaderKeyIrisCompatMixin.java"
        )
        val reloadBus = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/ShaderReloadBus.kt"
        )
        assertTrue("CParticleRenderLayer.ADDITION_BLEND_TRANSLUCENT_NOT_HDR," in renderer)
        assertTrue("CParticleRenderLayer.ADDITION_BLEND_TRANSLUCENT_NOT_HDR_NO_DEPTH_WRITE," in renderer)
        assertTrue("shader.setInt(\"uPremultiplyRgbByAlpha\"" in renderer)
        assertTrue("uniform int uPremultiplyRgbByAlpha;" in fragment)
        assertTrue("color.a = clamp(color.a, 0.0, 1.0);" in fragment)
        assertTrue("color.rgb = clamp(color.rgb, 0.0, 1.0) * color.a;" in fragment)
        assertFalse("uPremultiplyRgbByAlpha" in vertex)
        assertTrue("if (system.layer.premultiplyRgbByAlpha) continue" in renderer)
        assertTrue("drawInstancedSystems(" in renderer)
        assertTrue("layerSystems," in renderer)
        assertTrue("uniformScratch," in renderer)
        assertTrue("withPrimaryColorWriteOnly" in renderer)
        assertTrue("RenderSystem.bindTexture(RenderSystem.getShaderTexture(2))" in renderer)
        assertTrue("CParticleSprites.bindLookup(2)" in renderer)
        assertTrue("CParticleAppearanceDescriptors.bindLookup(3)" in renderer)
        assertTrue("color.rgb = clamp(color.rgb, 0.0, 1.0) * color.a;" in cpuFragment)
        assertTrue("if (tex.a * vColor.a < 0.001)" in fragment)
        assertTrue("if (color.a < 0.001)" in cpuFragment)
        assertTrue("if (color.a < 0.001)" in vanillaFragment)
        assertTrue("cooparticlesapi\$PARTICLE_ALPHA_THRESHOLD = Math.nextDown(0.001F)" in irisShaderKeyMixin)
        assertFalse("0.1" in fragment)
        assertFalse("0.1" in cpuFragment)
        assertFalse("0.1" in vanillaFragment)
        assertTrue("CooParticleTextureSheet.reloadShader(resourceManager)" in reloadBus)
    }

    /**
     * 校验服务端枚举、CPU ParticleRenderType 与 GPU 名称映射同时包含新增变体。
     *
     * 示例：网络下发 `ADDITION_BLEND_NOT_HDR` 后两种粒子路径都能解析。
     * 禁止仅添加字符串枚举而让 CPU 路径回退到缺失贴图映射。
     */
    @Test
    fun `cpu sheets enum and gpu mapping expose the same variants`() {
        val sheetNames = listOf(
            "PARTICLE_SHEET_TRANSLUCENT_NO_DEPTH_WRITE",
            "ADDITION_BLEND_NOT_HDR",
            "ADDITION_BLEND_NOT_HDR_NO_DEPTH_WRITE",
            "ADDITION_BLEND_TRANSLUCENT_NOT_HDR",
            "ADDITION_BLEND_TRANSLUCENT_NOT_HDR_NO_DEPTH_WRITE",
        )
        val cpuSheets = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/particles/CooParticleTextureSheet.kt"
        )
        val serverEnum = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/supports/TextureSheetsEnum.kt"
        )
        val gpuLayers = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/CParticleRenderLayer.kt"
        )
        val renderer = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/render/CParticleRenderer.kt"
        )

        sheetNames.forEach { name ->
            assertTrue("val $name" in cpuSheets, "CPU TextureSheet 缺少 $name")
            assertTrue(name in serverEnum, "TextureSheetsEnum 缺少 $name")
            assertTrue("\"$name\" ->" in gpuLayers, "GPU 名称映射缺少 $name")
            assertTrue("CParticleRenderLayer.$name," in renderer, "GPU 绘制顺序缺少 $name")
        }
        assertTrue("SourceFactor.ONE" in cpuSheets)
        assertTrue("DestFactor.ONE_MINUS_SRC_COLOR" in cpuSheets)
        assertTrue("useBoundedTranslucentShader" in cpuSheets)
        assertTrue("\"coo_particle_screen\"" in cpuSheets)
        assertFalse("SourceFactor.SRC_ALPHA,\n                GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR" in cpuSheets)
    }

    /**
     * 读取仓库内一个 UTF-8 源文件。
     *
     * 示例：传入 `common/src/main` 下的相对路径读取渲染契约。
     * 禁止传入绝对路径，测试必须能在不同工作目录下运行。
     *
     * @param relativePath 仓库根目录下的相对路径
     * @return 完整文件内容
     */
    private fun readProjectFile(relativePath: String): String =
        findRepoRoot().resolve(relativePath).readText()

    /**
     * 从 Gradle 测试工作目录向上定位仓库根目录。
     *
     * 示例：从根工程或 `common` 子工程启动测试都能定位 `settings.gradle`。
     * 禁止在不属于本仓库的目录中调用。
     *
     * @return 最近的仓库根目录
     */
    private fun findRepoRoot(): NioPath {
        var cursor = Path(System.getProperty("user.dir")).absolute()
        while (cursor.parent != null) {
            if (cursor.resolve("settings.gradle").exists()) return cursor
            cursor = cursor.parent
        }
        error("Could not locate repository root from ${System.getProperty("user.dir")}")
    }
}
