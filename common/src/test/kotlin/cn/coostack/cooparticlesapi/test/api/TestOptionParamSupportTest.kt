package cn.coostack.cooparticlesapi.test.api

import cn.coostack.cooparticlesapi.animation.Animate
import cn.coostack.cooparticlesapi.display.DisplayEntity
import cn.coostack.cooparticlesapi.network.particle.composition.ParticleComposition
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import cn.coostack.cooparticlesapi.test.ServerSoundFadeTestOption
import cn.coostack.cooparticlesapi.test.ServerSoundStartDuckLoopTestOption
import cn.coostack.cooparticlesapi.test.ServerSoundStartLoopTestOption
import cn.coostack.cooparticlesapi.test.ServerSoundTestOption
import cn.coostack.cooparticlesapi.test.ShakeOption
import cn.coostack.cooparticlesapi.test.SimpleAnimateOption
import cn.coostack.cooparticlesapi.test.SimpleCompositionOption
import cn.coostack.cooparticlesapi.test.SimpleDisplayEntityOption
import cn.coostack.cooparticlesapi.test.SimpleEmitterOption
import cn.coostack.cooparticlesapi.test.SimpleEventHandlerOption
import cn.coostack.cooparticlesapi.test.SimpleStyleOption
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
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
            .substringAfter("fun <T : Any, P : Any> applyParam(")
            .substringBefore("fun <T : Any> applyTo(")
        val applyToBody = source
            .substringAfter("fun <T : Any> applyTo(")
            .substringBefore("fun <T : Any> applyOptionParams(")
        val applyOptionParamsBody = source
            .substringAfter("fun <T : Any> applyOptionParams(")
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
        val option = object : TestOption<Unit> {
            override fun paramTarget() = Unit
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

    @Test
    fun `applyTo receives the option target type`() {
        val target = StringBuilder()
        val option = object : TestOption<StringBuilder> {
            override fun paramTarget(): StringBuilder = target
            override fun start() = Unit
            override fun stop() = Unit
            override fun isValid(): Boolean = true
            override fun onFailed() = Unit
            override fun onSuccess() = Unit
            override fun optionID(): String = "custom-id"
            override fun doTick() = Unit
        }
            .applyParam(IntTestOptionValue("count"), 1)
            .applyTo {
                it.append(getParamOrThrow<Int>("count"))
            }

        option.applyOptionParams(mapOf("count" to "7"))

        assertEquals("7", target.toString())
        assertEquals("custom-id", option.optionID())
    }

    @Test
    fun `multiple appliers share one target lookup`() {
        val targets = ArrayList<StringBuilder>()
        val option = object : TestOption<StringBuilder> {
            override fun paramTarget(): StringBuilder = StringBuilder().also(targets::add)
            override fun start() = Unit
            override fun stop() = Unit
            override fun isValid(): Boolean = true
            override fun onFailed() = Unit
            override fun onSuccess() = Unit
            override fun optionID(): String = "shared-target"
            override fun doTick() = Unit
        }
            .applyTo { it.append("first") }
            .applyTo { it.append("-second") }

        option.applyOptionParams(emptyMap())

        assertEquals(1, targets.size)
        assertEquals("first-second", targets.single().toString())
    }

    @Test
    @Suppress("DEPRECATION")
    fun `adding optional ids keeps the previous jvm constructors`() {
        assertConstructor(SimpleAnimateOption::class.java, Animate::class.java, Int::class.javaPrimitiveType!!)
        assertConstructor(SimpleEmitterOption::class.java, ParticleEmitters::class.java, Int::class.javaPrimitiveType!!)
        assertConstructor(SimpleDisplayEntityOption::class.java, DisplayEntity::class.java, Int::class.javaPrimitiveType!!)
        assertConstructor(SimpleCompositionOption::class.java, ParticleComposition::class.java, Int::class.javaPrimitiveType!!)
        assertConstructor(
            SimpleStyleOption::class.java,
            ParticleGroupStyle::class.java,
            Level::class.java,
            Vec3::class.java,
            Int::class.javaPrimitiveType!!
        )
        assertConstructor(SimpleEventHandlerOption::class.java, Player::class.java, Int::class.javaPrimitiveType!!)
        assertConstructor(ShakeOption::class.java, Int::class.javaPrimitiveType!!, Player::class.java)
        assertConstructor(ServerSoundTestOption::class.java, Player::class.java, ServerSoundTestOption.Mode::class.java)
        assertConstructor(ServerSoundStartLoopTestOption::class.java, Player::class.java)
        assertConstructor(ServerSoundStartDuckLoopTestOption::class.java, Player::class.java)
        assertConstructor(ServerSoundFadeTestOption::class.java, Player::class.java)
    }

    private fun assertConstructor(type: Class<*>, vararg parameterTypes: Class<*>) {
        assertTrue(
            type.declaredConstructors.any { constructor ->
                constructor.parameterTypes.contentEquals(parameterTypes)
            },
            "${type.name} 缺少构造器 ${parameterTypes.joinToString { it.simpleName }}"
        )
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
