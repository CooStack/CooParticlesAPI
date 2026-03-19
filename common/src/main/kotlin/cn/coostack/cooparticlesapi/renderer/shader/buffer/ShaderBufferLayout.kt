package cn.coostack.cooparticlesapi.renderer.shader.buffer

import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ShaderBufferFieldDescriptor<T>(
    val name: String,
    val type: ShaderBufferFieldType,
    val serializer: ((T) -> Any?)? = null
)

data class ShaderBufferLayout<T>(
    val name: String,
    val fields: List<ShaderBufferFieldDescriptor<T>>,
    val requestedBinding: ShaderBufferBinding = ShaderBufferBinding.UNIFORM_BUFFER,
    val memoryLayout: ShaderBufferMemoryLayout = ShaderBufferMemoryLayout.STD140,
    val assignedBinding: Int? = null
) {
    fun assignBinding(binding: Int): ShaderBufferLayout<T> {
        return copy(assignedBinding = binding)
    }

    fun byteSize(): Int {
        var cursor = 0
        fields.forEach { field ->
            cursor = align(cursor, field.type.alignment(memoryLayout))
            cursor += field.type.byteSize(memoryLayout)
        }
        return align(cursor, 16)
    }

    fun encode(value: T): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(byteSize()).order(ByteOrder.nativeOrder())
        var cursor = 0
        fields.forEach { field ->
            cursor = align(cursor, field.type.alignment(memoryLayout))
            val serialized = requireNotNull(field.serializer?.invoke(value)) {
                "Field ${field.name} does not define a serializer"
            }
            writeField(buffer, cursor, field.type, serialized)
            cursor += field.type.byteSize(memoryLayout)
        }
        buffer.limit(byteSize())
        buffer.position(0)
        return buffer
    }

    fun effectiveBinding(shaderStorageSupported: Boolean): ShaderBufferBinding {
        return if (requestedBinding == ShaderBufferBinding.SHADER_STORAGE_BUFFER && shaderStorageSupported) {
            ShaderBufferBinding.SHADER_STORAGE_BUFFER
        } else {
            ShaderBufferBinding.UNIFORM_BUFFER
        }
    }

    fun createGlslBlock(
        shaderStorageSupported: Boolean,
        interfaceName: String? = null
    ): String {
        val binding = effectiveBinding(shaderStorageSupported)
        val keyword = if (binding == ShaderBufferBinding.SHADER_STORAGE_BUFFER) "buffer" else "uniform"
        val bindingText = assignedBinding?.let { ", binding = $it" } ?: ""
        val blockName = interfaceName ?: name
        val fieldDeclarations = fields.joinToString("\n") { field ->
            "    ${field.type.glslName} ${field.name};"
        }
        return buildString {
            append("layout(")
            append(memoryLayout.glslKeyword)
            append(bindingText)
            append(") ")
            append(keyword)
            append(' ')
            append(blockName)
            append(" {\n")
            append(fieldDeclarations)
            append("\n};")
        }
    }

    companion object {
        fun <T> builder(name: String): ShaderBufferLayoutBuilder<T> {
            return ShaderBufferLayoutBuilder(name)
        }
    }

    private fun align(value: Int, alignment: Int): Int {
        if (alignment <= 1) {
            return value
        }
        val remainder = value % alignment
        return if (remainder == 0) value else value + (alignment - remainder)
    }

    private fun writeField(
        buffer: ByteBuffer,
        offset: Int,
        type: ShaderBufferFieldType,
        value: Any
    ) {
        when (type) {
            ShaderBufferFieldType.FLOAT -> buffer.putFloat(offset, (value as Number).toFloat())
            ShaderBufferFieldType.INT -> buffer.putInt(offset, (value as Number).toInt())
            ShaderBufferFieldType.UINT -> buffer.putInt(offset, (value as Number).toInt())
            ShaderBufferFieldType.VEC2 -> writeFloats(buffer, offset, 2, vector2Values(value))
            ShaderBufferFieldType.VEC3 -> writeFloats(buffer, offset, 3, vector3Values(value))
            ShaderBufferFieldType.VEC4 -> writeFloats(buffer, offset, 4, vector4Values(value))
            ShaderBufferFieldType.MAT3 -> writeFloats(buffer, offset, 12, matrix3Values(value))
            ShaderBufferFieldType.MAT4 -> writeFloats(buffer, offset, 16, matrix4Values(value))
        }
    }

    private fun writeFloats(
        buffer: ByteBuffer,
        offset: Int,
        expectedCount: Int,
        values: FloatArray
    ) {
        require(values.size == expectedCount) {
            "Expected $expectedCount float values but got ${values.size}"
        }
        values.forEachIndexed { index, component ->
            buffer.putFloat(offset + index * Float.SIZE_BYTES, component)
        }
    }

    private fun vector2Values(value: Any): FloatArray {
        return when (value) {
            is Vector2f -> floatArrayOf(value.x, value.y)
            is FloatArray -> value
            else -> error("Unsupported vec2 serializer payload: ${value::class.java.name}")
        }
    }

    private fun vector3Values(value: Any): FloatArray {
        return when (value) {
            is Vector3f -> floatArrayOf(value.x, value.y, value.z)
            is FloatArray -> value
            else -> error("Unsupported vec3 serializer payload: ${value::class.java.name}")
        }
    }

    private fun vector4Values(value: Any): FloatArray {
        return when (value) {
            is Vector4f -> floatArrayOf(value.x, value.y, value.z, value.w)
            is FloatArray -> value
            else -> error("Unsupported vec4 serializer payload: ${value::class.java.name}")
        }
    }

    private fun matrix3Values(value: Any): FloatArray {
        return when (value) {
            is FloatArray -> value
            else -> error("Unsupported mat3 serializer payload: ${value::class.java.name}")
        }
    }

    private fun matrix4Values(value: Any): FloatArray {
        return when (value) {
            is FloatArray -> value
            else -> error("Unsupported mat4 serializer payload: ${value::class.java.name}")
        }
    }
}

