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
    /**
     * 执行 `ShaderBufferLayout` 定义的 `assignBinding` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`assignBinding(binding = binding)`。
     *
     * @param binding 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun assignBinding(binding: Int): ShaderBufferLayout<T> {
        return copy(assignedBinding = binding)
    }

    /**
     * 执行 `ShaderBufferLayout` 定义的 `byteSize` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`byteSize()`。
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun byteSize(): Int {
        var cursor = 0
        fields.forEach { field ->
            cursor = align(cursor, field.type.alignment(memoryLayout))
            cursor += field.type.byteSize(memoryLayout)
        }
        return align(cursor, 16)
    }

    /**
     * 按 `ShaderBufferLayout` 约定的字段顺序写入 `encode` 数据；读取端必须使用相同协议。
     *
     * 示例：`encode(value = value)`。
     *
     * @param value 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
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

    /**
     * 执行 `ShaderBufferLayout` 定义的 `effectiveBinding` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`effectiveBinding(shaderStorageSupported = shaderStorageSupported)`。
     *
     * @param shaderStorageSupported 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun effectiveBinding(shaderStorageSupported: Boolean): ShaderBufferBinding {
        return if (requestedBinding == ShaderBufferBinding.SHADER_STORAGE_BUFFER && shaderStorageSupported) {
            ShaderBufferBinding.SHADER_STORAGE_BUFFER
        } else {
            ShaderBufferBinding.UNIFORM_BUFFER
        }
    }

    /**
     * 根据输入和 `ShaderBufferLayout` 当前配置创建 `createGlslBlock` 结果；返回对象保留本次配置的语义。
     *
     * 示例：`createGlslBlock(shaderStorageSupported = shaderStorageSupported, interfaceName = interfaceName)`。
     *
     * @param shaderStorageSupported 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param interfaceName 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 根据当前输入生成的新对象或数据结果
     */
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
        /**
         * 根据输入和 `ShaderBufferLayout` 当前配置创建 `builder` 结果；返回对象保留本次配置的语义。
         *
         * 示例：`builder(name = name)`。
         *
         * @param name 用于查找、绑定或记录目标的名称
         *
         * @return 根据当前输入生成的新对象或数据结果
         */
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

    /**
     * 在 `ShaderBufferLayoutBuilder` 中配置 `field`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`field(name = name, type = type, serializer = serializer)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @param type 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param serializer 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun field(
        name: String,
        type: ShaderBufferFieldType,
        serializer: ((T) -> Any?)? = null
    ): ShaderBufferLayoutBuilder<T> {
        fields += ShaderBufferFieldDescriptor(name = name, type = type, serializer = serializer)
        return this
    }

    /**
     * 在 `ShaderBufferLayoutBuilder` 中配置 `float`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`float(name = name, serializer = serializer)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @param serializer 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun float(name: String, serializer: ((T) -> Float)? = null): ShaderBufferLayoutBuilder<T> =
        field(name, ShaderBufferFieldType.FLOAT, serializer)

    /**
     * 在 `ShaderBufferLayoutBuilder` 中配置 `int`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`int(name = name, serializer = serializer)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @param serializer 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun int(name: String, serializer: ((T) -> Int)? = null): ShaderBufferLayoutBuilder<T> =
        field(name, ShaderBufferFieldType.INT, serializer)

    /**
     * 在 `ShaderBufferLayoutBuilder` 中配置 `uint`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`uint(name = name, serializer = serializer)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @param serializer 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun uint(name: String, serializer: ((T) -> UInt)? = null): ShaderBufferLayoutBuilder<T> =
        field(name, ShaderBufferFieldType.UINT, serializer)

    /**
     * 在 `ShaderBufferLayoutBuilder` 中配置 `vec2`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`vec2(name = name, serializer = serializer)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @param serializer 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun vec2(name: String, serializer: ((T) -> Any)? = null): ShaderBufferLayoutBuilder<T> =
        field(name, ShaderBufferFieldType.VEC2, serializer)

    /**
     * 在 `ShaderBufferLayoutBuilder` 中配置 `vec3`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`vec3(name = name, serializer = serializer)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @param serializer 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun vec3(name: String, serializer: ((T) -> Any)? = null): ShaderBufferLayoutBuilder<T> =
        field(name, ShaderBufferFieldType.VEC3, serializer)

    /**
     * 在 `ShaderBufferLayoutBuilder` 中配置 `vec4`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`vec4(name = name, serializer = serializer)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @param serializer 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun vec4(name: String, serializer: ((T) -> Any)? = null): ShaderBufferLayoutBuilder<T> =
        field(name, ShaderBufferFieldType.VEC4, serializer)

    /**
     * 在 `ShaderBufferLayoutBuilder` 中配置 `mat3`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`mat3(name = name, serializer = serializer)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @param serializer 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun mat3(name: String, serializer: ((T) -> Any)? = null): ShaderBufferLayoutBuilder<T> =
        field(name, ShaderBufferFieldType.MAT3, serializer)

    /**
     * 在 `ShaderBufferLayoutBuilder` 中配置 `mat4`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`mat4(name = name, serializer = serializer)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @param serializer 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun mat4(name: String, serializer: ((T) -> Any)? = null): ShaderBufferLayoutBuilder<T> =
        field(name, ShaderBufferFieldType.MAT4, serializer)

    /**
     * 根据输入和 `ShaderBufferLayoutBuilder` 当前配置创建 `build` 结果；返回对象保留本次配置的语义。
     *
     * 示例：`build(requestedBinding = requestedBinding, memoryLayout = memoryLayout)`。
     *
     * @param requestedBinding 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param memoryLayout 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
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
