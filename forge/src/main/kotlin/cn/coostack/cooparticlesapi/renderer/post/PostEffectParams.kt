package cn.coostack.cooparticlesapi.renderer.post

import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import net.minecraft.network.PacketByteBuf
import net.minecraft.resources.ResourceLocation

internal sealed interface PostEffectParamValue {
    fun write(buf: PacketByteBuf)

    data class BoolValue(val value: Boolean) : PostEffectParamValue {
        override fun write(buf: PacketByteBuf) {
            buf.writeBoolean(value)
        }
    }

    data class IntValue(val value: Int) : PostEffectParamValue {
        override fun write(buf: PacketByteBuf) {
            buf.writeInt(value)
        }
    }

    data class LongValue(val value: Long) : PostEffectParamValue {
        override fun write(buf: PacketByteBuf) {
            buf.writeLong(value)
        }
    }

    data class FloatValue(val value: Float) : PostEffectParamValue {
        override fun write(buf: PacketByteBuf) {
            buf.writeFloat(value)
        }
    }

    data class DoubleValue(val value: Double) : PostEffectParamValue {
        override fun write(buf: PacketByteBuf) {
            buf.writeDouble(value)
        }
    }

    data class StringValue(val value: String) : PostEffectParamValue {
        override fun write(buf: PacketByteBuf) {
            buf.writeUtf(value)
        }
    }

    data class ResourceValue(val value: ResourceLocation) : PostEffectParamValue {
        override fun write(buf: PacketByteBuf) {
            buf.writeResourceLocation(value)
        }
    }

    data class Vec2Value(val x: Float, val y: Float) : PostEffectParamValue {
        override fun write(buf: PacketByteBuf) {
            buf.writeFloat(x)
            buf.writeFloat(y)
        }
    }

    data class Vec3Value(val x: Double, val y: Double, val z: Double) : PostEffectParamValue {
        override fun write(buf: PacketByteBuf) {
            buf.writeDouble(x)
            buf.writeDouble(y)
            buf.writeDouble(z)
        }
    }

    data class ColorValue(val red: Float, val green: Float, val blue: Float, val alpha: Float = 1f) : PostEffectParamValue {
        override fun write(buf: PacketByteBuf) {
            buf.writeFloat(red)
            buf.writeFloat(green)
            buf.writeFloat(blue)
            buf.writeFloat(alpha)
        }
    }

    data class UniformValue(val value: CooUniformValue) : PostEffectParamValue {
        override fun write(buf: PacketByteBuf) {
            CooUniformValue.STREAM_CODEC.encode(buf, value)
        }
    }

    companion object {
        fun writeTyped(buf: PacketByteBuf, value: PostEffectParamValue) {
            buf.writeUtf(value.typeId)
            value.write(buf)
        }

        fun readTyped(buf: PacketByteBuf): PostEffectParamValue {
            return when (val type = buf.readUtf()) {
                "bool" -> BoolValue(buf.readBoolean())
                "int" -> IntValue(buf.readInt())
                "long" -> LongValue(buf.readLong())
                "float" -> FloatValue(buf.readFloat())
                "double" -> DoubleValue(buf.readDouble())
                "string" -> StringValue(buf.readUtf())
                "resource" -> ResourceValue(buf.readResourceLocation())
                "vec2" -> Vec2Value(buf.readFloat(), buf.readFloat())
                "vec3" -> Vec3Value(buf.readDouble(), buf.readDouble(), buf.readDouble())
                "color" -> ColorValue(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat())
                "uniform" -> UniformValue(CooUniformValue.STREAM_CODEC.decode(buf))
                else -> error("Unknown post effect param type: $type")
            }
        }
    }
}

internal val PostEffectParamValue.typeId: String
    get() = when (this) {
        is PostEffectParamValue.BoolValue -> "bool"
        is PostEffectParamValue.IntValue -> "int"
        is PostEffectParamValue.LongValue -> "long"
        is PostEffectParamValue.FloatValue -> "float"
        is PostEffectParamValue.DoubleValue -> "double"
        is PostEffectParamValue.StringValue -> "string"
        is PostEffectParamValue.ResourceValue -> "resource"
        is PostEffectParamValue.Vec2Value -> "vec2"
        is PostEffectParamValue.Vec3Value -> "vec3"
        is PostEffectParamValue.ColorValue -> "color"
        is PostEffectParamValue.UniformValue -> "uniform"
    }