class ShaderBufferLayoutBuilder<T>(
    private val name: String
) {
    private val fields = mutableListOf<ShaderBufferFieldDescriptor<T>>()

    fun field(
        name: String,
        type: ShaderBufferFieldType,
        serializer: ((T) -> Any?)? = null
    ): ShaderBufferLayoutBuilder<T> {
        fields += ShaderBufferFieldDescriptor(name = name, type = type, serializer = serializer)
        return this
    }

    fun float(name: String, serializer: ((T) -> Float)? = null): ShaderBufferLayoutBuilder<T> =
        field(name, ShaderBufferFieldType.FLOAT, serializer)

    fun int(name: String, serializer: ((T) -> Int)? = null): ShaderBufferLayoutBuilder<T> =
        field(name, ShaderBufferFieldType.INT, serializer)

    fun uint(name: String, serializer: ((T) -> UInt)? = null): ShaderBufferLayoutBuilder<T> =
        field(name, ShaderBufferFieldType.UINT, serializer)

    fun vec2(name: String, serializer: ((T) -> Any)? = null): ShaderBufferLayoutBuilder<T> =
        field(name, ShaderBufferFieldType.VEC2, serializer)

    fun vec3(name: String, serializer: ((T) -> Any)? = null): ShaderBufferLayoutBuilder<T> =
        field(name, ShaderBufferFieldType.VEC3, serializer)

    fun vec4(name: String, serializer: ((T) -> Any)? = null): ShaderBufferLayoutBuilder<T> =
        field(name, ShaderBufferFieldType.VEC4, serializer)

    fun mat3(name: String, serializer: ((T) -> Any)? = null): ShaderBufferLayoutBuilder<T> =
        field(name, ShaderBufferFieldType.MAT3, serializer)

    fun mat4(name: String, serializer: ((T) -> Any)? = null): ShaderBufferLayoutBuilder<T> =
        field(name, ShaderBufferFieldType.MAT4, serializer)

    fun build(
        requestedBinding: ShaderBufferBinding = ShaderBufferBinding.UNIFORM_BUFFER,
        memoryLayout: ShaderBufferMemoryLayout = ShaderBufferMemoryLayout.STD140
    ): ShaderBufferLayout<T> {
        return ShaderBufferLayout(
            name = name,
            fields = fields.toList(),
            requestedBinding = requestedBinding,
            memoryLayout = memoryLayout
        )
    }
}
