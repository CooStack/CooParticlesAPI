package cn.coostack.cooparticlesapi.test.api

import org.joml.Vector3f
import org.joml.Vector4f
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TestOptionParamSupportTest {
    @BeforeTest
    fun bootstrapMinecraftRegistries() {
        val sharedConstants = Class.forName("net.minecraft.SharedConstants")
        sharedConstants.getMethod("tryDetectVersion").invoke(null)
        val bootstrap = Class.forName("net.minecraft.server.Bootstrap")
        bootstrap.getMethod("bootStrap").invoke(null)
    }

    @Test
    fun `param appliers run only after option param values are applied`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/api/TestOptionParam.kt"
        )
        val applyParamBody = source
            .substringAfter("fun <T : Any> applyParam(")
            .substringBefore("fun applyTo(")
        val applyToBody = source
            .substringAfter("fun applyTo(")
            .substringBefore("fun applyOptionParams(")
        val applyOptionParamsBody = source
            .substringAfter("fun applyOptionParams(")
            .substringBefore("fun optionParamSpecs(")

        assertFalse("runAppliers(option)" in applyParamBody)
        assertFalse("runAppliers(option)" in applyToBody)
        assertTrue("runAppliers(option)" in applyOptionParamsBody)
    }

    @Test
    fun `vec3 based param editors are pickable positions by default`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/api/TestOptionParam.kt"
        )
        val vector3EditorSignature = source
            .substringAfter("private fun vector3Editor(")
            .substringBefore("): TestOptionParamEditor")

        assertTrue("pickable: Boolean = true" in vector3EditorSignature)
        assertTrue("allowAbsolute: Boolean = true" in vector3EditorSignature)
    }

    @Test
    fun `vector3f position editor metadata is encoded for controller gui`() {
        val specs = listOf(
            TestOptionParamSpec(
                Vector3fTestOptionValue("vector3f_pos", "Vector3f位置")
                    .asPosition(defaultMode = TestOptionParamPositionMode.ABSOLUTE),
                Vector3f()
            )
        )
        val decoded = TestOptionParamCodec.decodeOptionSpecs(TestOptionParamCodec.encodeOptionSpecs(specs))

        assertEquals(1, decoded.size)
        decoded.forEach { spec ->
            assertEquals(3, spec.componentCount)
            assertTrue(spec.pickable, "${spec.id} should expose the pick button")
            assertTrue(spec.allowAbsolute, "${spec.id} should expose the absolute/relative toggle")
        }
        assertEquals(TestOptionParamPositionMode.ABSOLUTE.id, decoded[0].defaultPositionMode)
    }

    @Test
    fun `controller gui uses split component inputs for vector params`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/block/client/TestControllerScreen.kt"
        )

        assertTrue("paramComponentBoxes" in source)
        assertTrue("usesComponentBoxes(spec)" in source)
        assertTrue(".joinToString(\",\")" in source)
    }

    @Test
    fun `controller gui opens color picker from a swatch and normalizes hex input`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/block/client/TestControllerScreen.kt"
        )

        assertTrue("colorSwatchAt" in source)
        assertTrue("openColorPicker" in source)
        assertTrue("renderColorPicker" in source)
        assertTrue("colorHexBox" in source)
        assertTrue("normalizeTestOptionColorHexInput" in source)
        assertFalse("renderColorPalette" in source)
    }

    @Test
    fun `color picker renders above parameter controls and exposes rgb inputs`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/block/client/TestControllerScreen.kt"
        )
        val renderBody = source
            .substringAfter("private fun renderColorPicker(")
            .substringBefore("private fun handleColorPickerClick(")
        val firstFlush = renderBody.indexOf("graphics.flush()")
        val pushPose = renderBody.indexOf("graphics.pose().pushPose()")
        val translate = renderBody.indexOf("graphics.pose().translate(0f, 0f, COLOR_PICKER_Z)")
        val secondFlush = renderBody.indexOf("graphics.flush()", firstFlush + 1)
        val popPose = renderBody.indexOf("graphics.pose().popPose()")

        assertTrue(firstFlush >= 0 && firstFlush < pushPose)
        assertTrue(pushPose >= 0 && pushPose < translate)
        assertTrue(translate >= 0 && translate < secondFlush)
        assertTrue(secondFlush >= 0 && secondFlush < popPose)
        assertTrue("colorRgbBoxes" in source)
        assertTrue("onColorRgbChanged" in source)
        assertTrue("moveColorInputFocus" in source)
    }

    @Test
    fun `vector color accepts bare and prefixed hex values`() {
        val type = Vector3fTestOptionValue("color").asColor()

        listOf("FFFFFF", "#FFFFFF", "0xFFFFFF").forEach { text ->
            assertEquals(Vector3f(1f, 1f, 1f), type.parse(text), text)
        }
    }

    @Test
    fun `pasted color hex prefixes are removed and format is uppercase`() {
        listOf("FFFFFF", "#ffffff", "0xFfFfFf").forEach { text ->
            assertEquals("FFFFFF", normalizeTestOptionColorHexInput(text), text)
        }
    }

    @Test
    fun `invalid color text is not filtered into a different hex value`() {
        assertEquals("GGFFFFFF", normalizeTestOptionColorHexInput("ggffffff"))
    }

    @Test
    fun `vector4 color accepts six or eight hex digits`() {
        val type = Vector4fTestOptionValue("color").asColor()

        assertEquals(Vector4f(1f, 1f, 1f, 1f), type.parse("#FFFFFF"))
        assertEquals(Vector4f(1f, 0f, 0f, 128f / 255f), type.parse("0xFF000080"))
    }

    @Test
    fun `rgb component inputs use the zero to 255 range`() {
        assertEquals(
            listOf(0f, 128f / 255f, 1f),
            parseTestOptionRgbInputs(listOf("0", "128", "255"))
        )
        assertEquals(listOf("0", "128", "255"), formatTestOptionRgbInputs(listOf(0f, 128f / 255f, 1f)))
        assertEquals(null, parseTestOptionRgbInputs(listOf("", "128", "255")))
        assertEquals(null, parseTestOptionRgbInputs(listOf("-1", "128", "255")))
        assertEquals(null, parseTestOptionRgbInputs(listOf("256", "128", "255")))
    }

    @Test
    fun `applying a map of option values remains chainable`() {
        val option = object : TestOption {
            override fun start() = Unit
            override fun stop() = Unit
            override fun isValid(): Boolean = true
            override fun onFailed() = Unit
            override fun onSuccess() = Unit
            override fun optionID(): String = "chainable"
            override fun doTick() = Unit
        }.applyParam(IntTestOptionValue("count"), 1)

        assertSame(option, option.applyOptionParams(mapOf("count" to "2")))
        assertEquals(2, option.getParam<Int>("count"))
    }

    private fun readProjectFile(relativePath: String): String {
        return Files.readString(projectFile(relativePath))
    }

    private fun projectFile(relativePath: String): Path {
        return findRepoRoot().resolve(relativePath)
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
