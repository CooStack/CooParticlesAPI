package cn.coostack.cooparticlesapi.test.api

import cn.coostack.cooparticlesapi.annotations.codec.CodecHelper
import cn.coostack.cooparticlesapi.supports.TextureSheetsEnum
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap

enum class TestOptionParamEditorKind {
    TEXT,
    VECTOR,
    ENUM
}

enum class TestOptionParamPositionMode(val id: String) {
    RELATIVE("relative"),
    ABSOLUTE("absolute");

    companion object {
        fun fromId(id: String?): TestOptionParamPositionMode {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: RELATIVE
        }
    }
}

data class TestOptionParamEditor(
    val kind: TestOptionParamEditorKind = TestOptionParamEditorKind.TEXT,
    val componentCount: Int = 1,
    val componentLabels: List<String> = emptyList(),
    val pickable: Boolean = false,
    val allowAbsolute: Boolean = false,
    val defaultPositionMode: TestOptionParamPositionMode = TestOptionParamPositionMode.RELATIVE,
    val color: Boolean = false,
    val suggestions: List<String> = emptyList()
) {
    companion object {
        fun text(): TestOptionParamEditor = TestOptionParamEditor()

        fun vector(
            componentCount: Int,
            componentLabels: List<String>,
            pickable: Boolean = false,
            allowAbsolute: Boolean = false,
            defaultPositionMode: TestOptionParamPositionMode = TestOptionParamPositionMode.RELATIVE,
            color: Boolean = false
        ): TestOptionParamEditor {
            return TestOptionParamEditor(
                kind = TestOptionParamEditorKind.VECTOR,
                componentCount = componentCount,
                componentLabels = componentLabels,
                pickable = pickable,
                allowAbsolute = allowAbsolute,
                defaultPositionMode = defaultPositionMode,
                color = color
            )
        }

        fun enum(suggestions: List<String>): TestOptionParamEditor {
            return TestOptionParamEditor(
                kind = TestOptionParamEditorKind.ENUM,
                suggestions = suggestions.distinct()
            )
        }
    }
}

open class TestOptionParamType<T : Any>(
    val id: String,
    val valueClass: Class<T>,
    val displayName: String = id,
    private val parser: (String) -> T?,
    private val formatter: (T) -> String = { it.toString() },
    val codecClass: Class<*> = valueClass,
    val editor: TestOptionParamEditor = TestOptionParamEditor.text()
) {
    init {
        require(id.isNotBlank()) { "TestOption param id 不能为空" }
    }

    fun parse(text: String): T? = parser(text.trim())

    fun format(value: T): String = formatter(value)
}

open class CodecTestOptionValue<T : Any>(
    id: String,
    valueClass: Class<T>,
    displayName: String = id,
    parser: (String) -> T?,
    formatter: (T) -> String = { it.toString() },
    codecClass: Class<*> = valueClass,
    editor: TestOptionParamEditor = TestOptionParamEditor.text()
) : TestOptionParamType<T>(id, valueClass, displayName, parser, formatter, codecClass, editor)

class BooleanTestOptionValue(id: String, displayName: String = id) : CodecTestOptionValue<Boolean>(
    id,
    Boolean::class.javaObjectType,
    displayName,
    parser = { raw ->
        when (raw.lowercase()) {
            "true", "1", "yes", "y", "on", "open", "开", "开启" -> true
            "false", "0", "no", "n", "off", "close", "关", "关闭" -> false
            else -> null
        }
    },
    formatter = { it.toString() }
)

class IntTestOptionValue(id: String, displayName: String = id) : CodecTestOptionValue<Int>(
    id,
    Int::class.javaObjectType,
    displayName,
    parser = { it.toIntOrNull() }
)

class LongTestOptionValue(id: String, displayName: String = id) : CodecTestOptionValue<Long>(
    id,
    Long::class.javaObjectType,
    displayName,
    parser = { it.toLongOrNull() }
)

class FloatTestOptionValue(id: String, displayName: String = id) : CodecTestOptionValue<Float>(
    id,
    Float::class.javaObjectType,
    displayName,
    parser = { it.toFloatOrNull() }
)

class DoubleTestOptionValue(id: String, displayName: String = id) : CodecTestOptionValue<Double>(
    id,
    Double::class.javaObjectType,
    displayName,
    parser = { it.toDoubleOrNull() }
)

class StringTestOptionValue(id: String, displayName: String = id) : CodecTestOptionValue<String>(
    id,
    String::class.java,
    displayName,
    parser = { it }
)

