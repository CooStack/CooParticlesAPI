package cn.coostack.cooparticlesapi.test.api

import java.util.Base64

object TestOptionParamCodec {
    const val OPTION_SEPARATOR: String = "\u001F"
    private const val PARAM_SEPARATOR: String = "\u001E"
    private const val FIELD_SEPARATOR: String = "\u001D"
    private const val COMPONENT_SEPARATOR: String = "\u001C"

    fun encodeOptionSpecs(specs: List<TestOptionParamSpec<*>>): String {
        return specs.joinToString(PARAM_SEPARATOR) { spec ->
            listOf(
                spec.id,
                spec.displayName,
                spec.valueClass.name,
                spec.defaultText(),
                spec.editor.kind.name,
                spec.editor.componentCount.toString(),
                spec.editor.componentLabels.joinToString(COMPONENT_SEPARATOR),
                spec.editor.pickable.toString(),
                spec.editor.allowAbsolute.toString(),
                spec.editor.defaultPositionMode.id,
                spec.editor.color.toString(),
                spec.editor.suggestions.joinToString(COMPONENT_SEPARATOR)
            ).joinToString(FIELD_SEPARATOR, transform = ::encodeField)
        }
    }

    fun decodeOptionSpecs(encoded: String): List<EncodedTestOptionParamSpec> {
        if (encoded.isBlank()) {
            return emptyList()
        }
        return encoded.split(PARAM_SEPARATOR).mapNotNull { rawParam ->
            val fields = rawParam.split(FIELD_SEPARATOR).mapNotNull(::decodeFieldOrNull)
            if (fields.size < 4) {
                null
            } else {
                EncodedTestOptionParamSpec(
                    id = fields[0],
                    displayName = fields[1],
                    typeName = fields[2],
                    defaultValue = fields[3],
                    editorKind = fields.getOrNull(4) ?: TestOptionParamEditorKind.TEXT.name,
                    componentCount = fields.getOrNull(5)?.toIntOrNull() ?: 1,
                    componentLabels = fields.getOrNull(6)?.splitComponentField().orEmpty(),
                    pickable = fields.getOrNull(7)?.toBooleanStrictOrNull() ?: false,
                    allowAbsolute = fields.getOrNull(8)?.toBooleanStrictOrNull() ?: false,
                    defaultPositionMode = fields.getOrNull(9) ?: TestOptionParamPositionMode.RELATIVE.id,
                    color = fields.getOrNull(10)?.toBooleanStrictOrNull() ?: false,
                    suggestions = fields.getOrNull(11)?.splitComponentField().orEmpty()
                )
            }
        }
    }

    fun encodeOptionValues(values: Map<String, String>): String {
        return values.entries.joinToString(PARAM_SEPARATOR) { (id, value) ->
            listOf(id, value).joinToString(FIELD_SEPARATOR, transform = ::encodeField)
        }
    }

    fun decodeOptionValues(encoded: String): Map<String, String> {
        if (encoded.isBlank()) {
            return emptyMap()
        }
        return encoded.split(PARAM_SEPARATOR).mapNotNull { rawParam ->
            val fields = rawParam.split(FIELD_SEPARATOR).mapNotNull(::decodeFieldOrNull)
            if (fields.size < 2) null else fields[0] to fields[1]
        }.toMap(LinkedHashMap())
    }

    fun encodeAllOptionSpecs(specs: List<List<TestOptionParamSpec<*>>>): List<String> {
        return specs.map(::encodeOptionSpecs)
    }

    fun encodeAllOptionValues(values: List<Map<String, String>>): List<String> {
        return values.map(::encodeOptionValues)
    }

    private fun encodeField(value: String): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
    }

    private fun decodeFieldOrNull(value: String): String? {
        return runCatching {
            String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun String.splitComponentField(): List<String> {
        if (isBlank()) return emptyList()
        return split(COMPONENT_SEPARATOR).filter { it.isNotBlank() }
    }
}

data class EncodedTestOptionParamSpec(
    val id: String,
    val displayName: String,
    val typeName: String,
    val defaultValue: String,
    val editorKind: String = TestOptionParamEditorKind.TEXT.name,
    val componentCount: Int = 1,
    val componentLabels: List<String> = emptyList(),
    val pickable: Boolean = false,
    val allowAbsolute: Boolean = false,
    val defaultPositionMode: String = TestOptionParamPositionMode.RELATIVE.id,
    val color: Boolean = false,
    val suggestions: List<String> = emptyList()
)