internal fun CooUniformValue.toPostEffectParamValue(): PostEffectParamValue {
    return when (this) {
        is CooUniformValue.BoolValue -> PostEffectParamValue.BoolValue(value)
        is CooUniformValue.IntValue -> PostEffectParamValue.IntValue(value)
        is CooUniformValue.FloatValue -> PostEffectParamValue.FloatValue(value)
        is CooUniformValue.Vec2Value -> PostEffectParamValue.Vec2Value(x, y)
        is CooUniformValue.Vec3Value -> PostEffectParamValue.Vec3Value(x.toDouble(), y.toDouble(), z.toDouble())
        is CooUniformValue.Vec4Value -> PostEffectParamValue.ColorValue(x, y, z, w)
        is CooUniformValue.UIntValue,
        is CooUniformValue.DoubleValue,
        is CooUniformValue.IVecValue,
        is CooUniformValue.UVecValue,
        is CooUniformValue.BVecValue,
        is CooUniformValue.DVecValue,
        is CooUniformValue.MatValue,
        is CooUniformValue.DMatValue,
        is CooUniformValue.SamplerValue,
        is CooUniformValue.ImageValue,
        is CooUniformValue.ArrayValue -> PostEffectParamValue.UniformValue(this)
    }
}

internal data class PostEffectParams(
    private val values: Map<String, PostEffectParamValue> = emptyMap()
) {
    fun asMap(): Map<String, PostEffectParamValue> = values

    operator fun get(name: String): PostEffectParamValue? = values[name]

    fun plus(name: String, value: PostEffectParamValue): PostEffectParams = PostEffectParams(values + (name to value))

    fun write(buf: PacketByteBuf) {
        buf.writeInt(values.size)
        values.toSortedMap().forEach { (name, value) ->
            buf.writeUtf(name)
            PostEffectParamValue.writeTyped(buf, value)
        }
    }

    companion object {
        val EMPTY = PostEffectParams()

        fun read(buf: PacketByteBuf): PostEffectParams {
            val count = buf.readInt()
            val values = LinkedHashMap<String, PostEffectParamValue>(count)
            repeat(count) {
                values[buf.readUtf()] = PostEffectParamValue.readTyped(buf)
            }
            return PostEffectParams(values)
        }
    }
}

internal class PostEffectParamsBuilder {
    private val values = LinkedHashMap<String, PostEffectParamValue>()

    fun bool(name: String, value: Boolean) = apply { values[name] = PostEffectParamValue.BoolValue(value) }
    fun int(name: String, value: Int) = apply { values[name] = PostEffectParamValue.IntValue(value) }
    fun long(name: String, value: Long) = apply { values[name] = PostEffectParamValue.LongValue(value) }
    fun float(name: String, value: Float) = apply { values[name] = PostEffectParamValue.FloatValue(value) }
    fun double(name: String, value: Double) = apply { values[name] = PostEffectParamValue.DoubleValue(value) }
    fun string(name: String, value: String) = apply { values[name] = PostEffectParamValue.StringValue(value) }
    fun resource(name: String, value: ResourceLocation) = apply { values[name] = PostEffectParamValue.ResourceValue(value) }
    fun vec2(name: String, x: Float, y: Float) = apply { values[name] = PostEffectParamValue.Vec2Value(x, y) }
    fun vec3(name: String, x: Double, y: Double, z: Double) = apply { values[name] = PostEffectParamValue.Vec3Value(x, y, z) }
    fun color(name: String, red: Float, green: Float, blue: Float, alpha: Float = 1F) = apply {
        values[name] = PostEffectParamValue.ColorValue(red, green, blue, alpha)
    }

    fun put(name: String, value: PostEffectParamValue) = apply { values[name] = value }

    fun build(): PostEffectParams = PostEffectParams(values.toMap())
}