class Vec2TestOptionValue(
    id: String,
    displayName: String = id,
    editor: TestOptionParamEditor = TestOptionParamEditor.vector(2, listOf("x", "y"))
) : CodecTestOptionValue<Vec2>(
    id,
    Vec2::class.java,
    displayName,
    parser = { raw ->
        parseFloatComponents(raw, 2)?.let { Vec2(it[0], it[1]) }
    },
    formatter = { formatComponents(it.x, it.y) },
    editor = editor
)

class Vec3TestOptionValue(
    id: String,
    displayName: String = id,
    editor: TestOptionParamEditor = vector3Editor()
) : CodecTestOptionValue<Vec3>(
    id,
    Vec3::class.java,
    displayName,
    parser = { raw ->
        parseDoubleComponents(raw, 3)?.let { Vec3(it[0], it[1], it[2]) }
    },
    formatter = { formatComponents(it.x, it.y, it.z) },
    editor = editor
) {
    fun asPosition(
        allowAbsolute: Boolean = true,
        defaultMode: TestOptionParamPositionMode = TestOptionParamPositionMode.RELATIVE
    ): Vec3TestOptionValue = Vec3TestOptionValue(id, displayName, vector3Editor(pickable = true, allowAbsolute, defaultMode))
}

class RelativeLocationTestOptionValue(
    id: String,
    displayName: String = id,
    editor: TestOptionParamEditor = vector3Editor()
) : CodecTestOptionValue<RelativeLocation>(
    id,
    RelativeLocation::class.java,
    displayName,
    parser = { raw ->
        parseDoubleComponents(raw, 3)?.let { RelativeLocation(it[0], it[1], it[2]) }
    },
    formatter = { formatComponents(it.x, it.y, it.z) },
    editor = editor
) {
    fun asPosition(
        allowAbsolute: Boolean = true,
        defaultMode: TestOptionParamPositionMode = TestOptionParamPositionMode.RELATIVE
    ): RelativeLocationTestOptionValue = RelativeLocationTestOptionValue(id, displayName, vector3Editor(pickable = true, allowAbsolute, defaultMode))
}

class Vector3fTestOptionValue(
    id: String,
    displayName: String = id,
    editor: TestOptionParamEditor = vector3Editor()
) : CodecTestOptionValue<Vector3f>(
    id,
    Vector3f::class.java,
    displayName,
    parser = { raw ->
        parseFloatComponents(raw, 3)?.let { Vector3f(it[0], it[1], it[2]) }
    },
    formatter = { formatComponents(it.x, it.y, it.z) },
    editor = editor
) {
    fun asPosition(
        allowAbsolute: Boolean = true,
        defaultMode: TestOptionParamPositionMode = TestOptionParamPositionMode.RELATIVE
    ): Vector3fTestOptionValue = Vector3fTestOptionValue(id, displayName, vector3Editor(pickable = true, allowAbsolute, defaultMode))

    fun asColor(): Vector3fTestOptionValue {
        return Vector3fTestOptionValue(
            id,
            displayName,
            TestOptionParamEditor.vector(3, listOf("r", "g", "b"), color = true)
        )
    }
}

class Vector4fTestOptionValue(
    id: String,
    displayName: String = id,
    editor: TestOptionParamEditor = TestOptionParamEditor.vector(4, listOf("x", "y", "z", "w"))
) : CodecTestOptionValue<Vector4f>(
    id,
    Vector4f::class.java,
    displayName,
    parser = { raw ->
        parseFloatComponents(raw, 4)?.let { Vector4f(it[0], it[1], it[2], it[3]) }
    },
    formatter = { formatComponents(it.x, it.y, it.z, it.w) },
    editor = editor
) {
    fun asColor(): Vector4fTestOptionValue {
        return Vector4fTestOptionValue(
            id,
            displayName,
            TestOptionParamEditor.vector(4, listOf("r", "g", "b", "a"), color = true)
        )
    }
}

class QuaternionfTestOptionValue(
    id: String,
    displayName: String = id
) : CodecTestOptionValue<Quaternionf>(
    id,
    Quaternionf::class.java,
    displayName,
    parser = { raw ->
        parseFloatComponents(raw, 4)?.let { Quaternionf(it[0], it[1], it[2], it[3]) }
    },
    formatter = { formatComponents(it.x, it.y, it.z, it.w) },
    editor = TestOptionParamEditor.vector(4, listOf("x", "y", "z", "w"))
)

open class EnumTestOptionValue<T : Enum<T>>(
    id: String,
    enumClass: Class<T>,
    displayName: String = id
) : TestOptionParamType<T>(
    id,
    enumClass,
    displayName,
    parser = { raw ->
        enumClass.enumConstants.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
    },
    formatter = { it.name },
    codecClass = String::class.java,
    editor = TestOptionParamEditor.enum(enumClass.enumConstants.map { it.name })
)

class TextureSheetsEnumTestOptionValue(id: String, displayName: String = id) : EnumTestOptionValue<TextureSheetsEnum>(
    id,
    TextureSheetsEnum::class.java,
    displayName
)

