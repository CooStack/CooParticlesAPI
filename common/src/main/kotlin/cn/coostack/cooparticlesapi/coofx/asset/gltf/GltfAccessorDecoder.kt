package cn.coostack.cooparticlesapi.coofx.asset.gltf

import com.google.gson.JsonObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class DecodedAccessor(
    val values: List<Float>,
    val componentCount: Int,
    val count: Int,
)

internal class GltfAccessorDecoder(
    private val document: JsonObject,
    private val buffers: List<ByteArray>,
) {
    fun decode(accessorIndex: Int): DecodedAccessor {
        val accessors = document.getAsJsonArray("accessors")
            ?: throw IllegalArgumentException("glTF 缺少 accessors")
        val accessor = accessors.getOrNull(accessorIndex)?.asJsonObject
            ?: throw IllegalArgumentException("accessor 索引越界：$accessorIndex")
        require(!accessor.has("sparse")) { "不支持 sparse accessor" }
        val viewIndex = accessor.requiredInt("bufferView")
        val views = document.getAsJsonArray("bufferViews")
            ?: throw IllegalArgumentException("glTF 缺少 bufferViews")
        val view = views.getOrNull(viewIndex)?.asJsonObject
            ?: throw IllegalArgumentException("bufferView 索引越界：$viewIndex")
        val bufferIndex = view.requiredInt("buffer")
        val bytes = buffers.getOrNull(bufferIndex)
            ?: throw IllegalArgumentException("buffer 索引越界：$bufferIndex")
        val componentType = accessor.requiredInt("componentType")
        val componentSize = componentSize(componentType)
        val componentCount = componentCount(accessor.requiredString("type"))
        val count = accessor.requiredInt("count")
        require(count >= 0) { "accessor count 不能为负数" }
        val elementSize = componentSize.toLong() * componentCount
        val stride = view.optionalInt("byteStride", elementSize.toInt()).toLong()
        require(stride >= elementSize && stride % componentSize == 0L) { "byteStride 与 accessor 布局不兼容" }
        val viewStart = view.optionalInt("byteOffset", 0).toLong()
        val accessorOffset = accessor.optionalInt("byteOffset", 0).toLong()
        val viewLength = view.requiredInt("byteLength").toLong()
        require(viewStart >= 0L && accessorOffset >= 0L && viewLength >= 0L) { "accessor 偏移或长度不能为负数" }
        val start = Math.addExact(viewStart, accessorOffset)
        val viewEnd = Math.addExact(viewStart, viewLength)
        val requiredEnd = if (count == 0) {
            start
        } else {
            Math.addExact(start, Math.addExact(Math.multiplyExact(count.toLong() - 1L, stride), elementSize))
        }
        require(requiredEnd <= viewEnd && viewEnd <= bytes.size.toLong()) { "accessor 读取范围越界" }
        val normalized = accessor.get("normalized")?.asBoolean ?: false
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val result = ArrayList<Float>(Math.multiplyExact(count, componentCount))
        repeat(count) { element ->
            var offset = Math.addExact(start, Math.multiplyExact(element.toLong(), stride)).toInt()
            repeat(componentCount) {
                result += readComponent(buffer, offset, componentType, normalized)
                offset += componentSize
            }
        }
        return DecodedAccessor(result, componentCount, count)
    }

    fun decodeIndices(accessorIndex: Int): List<Int> {
        val accessors = document.getAsJsonArray("accessors")
            ?: throw IllegalArgumentException("glTF 缺少 accessors")
        val accessor = accessors.getOrNull(accessorIndex)?.asJsonObject
            ?: throw IllegalArgumentException("accessor 索引越界：$accessorIndex")
        require(!accessor.has("sparse")) { "不支持 sparse accessor" }
        require(accessor.requiredString("type") == "SCALAR") { "索引 accessor 必须为 SCALAR" }
        require(accessor.get("normalized")?.asBoolean != true) { "索引 accessor 不能声明 normalized" }
        val componentType = accessor.requiredInt("componentType")
        require(componentType == 5121 || componentType == 5123 || componentType == 5125) {
            "索引 accessor 只允许无符号整数"
        }
        val viewIndex = accessor.requiredInt("bufferView")
        val view = document.getAsJsonArray("bufferViews")?.getOrNull(viewIndex)?.asJsonObject
            ?: throw IllegalArgumentException("bufferView 索引越界：$viewIndex")
        val bytes = buffers.getOrNull(view.requiredInt("buffer"))
            ?: throw IllegalArgumentException("buffer 索引越界")
        val componentSize = componentSize(componentType)
        val count = accessor.requiredInt("count")
        require(count >= 0) { "accessor count 不能为负数" }
        val stride = view.optionalInt("byteStride", componentSize).toLong()
        require(stride >= componentSize && stride % componentSize == 0L) { "byteStride 与索引 accessor 布局不兼容" }
        val viewStart = view.optionalInt("byteOffset", 0).toLong()
        val accessorOffset = accessor.optionalInt("byteOffset", 0).toLong()
        val viewLength = view.requiredInt("byteLength").toLong()
        require(viewStart >= 0L && accessorOffset >= 0L && viewLength >= 0L) { "索引 accessor 偏移或长度不能为负数" }
        val start = Math.addExact(viewStart, accessorOffset)
        val viewEnd = Math.addExact(viewStart, viewLength)
        val requiredEnd = if (count == 0) {
            start
        } else {
            Math.addExact(start, Math.addExact(Math.multiplyExact(count.toLong() - 1L, stride), componentSize.toLong()))
        }
        require(requiredEnd <= viewEnd && viewEnd <= bytes.size.toLong()) { "索引 accessor 读取范围越界" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return List(count) { element ->
            val offset = Math.addExact(start, Math.multiplyExact(element.toLong(), stride)).toInt()
            val value = when (componentType) {
                5121 -> buffer.get(offset).toInt() and 0xff
                5123 -> buffer.getShort(offset).toInt() and 0xffff
                else -> (buffer.getInt(offset).toLong() and 0xffffffffL).also {
                    require(it <= Int.MAX_VALUE.toLong()) { "索引值超出 JVM Int 可表示范围" }
                }.toInt()
            }
            value
        }
    }

    private fun readComponent(buffer: ByteBuffer, offset: Int, type: Int, normalized: Boolean): Float {
        return when (type) {
            5120 -> buffer.get(offset).let { if (normalized) (it.toFloat() / 127F).coerceAtLeast(-1F) else it.toFloat() }
            5121 -> (buffer.get(offset).toInt() and 0xff).let { if (normalized) it / 255F else it.toFloat() }
            5122 -> buffer.getShort(offset).let { if (normalized) (it.toFloat() / 32767F).coerceAtLeast(-1F) else it.toFloat() }
            5123 -> (buffer.getShort(offset).toInt() and 0xffff).let { if (normalized) it / 65535F else it.toFloat() }
            5125 -> (buffer.getInt(offset).toLong() and 0xffffffffL).let {
                if (normalized) (it / 4294967295.0).toFloat() else it.toFloat()
            }
            5126 -> buffer.getFloat(offset)
            else -> throw IllegalArgumentException("不支持 accessor componentType：$type")
        }.also { require(it.isFinite()) { "accessor 包含非有限数" } }
    }

    private fun componentSize(type: Int): Int = when (type) {
        5120, 5121 -> 1
        5122, 5123 -> 2
        5125, 5126 -> 4
        else -> throw IllegalArgumentException("不支持 accessor componentType：$type")
    }

    private fun componentCount(type: String): Int = when (type) {
        "SCALAR" -> 1
        "VEC2" -> 2
        "VEC3" -> 3
        "VEC4" -> 4
        "MAT4" -> 16
        else -> throw IllegalArgumentException("不支持 accessor type：$type")
    }
}

internal fun JsonObject.requiredInt(name: String): Int = get(name)?.takeIf { it.isJsonPrimitive }?.asInt
    ?: throw IllegalArgumentException("缺少整数属性：$name")

internal fun JsonObject.optionalInt(name: String, default: Int): Int = get(name)?.asInt ?: default

internal fun JsonObject.requiredString(name: String): String = get(name)?.takeIf { it.isJsonPrimitive }?.asString
    ?: throw IllegalArgumentException("缺少字符串属性：$name")

private fun <T> Iterable<T>.getOrNull(index: Int): T? = if (index < 0) null else elementAtOrNull(index)
