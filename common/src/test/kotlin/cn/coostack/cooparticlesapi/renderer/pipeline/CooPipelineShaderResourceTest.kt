package cn.coostack.cooparticlesapi.renderer.pipeline

import java.nio.file.Path
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.io.path.Path as pathOf
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 校验内置 BSL Bloom 的 shader 契约、边界处理和合成标定。 */
class CooPipelineShaderResourceTest {
    @Test
    fun `mask bloom shader symbols match pipeline ports and fluent parameters`() {
        val atlas = readProjectFile("assets/cooparticlesapi/shaders/post/bloom_bsl_atlas.fsh")
        val extract = readProjectFile("assets/cooparticlesapi/shaders/post/bloom_bright_extract.fsh")
        val composite = readProjectFile("assets/cooparticlesapi/shaders/post/mask_bloom_composite.fsh")

        assertTrue("uniform sampler2D BloomInput" in atlas)
        assertFalse("uniform float Sigma" in atlas)
        assertFalse("uniform float Range" in atlas)
        assertTrue("uniform int BloomLevels" in atlas)
        assertTrue("const float BSL_WEIGHT[6]" in atlas)
        assertTrue("textureGrad(BloomInput" in atlas)
        assertTrue("vec2 gradientX = dFdx(tileCoord)" in atlas)
        assertTrue("vec2 gradientY = dFdy(tileCoord)" in atlas)
        assertFalse("textureLod(BloomInput" in atlas)
        assertTrue("bloomTile(7.0" in atlas)
        assertTrue("float pixelHeight = 0.8 / min(720.0, viewHeight)" in atlas)
        assertTrue("vec2 bloomCoord = screen_uv * viewHeight * 0.8 / min(720.0, viewHeight)" in atlas)
        assertFalse(Regex("\\bSigma\\b").containsMatchIn(atlas))
        assertFalse(Regex("\\bRange\\b").containsMatchIn(atlas))
        assertFalse("kernelScale" in atlas)
        assertFalse("GAUSSIAN_WEIGHT" in atlas)
        assertTrue("const float BSL_HDR_SCALE = 32.0" in atlas)
        assertTrue("vec3 encodedBloom = pow(max(blur.rgb / BSL_HDR_SCALE" in atlas)
        assertTrue("FragColor = vec4(encodedBloom, 1.0)" in atlas)
        assertFalse("clamp(encodedBloom" in atlas)
        assertTrue("threshold <= 0.0" in extract)
        assertTrue("uniform float threshold" in extract)
        assertTrue("uniform float softKnee" in extract)
        assertTrue("uniform bool PremultipliedInput" in extract)
        assertTrue("uniform float Intensity" in extract)
        assertTrue("premultipliedColor * gain" in extract)
        assertTrue("? source.rgb" in extract)
        assertFalse("sqrt(min(source.a" in extract)
        assertFalse("clamp(premultipliedColor" in extract)
        assertTrue("uniform sampler2D SceneColor" in composite)
        assertFalse("uniform sampler2D BloomSource" in composite)
        assertFalse("uniform sampler2D BloomCore" in composite)
        assertTrue("uniform sampler2D BloomAtlas" in composite)
        assertTrue("uniform int MipLevels" in composite)
        assertTrue("sampleBloomTile(7.0" in composite)
        assertTrue("blur1 * 7.76" in composite)
        assertTrue("blur2 * 7.41" in composite)
        assertTrue("blur7) / 33.26" in composite)
        assertTrue("vec4 reconstructBloom()" in composite)
        assertTrue("float resolutionScale = 1.25 * min(720.0, atlasSize.y) / atlasSize.y" in composite)
        assertTrue("vec4 encodedBloom = texture(BloomAtlas, uv)" in composite)
        assertTrue("vec3 decodeBloom(vec3 encodedBloom)" in composite)
        assertTrue("return bloom * bloom * BSL_HDR_SCALE" in composite)
        assertTrue("return vec4(decodeBloom(encodedBloom.rgb), encodedBloom.a)" in composite)
        assertFalse("sampleHdrCoreBoost" in composite)
        assertTrue("vec3 hdrBloom = reconstructBloom().rgb" in composite)
        assertTrue("vec3 compositeHdrBloom(vec3 sceneColor, vec3 bloomColor)" in composite)
        assertTrue("const float BSL_BLOOM_MIX = 0.2" in composite)
        assertTrue("vec3 transmission = exp(-max(bloomColor, vec3(0.0)) * BSL_BLOOM_MIX)" in composite)
        assertTrue("return vec3(1.0) - (vec3(1.0) - scene) * transmission" in composite)
        assertTrue("vec3 result = compositeHdrBloom(sceneSample.rgb, hdrBloom)" in composite)
        assertTrue("vec3 ditherRgba8(vec3 color, float threshold)" in composite)
        assertTrue("return floor(scaled + threshold + 1.0e-4) / 255.0" in composite)
        assertTrue("FragColor = vec4(ditherRgba8(result, bayer8(gl_FragCoord.xy))" in composite)
        assertFalse("sampleBloomSourceOccupancy" in composite)
        assertFalse("sampleBloomSourceCoverage" in composite)
        assertFalse("bloomAlpha" in composite)
        assertFalse("outputAlpha" in composite)
        assertFalse("inverseDisplayCurve" in composite)
        assertFalse("mapHdrToDisplay" in composite)
        assertFalse("sampleSceneLuma" in composite)
        assertFalse("sceneHeadroom" in composite)
        assertFalse("channelHeadroom" in composite)
        assertFalse("skyFactor" in composite)
        assertFalse("mapFarHaloToDisplay" in composite)
        assertFalse("farPeak" in composite)
        assertFalse("0.04" in composite)
        assertFalse("float peak" in composite)
        assertFalse("totalWeight" in composite)
        assertFalse("uniform float Intensity" in composite)
        assertFalse("1.0 - (1.0 - scene) * (1.0 - bloom)" in composite)
        assertFalse("sceneSample.a +" in composite)
    }