typealias TextureSheetsEnumValue = TextureSheetsEnumTestOptionValue
typealias TextureSheetEnumTestOptionValue = TextureSheetsEnumTestOptionValue
typealias TextureSheetEnumValue = TextureSheetsEnumTestOptionValue

inline fun <reified T : Enum<T>> enumTestOptionValue(id: String, displayName: String = id): EnumTestOptionValue<T> {
    return EnumTestOptionValue(id, T::class.java, displayName)
}

inline fun <reified T : Enum<T>> enumValue(id: String, displayName: String = id): EnumTestOptionValue<T> {
    return enumTestOptionValue(id, displayName)
}

typealias CodecValue<T> = CodecTestOptionValue<T>
typealias BooleanValue = BooleanTestOptionValue
typealias IntValue = IntTestOptionValue
typealias LongValue = LongTestOptionValue
typealias FloatValue = FloatTestOptionValue
typealias DoubleValue = DoubleTestOptionValue
typealias StringValue = StringTestOptionValue
typealias Vec2Value = Vec2TestOptionValue
typealias Vec3Value = Vec3TestOptionValue
typealias RelativeLocationValue = RelativeLocationTestOptionValue
typealias Vector3fValue = Vector3fTestOptionValue
typealias Vector4fValue = Vector4fTestOptionValue
typealias QuaternionfValue = QuaternionfTestOptionValue
typealias EnumValue<T> = EnumTestOptionValue<T>

data class TestOptionParamSpec<T : Any>(
    val type: TestOptionParamType<T>,
    val defaultValue: T
) {
    val id: String get() = type.id
    val displayName: String get() = type.displayName
    val valueClass: Class<T> get() = type.valueClass
    val codecClass: Class<*> get() = type.codecClass
    val editor: TestOptionParamEditor get() = type.editor

    fun defaultText(): String = type.format(defaultValue)

    @Suppress("UNCHECKED_CAST")
    fun textOf(value: Any?): String {
        return if (value != null && type.valueClass.isInstance(value)) {
            type.format(value as T)
        } else {
            defaultText()
        }
    }

    fun parseText(text: String): T? = type.parse(text)
}

object TestOptionParamSupport {
    private val specs = Collections.synchronizedMap(WeakHashMap<TestOption, LinkedHashMap<String, TestOptionParamSpec<*>>>())
    private val values = Collections.synchronizedMap(WeakHashMap<TestOption, LinkedHashMap<String, Any>>())
    private val appliers = Collections.synchronizedMap(WeakHashMap<TestOption, MutableList<TestOption.(Any) -> Unit>>())

    fun <T : Any> applyParam(
        option: TestOption,
        type: TestOptionParamType<T>,
        defaultValue: T
    ): TestOption {
        require(type.valueClass.isInstance(defaultValue)) {
            "参数 ${type.id} 默认值类型 ${defaultValue::class.java.name} 与声明类型 ${type.valueClass.name} 不匹配"
        }
        require(isCodecRegistered(type.codecClass)) {
            "参数 ${type.id} Codec 类型 ${type.codecClass.name} 未注册到 CodecHelper"
        }

        specsFor(option)[type.id] = TestOptionParamSpec(type, defaultValue)
        valuesFor(option)[type.id] = defaultValue
        return option
    }

    fun applyTo(option: TestOption, action: TestOption.(Any) -> Unit): TestOption {
        appliers.getOrPut(option) { ArrayList() }.add(action)
        return option
    }

    fun applyOptionParams(option: TestOption, encodedValues: Map<String, String>): TestOption {
        val optionValues = valuesFor(option)
        optionParamSpecs(option).forEach { spec ->
            optionValues[spec.id] = spec.defaultValue
            val raw = encodedValues[spec.id] ?: return@forEach
            spec.parseText(raw)?.let { parsed ->
                optionValues[spec.id] = parsed
            }
        }
        runAppliers(option)
        return option
    }

