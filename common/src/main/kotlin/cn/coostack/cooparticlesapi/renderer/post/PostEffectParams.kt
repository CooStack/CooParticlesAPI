package cn.coostack.cooparticlesapi.renderer.post

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation

sealed interface PostEffectParamValue {
    fun write(buf: FriendlyByteBuf)

    data class Bool(val value: Boolean) : PostEffectParamValue {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeBoolean(value)
        }
    }

    data class IntValue(val value: Int) : PostEffectParamValue {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeInt(value)
        }
    }

    data class LongValue(val value: Long) : PostEffectParamValue {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeLong(value)
        }
    }

    data class FloatValue(val value: Float) : PostEffectParamValue {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeFloat(value)
        }
    }

    data class DoubleValue(val value: Double) : PostEffectParamValue {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeDouble(value)
        }
    }

    data class StringValue(val value: String) : PostEffectParamValue {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeUtf(value)
        }
    }

    data class Resource(val value: ResourceLocation) : PostEffectParamValue {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeResourceLocation(value)
        }
    }

    data class Vec2(val x: Float, val y: Float) : PostEffectParamValue {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeFloat(x)
            buf.writeFloat(y)
        }
    }

    data class Vec3(val x: Double, val y: Double, val z: Double) : PostEffectParamValue {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeDouble(x)
            buf.writeDouble(y)
            buf.writeDouble(z)
        }
    }

    data class Color(val red: Float, val green: Float, val blue: Float, val alpha: Float = 1f) : PostEffectParamValue {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeFloat(red)
            buf.writeFloat(green)
            buf.writeFloat(blue)
            buf.writeFloat(alpha)
        }
    }

    companion object {
        fun writeTyped(buf: FriendlyByteBuf, value: PostEffectParamValue) {
            buf.writeUtf(value.typeId)
            value.write(buf)
        }

        fun readTyped(buf: FriendlyByteBuf): PostEffectParamValue {
            return when (val type = buf.readUtf()) {
                "bool" -> Bool(buf.readBoolean())
                "int" -> IntValue(buf.readInt())
                "long" -> LongValue(buf.readLong())
                "float" -> FloatValue(buf.readFloat())
                "double" -> DoubleValue(buf.readDouble())
                "string" -> StringValue(buf.readUtf())
                "resource" -> Resource(buf.readResourceLocation())
                "vec2" -> Vec2(buf.readFloat(), buf.readFloat())
                "vec3" -> Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())
                "color" -> Color(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat())
                else -> error("Unknown post effect param type: $type")
            }
        }
    }
}

val PostEffectParamValue.typeId: String
    get() = when (this) {
        is PostEffectParamValue.Bool -> "bool"
        is PostEffectParamValue.IntValue -> "int"
        is PostEffectParamValue.LongValue -> "long"
        is PostEffectParamValue.FloatValue -> "float"
        is PostEffectParamValue.DoubleValue -> "double"
        is PostEffectParamValue.StringValue -> "string"
        is PostEffectParamValue.Resource -> "resource"
        is PostEffectParamValue.Vec2 -> "vec2"
        is PostEffectParamValue.Vec3 -> "vec3"
        is PostEffectParamValue.Color -> "color"
    }

data class PostEffectParams(
    private val values: Map<String, PostEffectParamValue> = emptyMap()
) {
    fun asMap(): Map<String, PostEffectParamValue> = values
    operator fun get(name: String): PostEffectParamValue? = values[name]
    fun plus(name: String, value: PostEffectParamValue): PostEffectParams = PostEffectParams(values + (name to value))

    fun write(buf: FriendlyByteBuf) {
        buf.writeInt(values.size)
        values.toSortedMap().forEach { (name, value) ->
            buf.writeUtf(name)
            PostEffectParamValue.writeTyped(buf, value)
        }
    }

    companion object {
        val EMPTY = PostEffectParams()

        fun read(buf: FriendlyByteBuf): PostEffectParams {
            val count = buf.readInt()
            val values = LinkedHashMap<String, PostEffectParamValue>(count)
            repeat(count) {
                values[buf.readUtf()] = PostEffectParamValue.readTyped(buf)
            }
            return PostEffectParams(values)
        }
    }
}

class PostEffectParamsBuilder {
    private val values = LinkedHashMap<String, PostEffectParamValue>()

    fun bool(name: String, value: Boolean) = apply { values[name] = PostEffectParamValue.Bool(value) }
    fun int(name: String, value: Int) = apply { values[name] = PostEffectParamValue.IntValue(value) }
    fun long(name: String, value: Long) = apply { values[name] = PostEffectParamValue.LongValue(value) }
    fun float(name: String, value: Float) = apply { values[name] = PostEffectParamValue.FloatValue(value) }
    fun double(name: String, value: Double) = apply { values[name] = PostEffectParamValue.DoubleValue(value) }
    fun string(name: String, value: String) = apply { values[name] = PostEffectParamValue.StringValue(value) }
    fun resource(name: String, value: ResourceLocation) = apply { values[name] = PostEffectParamValue.Resource(value) }
    fun vec2(name: String, x: Float, y: Float) = apply { values[name] = PostEffectParamValue.Vec2(x, y) }
    fun vec3(name: String, x: Double, y: Double, z: Double) = apply { values[name] = PostEffectParamValue.Vec3(x, y, z) }
    fun color(name: String, red: Float, green: Float, blue: Float, alpha: Float = 1f) = apply {
        values[name] = PostEffectParamValue.Color(red, green, blue, alpha)
    }

    fun put(name: String, value: PostEffectParamValue) = apply { values[name] = value }
    fun build(): PostEffectParams = PostEffectParams(values.toMap())
}