    @Test
    fun `bloom rejects edge taps and preserves the scene alpha`() {
        val atlas = readProjectFile("assets/cooparticlesapi/shaders/post/bloom_bsl_atlas.fsh")
        val extract = readProjectFile("assets/cooparticlesapi/shaders/post/bloom_bright_extract.fsh")
        val maskComposite = readProjectFile("assets/cooparticlesapi/shaders/post/mask_bloom_composite.fsh")
        val modelExecutor = readWorkspaceFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/model/OpenGlRenderEntityModelExecutor.kt"
        )

        assertTrue("FragColor = vec4(premultipliedColor * gain, source.a)" in extract)
        assertTrue("premultipliedColor * contribution" in extract)
        assertTrue("bloom += sampleBloom(" in atlas)
        assertTrue("return vec4(0.0)" in atlas)
        assertTrue("FragColor = vec4(encodedBloom, 1.0)" in atlas)
        assertFalse("bayer8" in atlas)
        assertFalse("blur.rgb * blur.a" in atlas)
        assertTrue("sampleBloomTile(7.0" in maskComposite)
        assertTrue("vec4 encodedBloom = texture(BloomAtlas, uv)" in maskComposite)
        assertTrue("vec3 hdrBloom = reconstructBloom().rgb" in maskComposite)
        assertFalse("sampleHdrCoreBoost" in maskComposite)
        assertTrue("vec3 compositeHdrBloom(vec3 sceneColor, vec3 bloomColor)" in maskComposite)
        assertTrue("vec3 transmission = exp(-max(bloomColor, vec3(0.0)) * BSL_BLOOM_MIX)" in maskComposite)
        assertTrue("return vec3(1.0) - (vec3(1.0) - scene) * transmission" in maskComposite)
        assertTrue("vec3 result = compositeHdrBloom(sceneSample.rgb, hdrBloom)" in maskComposite)
        assertTrue("vec3 ditherRgba8(vec3 color, float threshold)" in maskComposite)
        assertTrue("FragColor = vec4(ditherRgba8(result, bayer8(gl_FragCoord.xy))" in maskComposite)
        assertFalse("inverseDisplayCurve" in maskComposite)
        assertFalse("mapHdrToDisplay" in maskComposite)
        assertFalse("mapFarHaloToDisplay" in maskComposite)
        assertFalse("sampleSceneLuma" in maskComposite)
        assertFalse("sceneHeadroom" in maskComposite)
        assertFalse("channelHeadroom" in maskComposite)
        assertFalse("skyFactor" in maskComposite)
        assertFalse("scene.rgb + bloom.rgb" in maskComposite)
        assertFalse("sceneSample.rgb + hdrBloom" in maskComposite)
        assertFalse("gain" in maskComposite)
        assertFalse("sceneAlpha" in maskComposite)
        assertFalse("outputPremultiplied" in maskComposite)
        assertFalse("bloomAlpha" in maskComposite)
        assertFalse("outputAlpha" in maskComposite)
        assertTrue(
            "glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)" in modelExecutor
        )
    }

    @Test
    fun `mask bloom composite preserves the scene and remains visible over bright backgrounds`() {
        fun composite(scene: Double, bloom: Double): Double {
            return 1.0 - (1.0 - scene.coerceIn(0.0, 1.0)) * exp(-bloom.coerceAtLeast(0.0) * 0.2)
        }

        val brightSky = doubleArrayOf(0.52, 0.67, 0.94)
        val coloredBloom = doubleArrayOf(0.5, 1.0, 0.79)
        val lumaWeights = doubleArrayOf(0.2126, 0.7152, 0.0722)
        val output = DoubleArray(3) { composite(brightSky[it], coloredBloom[it]) }
        val inputLuma = brightSky.indices.sumOf { brightSky[it] * lumaWeights[it] }
        val outputLuma = output.indices.sumOf { output[it] * lumaWeights[it] }

        brightSky.forEach { assertEquals(it, composite(it, 0.0), 1.0e-6) }
        assertTrue(outputLuma - inputLuma > 0.05)
        output.indices.forEach { assertTrue(output[it] > brightSky[it]) }
        assertTrue(composite(0.94, 8.0) < 1.0)
    }

    @Test
    fun `bsl atlas write and read scales remain reciprocal`() {
        listOf(360.0, 720.0, 1080.0, 2160.0).forEach { height ->
            val atlasScale = height * 0.8 / minOf(720.0, height)
            val reconstructionScale = 1.25 * minOf(720.0, height) / height
            assertEquals(1.0, atlasScale * reconstructionScale, 1.0e-12)
        }
    }

    @Test
    fun `rgba8 dither remains stable when bloom batches composite repeatedly`() {
        fun quantize(color: Double, threshold: Double): Double {
            val scaled = color.coerceIn(0.0, 1.0) * 255.0
            return floor(scaled + threshold + 1.0e-4) / 255.0
        }

        listOf(0.0, 0.031, 0.42, 0.875, 1.0).forEach { color ->
            listOf(0.0, 0.25, 0.5, 0.984375).forEach { threshold ->
                val once = quantize(color, threshold)
                assertEquals(once, quantize(once, threshold), 1.0e-12)
            }
        }
    }

    @Test
    fun `hdr companding keeps high brightness reversible without exposing linear mip shoulders`() {
        fun encode(value: Double): Double = (value.coerceAtLeast(0.0) / 32.0).pow(0.25)
        fun decode(value: Double): Double = value.pow(4.0) * 32.0

        val encodedHighBrightness = encode(96.0)
        assertTrue(encodedHighBrightness > 1.0)
        assertEquals(96.0, decode(encodedHighBrightness), 1.0e-9)

        val linearMidpoint = 96.0 * 0.5
        val compandedMidpoint = decode((encode(0.0) + encodedHighBrightness) * 0.5)
        assertTrue(compandedMidpoint < linearMidpoint * 0.25)
    }

    @Test
    fun `mask bloom laser demo exposes one visible color draw path`() {
        val vertex = readProjectFile(
            "assets/cooparticlesapi/shaders/core/vertex/mask_bloom_straight_laser.vsh"
        )
        val fragment = readProjectFile(
            "assets/cooparticlesapi/shaders/core/fragment/mask_bloom_straight_laser.fsh"
        )
        val renderer = readWorkspaceFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/options/renderer/world/" +
                "DemoMaskBloomStraightLaserRenderEntityRenderer.kt"
        )
        val options = readWorkspaceFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/options/renderer/world/" +
                "DemoWorldRenderEffectOptions.kt"
        )

        assertTrue("uniform float coneEndRatio" in vertex)
        assertTrue("layout(location = 0) out vec4 FragColor" in fragment)
        assertTrue("layout(location = 1) out vec4 MaskColor" in fragment)
        assertEquals(2, Regex("MaskColor = FragColor;").findAll(fragment).count())
        assertFalse("maskAlpha" in fragment)
        assertFalse("maskBrightness" in fragment)
        assertTrue("CooPipelines.MASK_BLOOM" in renderer)
        assertTrue("override val shaderPackHandled = false" in renderer)
        assertTrue("entity.brightness * 19.2F" in renderer)
        assertFalse("RenderPhase" in renderer)
        assertFalse("maskAlpha" in renderer)
        assertFalse("maskBrightness" in renderer)
        assertEquals(3, Regex("(?m)^\\s+drawPass\\($").findAll(renderer).count())
        assertTrue("mask_bloom_straight_laser.vsh" in renderer)
        assertTrue("mask_bloom_straight_laser.fsh" in renderer)
        listOf("target", "size", "color", "lifetime", "bright").forEach { parameter ->
            assertTrue("\"$parameter\"" in options)
        }
    }

    private fun readProjectFile(resourcePath: String): String {
        return projectRoot().resolve("common/src/main/resources").resolve(resourcePath).readText()
    }

    private fun readWorkspaceFile(path: String): String {
        return projectRoot().resolve(path).readText()
    }

    private fun projectRoot(): Path {
        var cursor = pathOf(System.getProperty("user.dir")).toAbsolutePath()
        while (cursor.parent != null) {
            if (cursor.resolve("settings.gradle").exists()) return cursor
            cursor = cursor.parent
        }
        error("Could not locate repository root from ${System.getProperty("user.dir")}")
    }
}