    fun optionParamSpecs(option: TestOption): List<TestOptionParamSpec<*>> {
        return specs[option]?.values?.toList() ?: emptyList()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getParam(option: TestOption, id: String): T? {
        return values[option]?.get(id) as? T
    }

    fun optionParamValues(option: TestOption): Map<String, String> {
        val optionValues = values[option]
        return optionParamSpecs(option).associate { spec ->
            spec.id to spec.textOf(optionValues?.get(spec.id))
        }
    }

    private fun specsFor(option: TestOption): LinkedHashMap<String, TestOptionParamSpec<*>> {
        return specs.getOrPut(option) { linkedMapOf() }
    }

    private fun valuesFor(option: TestOption): LinkedHashMap<String, Any> {
        return values.getOrPut(option) { linkedMapOf() }
    }

    private fun runAppliers(option: TestOption) {
        val target = option.paramTarget()
        appliers[option]?.forEach { action -> option.action(target) }
    }

    private fun isCodecRegistered(type: Class<*>): Boolean {
        if (CodecHelper.isSupposedType(type)) {
            return true
        }
        val primitive = primitiveByWrapper[type] ?: return false
        return CodecHelper.isSupposedType(primitive)
    }

    private val primitiveByWrapper: Map<Class<*>, Class<*>> = mapOf(
        java.lang.Boolean::class.java to Boolean::class.java,
        java.lang.Byte::class.java to Byte::class.java,
        java.lang.Character::class.java to Char::class.java,
        java.lang.Double::class.java to Double::class.java,
        java.lang.Float::class.java to Float::class.java,
        java.lang.Integer::class.java to Int::class.java,
        java.lang.Long::class.java to Long::class.java,
        java.lang.Short::class.java to Short::class.java
    )
}

private fun vector3Editor(
    pickable: Boolean = true,
    allowAbsolute: Boolean = true,
    defaultMode: TestOptionParamPositionMode = TestOptionParamPositionMode.RELATIVE
): TestOptionParamEditor {
    return TestOptionParamEditor.vector(
        componentCount = 3,
        componentLabels = listOf("x", "y", "z"),
        pickable = pickable,
        allowAbsolute = allowAbsolute,
        defaultPositionMode = defaultMode
    )
}

private fun parseDoubleComponents(raw: String, count: Int): List<Double>? {
    return parseComponents(raw, count)?.map { it.toDoubleOrNull() ?: return null }
}

private fun parseFloatComponents(raw: String, count: Int): List<Float>? {
    parseHexColor(raw, count)?.let { return it }
    return parseComponents(raw, count)?.map { it.toFloatOrNull() ?: return null }
}

private fun parseComponents(raw: String, count: Int): List<String>? {
    val trimmed = stripPositionMode(raw)
        .replace("(", " ")
        .replace(")", " ")
        .replace("[", " ")
        .replace("]", " ")
        .replace(";", " ")
        .replace(",", " ")
        .trim()
    if (trimmed.isBlank()) return null
    val parts = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
    return parts.takeIf { it.size == count }
}

private fun stripPositionMode(raw: String): String {
    val trimmed = raw.trim()
    val colon = trimmed.indexOf(':')
    if (colon <= 0) return trimmed
    val prefix = trimmed.substring(0, colon).lowercase(Locale.ROOT)
    return when (prefix) {
        TestOptionParamPositionMode.RELATIVE.id,
        TestOptionParamPositionMode.ABSOLUTE.id,
        "rel",
        "abs" -> trimmed.substring(colon + 1)
        else -> trimmed
    }
}

private fun parseHexColor(raw: String, count: Int): List<Float>? {
    if (count !in 3..4) return null
    val text = stripPositionMode(raw).trim()
    val hex = when {
        text.startsWith("#") -> text.substring(1)
        text.startsWith("0x", ignoreCase = true) -> text.substring(2)
        else -> text
    }
    if (hex.length != 6 && hex.length != 8) return null
    val value = hex.toLongOrNull(16) ?: return null
    val r: Int
    val g: Int
    val b: Int
    val a: Int
    if (hex.length == 6) {
        r = ((value shr 16) and 0xFF).toInt()
        g = ((value shr 8) and 0xFF).toInt()
        b = (value and 0xFF).toInt()
        a = 255
    } else {
        r = ((value shr 24) and 0xFF).toInt()
        g = ((value shr 16) and 0xFF).toInt()
        b = ((value shr 8) and 0xFF).toInt()
        a = (value and 0xFF).toInt()
    }
    val components = listOf(r, g, b, a).map { (it / 255f).coerceIn(0f, 1f) }
    return components.take(count)
}

internal fun normalizeTestOptionColorHexInput(rawValue: String): String {
    val trimmed = rawValue.trim()
    val withoutPrefix = when {
        trimmed.startsWith("#") -> trimmed.substring(1)
        trimmed.startsWith("0x", ignoreCase = true) -> trimmed.substring(2)
        else -> trimmed
    }
    return withoutPrefix
        .uppercase(Locale.ROOT)
        .take(8)
}

private fun formatComponents(vararg values: Number): String {
    return values.joinToString(",") { value -> formatNumber(value.toDouble()) }
}

private fun formatNumber(value: Double): String {
    val rounded = kotlin.math.round(value * 1_000_000.0) / 1_000_000.0
    if (rounded % 1.0 == 0.0) {
        return rounded.toInt().toString()
    }
    return String.format(Locale.ROOT, "%.6f", rounded).trimEnd('0').trimEnd('.')
}
