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
        val elementSize = componentSize * componentCount
        val stride = view.optionalInt("byteStride", elementSize)
        require(stride >= elementSize && stride % componentSize == 0) { "byteStride 与 accessor 布局不兼容" }
        val start = view.optionalInt("byteOffset", 0) + accessor.optionalInt("byteOffset", 0)
        val viewEnd = view.optionalInt("byteOffset", 0) + view.requiredInt("byteLength")
        val requiredEnd = if (count == 0) start else start + (count - 1) * stride + elementSize
        require(start >= 0 && requiredEnd <= viewEnd && viewEnd <= bytes.size) { "accessor 读取范围越界" }
        val normalized = accessor.get("normalized")?.asBoolean ?: false
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val result = ArrayList<Float>(count * componentCount)
        repeat(count) { element ->
            var offset = start + element * stride
            repeat(componentCount) {
                result += readComponent(buffer, offset, componentType, normalized)
                offset += componentSize
            }
        }
        return DecodedAccessor(result, componentCount, count)
    }

    fun decodeIndices(accessorIndex: Int): List<Int> {
        val accessor = document.getAsJsonArray("accessors").get(accessorIndex).asJsonObject
        val componentType = accessor.requiredInt("componentType")
        require(componentType == 5121 || componentType == 5123 || componentType == 5125) {
            "索引 accessor 只允许无符号整数"
        }
        val decoded = decode(accessorIndex)
        require(decoded.componentCount == 1) { "索引 accessor 必须为 SCALAR" }
        return decoded.values.map { value -> value.toLong().also { require(it <= Int.MAX_VALUE) }.toInt() }
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
